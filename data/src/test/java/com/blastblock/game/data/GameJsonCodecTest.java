package com.blastblock.game.data;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.blastblock.game.domain.catalog.PieceCatalog;
import com.blastblock.game.domain.engine.GameEngine;
import com.blastblock.game.domain.model.Board;
import com.blastblock.game.domain.model.GameState;
import com.blastblock.game.domain.model.Piece;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;
import java.util.Random;

/** {@link GameJsonCodec} için bilgisayarda (JVM) çalışan birim testleri. */
public class GameJsonCodecTest {

    private final GameJsonCodec codec = new GameJsonCodec();

    /** Dolu hücreleri, kullanılmış bir yuvası, skoru ve serisi olan örnek durum. */
    private static GameState sampleState(boolean gameOver) {
        int[] cells = new int[Board.SIZE * Board.SIZE];
        cells[0] = 1;
        cells[9] = 3;
        cells[27] = 5;
        cells[63] = 2;
        List<String> ids = PieceCatalog.ids();
        Piece[] tray = {
                PieceCatalog.create(ids.get(0), 2),
                null,
                PieceCatalog.create(ids.get(ids.size() - 1), 4)
        };
        return new GameState(new Board(cells), tray, 1290L, 3, gameOver);
    }

    private JSONObject sampleJson() throws Exception {
        return new JSONObject(codec.encode(sampleState(false)));
    }

    private void assertInvalid(String json) {
        try {
            codec.decode(json);
            fail("Geçersiz kayıt kabul edildi: " + json);
        } catch (InvalidSaveException expected) {
            // beklenen
        }
    }

    // ---- gidiş-dönüş ----

    @Test
    public void roundTrip_preservesEverything() {
        GameState original = sampleState(false);
        GameState decoded = codec.decode(codec.encode(original));

        assertArrayEquals(original.getBoard().getCells(), decoded.getBoard().getCells());
        assertEquals(1290L, decoded.getScore());
        assertEquals(3, decoded.getStreak());
        assertFalse(decoded.isGameOver());
        assertNull(decoded.getTrayPiece(1));
        for (int slot : new int[]{0, 2}) {
            Piece a = original.getTrayPiece(slot);
            Piece b = decoded.getTrayPiece(slot);
            assertEquals(a.getShapeId(), b.getShapeId());
            assertEquals(a.getColorId(), b.getColorId());
        }
        assertEquals(original, decoded);
    }

    @Test
    public void roundTrip_keepsGameOverFlag() {
        GameState decoded = codec.decode(codec.encode(sampleState(true)));
        assertTrue(decoded.isGameOver());
    }

    @Test
    public void roundTrip_newGameFromEngine() {
        GameState state = new GameEngine(new Random(1)).newGame();
        assertEquals(state, codec.decode(codec.encode(state)));
    }

    @Test
    public void encode_writesExpectedFields() throws Exception {
        JSONObject json = sampleJson();
        assertEquals(1, json.getInt("schemaVersion"));
        assertEquals(64, json.getJSONArray("cells").length());
        JSONArray tray = json.getJSONArray("tray");
        assertEquals(3, tray.length());
        assertTrue(tray.isNull(1));
        assertEquals(2, tray.getJSONObject(0).getInt("colorId"));
        assertEquals(1290L, json.getLong("score"));
        assertEquals(3, json.getInt("streak"));
        assertFalse(json.getBoolean("gameOver"));
    }

    // ---- bozuk kayıtlar ----

    @Test
    public void truncatedJson_isInvalid() {
        String full = codec.encode(sampleState(false));
        assertInvalid(full.substring(0, full.length() / 2));
    }

    @Test
    public void emptyOrNull_isInvalid() {
        assertInvalid("");
        assertInvalid(null);
        assertInvalid("[]");
    }

    @Test
    public void unknownShapeId_isInvalid() throws Exception {
        JSONObject json = sampleJson();
        json.getJSONArray("tray").getJSONObject(0).put("shapeId", "UYDURMA_SEKIL");
        assertInvalid(json.toString());
    }

    @Test
    public void colorOutOfRange_isInvalid() throws Exception {
        JSONObject json = sampleJson();
        json.getJSONArray("tray").getJSONObject(0).put("colorId", 6);
        assertInvalid(json.toString());
    }

    @Test
    public void cells63_isInvalid() throws Exception {
        JSONObject json = sampleJson();
        json.getJSONArray("cells").remove(63);
        assertEquals(63, json.getJSONArray("cells").length());
        assertInvalid(json.toString());
    }

    @Test
    public void cellValue9_isInvalid() throws Exception {
        JSONObject json = sampleJson();
        json.getJSONArray("cells").put(5, 9);
        assertInvalid(json.toString());
    }

    @Test
    public void nonIntegerCell_isInvalid() throws Exception {
        JSONObject json = sampleJson();
        json.getJSONArray("cells").put(5, "1");
        assertInvalid(json.toString());
    }

    @Test
    public void unsupportedSchemaVersion_isInvalid() throws Exception {
        JSONObject json = sampleJson();
        json.put("schemaVersion", 2);
        assertInvalid(json.toString());
    }

    @Test
    public void trayLength2_isInvalid() throws Exception {
        JSONObject json = sampleJson();
        json.getJSONArray("tray").remove(2);
        assertInvalid(json.toString());
    }

    @Test
    public void missingFields_areInvalid() throws Exception {
        for (String key : new String[]{"schemaVersion", "cells", "tray", "score", "streak", "gameOver"}) {
            JSONObject json = sampleJson();
            json.remove(key);
            assertInvalid(json.toString());
        }
    }

    @Test
    public void negativeScoreOrBadStreak_isInvalid() throws Exception {
        JSONObject json = sampleJson();
        json.put("score", -1);
        assertInvalid(json.toString());

        json = sampleJson();
        json.put("streak", 6);
        assertInvalid(json.toString());
    }

    @Test
    public void emptyTrayInUnfinishedGame_isInvalid() throws Exception {
        JSONObject json = sampleJson();
        JSONArray tray = json.getJSONArray("tray");
        tray.put(0, JSONObject.NULL);
        tray.put(2, JSONObject.NULL);
        assertInvalid(json.toString());
    }
}
