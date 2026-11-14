package com.blastblock.game.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.blastblock.game.domain.model.ScoreRecord;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** {@link ScoreHistoryJsonCodec} için bilgisayarda (JVM) çalışan birim testleri. */
public class ScoreHistoryJsonCodecTest {

    private final ScoreHistoryJsonCodec codec = new ScoreHistoryJsonCodec();

    private void assertInvalid(String json) {
        try {
            codec.decode(json);
            fail("Geçersiz dosya kabul edildi: " + json);
        } catch (InvalidSaveException expected) {
            // beklenen
        }
    }

    /** Tek kayıtlı geçerli JSON nesnesi. */
    private static JSONObject validJson() throws Exception {
        JSONObject item = new JSONObject();
        item.put("score", 1290L);
        item.put("finishedAt", 1731070000000L);
        JSONObject root = new JSONObject();
        root.put("schemaVersion", 1);
        root.put("scores", new JSONArray().put(item));
        return root;
    }

    // ---- gidiş-dönüş ----

    @Test
    public void roundTrip_preservesRecordsAndOrder() {
        List<ScoreRecord> original = Arrays.asList(
                new ScoreRecord(1290L, 1731070000000L),
                new ScoreRecord(800L, 1731000000000L),
                new ScoreRecord(800L, 1731050000000L),
                new ScoreRecord(0L, 1731080000000L));
        assertEquals(original, codec.decode(codec.encode(original)));
    }

    @Test
    public void roundTrip_emptyList() {
        String json = codec.encode(Collections.<ScoreRecord>emptyList());
        assertTrue(codec.decode(json).isEmpty());
    }

    @Test
    public void encode_writesExpectedFormat() throws Exception {
        JSONObject root = new JSONObject(codec.encode(
                Collections.singletonList(new ScoreRecord(1290L, 1731070000000L))));
        assertEquals(1, root.getInt("schemaVersion"));
        JSONObject item = root.getJSONArray("scores").getJSONObject(0);
        assertEquals(1290L, item.getLong("score"));
        assertEquals(1731070000000L, item.getLong("finishedAt"));
    }

    @Test
    public void decode_validJson() throws Exception {
        List<ScoreRecord> list = codec.decode(validJson().toString());
        assertEquals(Collections.singletonList(new ScoreRecord(1290L, 1731070000000L)), list);
    }

    // ---- sıralama ve kırpma ----

    @Test
    public void decode_sortsUnorderedFile() {
        // Elle karıştırılmış dosya: puana göre azalan, eşitlikte eski önde olmalı.
        String json = "{\"schemaVersion\":1,\"scores\":["
                + "{\"score\":100,\"finishedAt\":5},"
                + "{\"score\":500,\"finishedAt\":9},"
                + "{\"score\":500,\"finishedAt\":3}]}";
        List<ScoreRecord> expected = Arrays.asList(
                new ScoreRecord(500L, 3L),
                new ScoreRecord(500L, 9L),
                new ScoreRecord(100L, 5L));
        assertEquals(expected, codec.decode(json));
    }

    @Test
    public void decode_preservesEveryLegacyRecord() throws Exception {
        JSONArray array = new JSONArray();
        for (int i = 1; i <= 12; i++) {
            array.put(new JSONObject().put("score", i * 10L).put("finishedAt", 1000L + i));
        }
        JSONObject root = new JSONObject().put("schemaVersion", 1).put("scores", array);

        List<ScoreRecord> list = codec.decode(root.toString());
        assertEquals(12, list.size());
        assertEquals(new ScoreRecord(120L, 1012L), list.get(0));
        assertEquals(new ScoreRecord(10L, 1001L), list.get(11));
    }

    @Test
    public void decode_resultIsUnmodifiable() throws Exception {
        List<ScoreRecord> list = codec.decode(validJson().toString());
        try {
            list.add(new ScoreRecord(1L, 1L));
            fail("Liste değiştirilebilir olmamalı");
        } catch (UnsupportedOperationException expected) {
            // beklenen
        }
    }

    // ---- bozuk dosyalar ----

    @Test
    public void decode_emptyOrNull_isInvalid() {
        assertInvalid(null);
        assertInvalid("");
        assertInvalid("   ");
    }

    @Test
    public void decode_truncatedJson_isInvalid() throws Exception {
        String json = validJson().toString();
        assertInvalid(json.substring(0, json.length() / 2));
        assertInvalid("{\"schemaVersion\":1,\"scores\":[{\"score\":5,");
    }

    @Test
    public void decode_wrongVersion_isInvalid() throws Exception {
        assertInvalid(validJson().put("schemaVersion", 2).toString());
        assertInvalid(validJson().put("schemaVersion", "1").toString());
    }

    @Test
    public void decode_negativeScore_isInvalid() {
        assertInvalid("{\"schemaVersion\":1,\"scores\":[{\"score\":-5,\"finishedAt\":1}]}");
    }

    @Test
    public void decode_missingFields_isInvalid() throws Exception {
        JSONObject noVersion = validJson();
        noVersion.remove("schemaVersion");
        assertInvalid(noVersion.toString());

        JSONObject noScores = validJson();
        noScores.remove("scores");
        assertInvalid(noScores.toString());

        assertInvalid("{\"schemaVersion\":1,\"scores\":[{\"finishedAt\":1}]}");
        assertInvalid("{\"schemaVersion\":1,\"scores\":[{\"score\":5}]}");
        assertInvalid("{\"schemaVersion\":1,\"scores\":[{\"score\":null,\"finishedAt\":1}]}");
    }

    @Test
    public void decode_wrongTypes_isInvalid() {
        assertInvalid("{\"schemaVersion\":1,\"scores\":{}}");
        assertInvalid("{\"schemaVersion\":1,\"scores\":[5]}");
        assertInvalid("{\"schemaVersion\":1,\"scores\":[null]}");
        assertInvalid("{\"schemaVersion\":1,\"scores\":[{\"score\":\"5\",\"finishedAt\":1}]}");
        assertInvalid("{\"schemaVersion\":1,\"scores\":[{\"score\":2.5,\"finishedAt\":1}]}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void encode_null_throws() {
        codec.encode(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void encode_nullRecord_throws() {
        List<ScoreRecord> list = new ArrayList<>();
        list.add(null);
        codec.encode(list);
    }
}
