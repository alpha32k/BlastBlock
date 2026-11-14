package com.blastblock.game.data;

import com.blastblock.game.domain.model.ScoreRecord;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Reads the legacy JSON score history for migration.
 *
 * <p><b>JSON biçimi (sürüm 1):</b>
 * <pre>
 * {
 *   "schemaVersion": 1,
 *   "scores": [ {"score": 1290, "finishedAt": 1731070000000}, ... ]
 * }
 * </pre>
 * {@code finishedAt} oyunun bittiği an olup 1970'ten bu yana geçen milisaniyedir.
 *
 * <p><b>Neden {@code schemaVersion}?</b> {@link GameJsonCodec}'teki gerekçeyle aynı: ileride
 * biçim değişirse eski dosyalar tanınabilsin, anlaşılmayan yeni biçim güvenle reddedilsin.
 *
 * <p><b>Bozuk dosya davranışı:</b> {@link #decode(String)} en küçük tutarsızlıkta (kesik JSON,
 * yanlış sürüm, eksik alan, negatif puan, sayı yerine metin…) {@link InvalidSaveException}
 * fırlatır; yarım bir liste döndürmez.
 *
 * <p>Yalnızca {@code org.json} kullanır, Android API'sine dokunmaz; bu sayede bilgisayarda (JVM)
 * birim testleriyle doğrulanabilir. Durumsuzdur ve iş parçacığı güvenlidir.
 */
public final class ScoreHistoryJsonCodec {

    /** Bu sürümün yazdığı ve okuyabildiği tek biçim sürümü. */
    public static final int SCHEMA_VERSION = 1;

    static final String KEY_VERSION = "schemaVersion";
    static final String KEY_SCORES = "scores";
    static final String KEY_SCORE = "score";
    static final String KEY_FINISHED_AT = "finishedAt";

    /**
     * Listeyi JSON metnine çevirir. Liste verildiği sırayla yazılır.
     *
     * @throws IllegalArgumentException liste null ise ya da null kayıt içeriyorsa
     */
    public String encode(List<ScoreRecord> scores) {
        if (scores == null) {
            throw new IllegalArgumentException("Kaydedilecek liste null olamaz");
        }
        try {
            JSONArray array = new JSONArray();
            for (ScoreRecord record : scores) {
                if (record == null) {
                    throw new IllegalArgumentException("Listede boş kayıt olamaz");
                }
                JSONObject item = new JSONObject();
                item.put(KEY_SCORE, record.getScore());
                item.put(KEY_FINISHED_AT, record.getFinishedAtMillis());
                array.put(item);
            }
            JSONObject root = new JSONObject();
            root.put(KEY_VERSION, SCHEMA_VERSION);
            root.put(KEY_SCORES, array);
            return root.toString();
        } catch (JSONException e) {
            // Yalnızca tam sayı yazdığımız için pratikte oluşmaz.
            throw new IllegalStateException("Skor listesi JSON'a çevrilemedi", e);
        }
    }

    /**
     * JSON metninden listeyi kurar.
     *
     * @return all records, highest score first and oldest first on ties
     * @throws InvalidSaveException metin bozuk, eksik, uyumsuz sürümlü veya tutarsızsa
     */
    public List<ScoreRecord> decode(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new InvalidSaveException("Skor dosyası boş");
        }
        try {
            JSONObject root = new JSONObject(json);

            long version = toLong(require(root, KEY_VERSION), KEY_VERSION);
            if (version != SCHEMA_VERSION) {
                throw new InvalidSaveException("Desteklenmeyen skor dosyası sürümü: " + version);
            }

            Object scoresValue = require(root, KEY_SCORES);
            if (!(scoresValue instanceof JSONArray)) {
                throw new InvalidSaveException(KEY_SCORES + " dizi olmalı");
            }
            JSONArray array = (JSONArray) scoresValue;
            List<ScoreRecord> records = new ArrayList<>(array.length());
            for (int i = 0; i < array.length(); i++) {
                Object item = array.get(i);
                if (!(item instanceof JSONObject)) {
                    throw new InvalidSaveException("Skor kaydı nesne olmalı: " + i);
                }
                JSONObject obj = (JSONObject) item;
                long score = toLong(require(obj, KEY_SCORE), KEY_SCORE);
                long finishedAt = toLong(require(obj, KEY_FINISHED_AT), KEY_FINISHED_AT);
                // ScoreRecord kurucusu negatif puanı IllegalArgumentException ile reddeder.
                records.add(new ScoreRecord(score, finishedAt));
            }
            records.sort(Comparator.comparingLong(ScoreRecord::getScore).reversed()
                    .thenComparingLong(ScoreRecord::getFinishedAtMillis));
            return Collections.unmodifiableList(records);
        } catch (InvalidSaveException e) {
            throw e;
        } catch (JSONException | IllegalArgumentException e) {
            // JSONException: sözdizimi hatası/kesik metin; IllegalArgumentException: domain
            // kurucusunun reddettiği değer. Hepsi "geçersiz dosya" demektir.
            throw new InvalidSaveException("Geçersiz skor dosyası: " + e.getMessage(), e);
        }
    }

    private static Object require(JSONObject obj, String key) throws JSONException {
        if (!obj.has(key) || obj.isNull(key)) {
            throw new InvalidSaveException("Eksik alan: " + key);
        }
        return obj.get(key);
    }

    /** Yalnızca tam sayı türlerini kabul eder (metin "3" veya 2.5 reddedilir). */
    private static long toLong(Object value, String name) {
        if (value instanceof Integer || value instanceof Long
                || value instanceof Short || value instanceof Byte) {
            return ((Number) value).longValue();
        }
        if (value instanceof java.math.BigInteger) {
            java.math.BigInteger big = (java.math.BigInteger) value;
            if (big.bitLength() < 64) {
                return big.longValue();
            }
        }
        throw new InvalidSaveException(name + " tam sayı olmalı");
    }
}
