package com.blastblock.game.data;

import com.blastblock.game.domain.catalog.PieceCatalog;
import com.blastblock.game.domain.model.Board;
import com.blastblock.game.domain.model.GameState;
import com.blastblock.game.domain.model.Piece;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * {@link GameState} nesnesini JSON metnine çevirir (encode) ve geri okur (decode).
 *
 * <p><b>JSON biçimi (sürüm 1):</b>
 * <pre>
 * {
 *   "schemaVersion": 1,
 *   "cells": [0,0,3,0, ... 64 sayı ...],        // satır-öncelikli, 0 = boş, 1..5 = renk
 *   "tray": [ {"shapeId":"L3","colorId":2}, null, {"shapeId":"I4","colorId":5} ],
 *   "score": 1290,
 *   "streak": 2,
 *   "gameOver": false
 * }
 * </pre>
 *
 * <p><b>Neden parçanın hücreleri yazılmıyor?</b> Tepsideki her parça yalnızca şekil kimliği ve
 * rengiyle saklanır; okurken {@link PieceCatalog#create(String, int)} ile katalogdan yeniden
 * kurulur. Böylece dosya küçük kalır ve elle değiştirilmiş/bozuk bir dosya katalogda olmayan
 * "uydurma" bir parça üretemez.
 *
 * <p><b>Neden {@code schemaVersion}?</b> Uygulamanın ileriki bir sürümü kayıt biçimini
 * değiştirirse, eski kayıtları tanıyıp dönüştürebilmek ya da anlamadığı yeni bir biçimi güvenle
 * reddedebilmek için her dosya kendi sürüm numarasını taşır. Bu sürüm yalnızca 1'i kabul eder.
 *
 * <p><b>Bozuk kayıt davranışı:</b> {@link #decode(String)} hiçbir zaman yarım/şüpheli bir durum
 * döndürmez; en küçük tutarsızlıkta {@link InvalidSaveException} fırlatır. Denetimler bilerek
 * katıdır: örneğin sayı yerine metin ("3") veya ondalıklı sayı (2.5) kabul edilmez.
 *
 * <p>Bu sınıf yalnızca {@code org.json} kullanır, Android API'sine dokunmaz; bu sayede
 * bilgisayarda (JVM) birim testleriyle doğrulanabilir. Durumsuzdur ve iş parçacığı güvenlidir.
 */
public final class GameJsonCodec {

    /** Bu sürümün yazdığı ve okuyabildiği tek kayıt biçimi sürümü. */
    public static final int SCHEMA_VERSION = 1;

    static final String KEY_VERSION = "schemaVersion";
    static final String KEY_CELLS = "cells";
    static final String KEY_TRAY = "tray";
    static final String KEY_SHAPE = "shapeId";
    static final String KEY_COLOR = "colorId";
    static final String KEY_SCORE = "score";
    static final String KEY_STREAK = "streak";
    static final String KEY_GAME_OVER = "gameOver";

    /**
     * Oyun durumunu JSON metnine çevirir.
     *
     * @throws IllegalArgumentException durum null ise
     */
    public String encode(GameState state) {
        if (state == null) {
            throw new IllegalArgumentException("Kaydedilecek durum null olamaz");
        }
        try {
            JSONObject root = new JSONObject();
            root.put(KEY_VERSION, SCHEMA_VERSION);

            JSONArray cells = new JSONArray();
            for (int value : state.getBoard().getCells()) {
                cells.put(value);
            }
            root.put(KEY_CELLS, cells);

            JSONArray tray = new JSONArray();
            for (Piece piece : state.getTray()) {
                if (piece == null) {
                    // Kullanılmış yuva açıkça JSON null olarak yazılır; böylece yuva sırası korunur.
                    tray.put(JSONObject.NULL);
                } else {
                    JSONObject p = new JSONObject();
                    p.put(KEY_SHAPE, piece.getShapeId());
                    p.put(KEY_COLOR, piece.getColorId());
                    tray.put(p);
                }
            }
            root.put(KEY_TRAY, tray);

            root.put(KEY_SCORE, state.getScore());
            root.put(KEY_STREAK, state.getStreak());
            root.put(KEY_GAME_OVER, state.isGameOver());
            return root.toString();
        } catch (JSONException e) {
            // Yalnızca sayı/metin/boolean yazdığımız için pratikte oluşmaz.
            throw new IllegalStateException("Durum JSON'a çevrilemedi", e);
        }
    }

    /**
     * JSON metninden oyun durumunu kurar.
     *
     * @throws InvalidSaveException metin bozuk, eksik, uyumsuz sürümlü veya tutarsızsa
     */
    public GameState decode(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new InvalidSaveException("Kayıt boş");
        }
        try {
            JSONObject root = new JSONObject(json);

            int version = readInt(root, KEY_VERSION);
            if (version != SCHEMA_VERSION) {
                throw new InvalidSaveException("Desteklenmeyen kayıt sürümü: " + version);
            }

            Board board = new Board(readCells(root));
            Piece[] tray = readTray(root);
            long score = readLong(root, KEY_SCORE);
            int streak = readInt(root, KEY_STREAK);
            boolean gameOver = readBoolean(root, KEY_GAME_OVER);

            if (!gameOver && tray[0] == null && tray[1] == null && tray[2] == null) {
                // Oyun motoru tepsi boşalınca hemen yeni üçlü verir; böyle bir kayıt tutarsızdır.
                throw new InvalidSaveException("Bitmemiş oyunda tepsi boş");
            }
            // GameState kurucusu skor < 0 veya seri 0..5 dışıysa IllegalArgumentException atar.
            return new GameState(board, tray, score, streak, gameOver);
        } catch (InvalidSaveException e) {
            throw e;
        } catch (JSONException | IllegalArgumentException e) {
            // JSONException: sözdizimi hatası/kesik metin; IllegalArgumentException: domain
            // kurucularının veya PieceCatalog'un reddettiği değer. Hepsi "geçersiz kayıt" demektir.
            throw new InvalidSaveException("Geçersiz kayıt: " + e.getMessage(), e);
        }
    }

    private static int[] readCells(JSONObject root) throws JSONException {
        JSONArray array = readArray(root, KEY_CELLS);
        int expected = Board.SIZE * Board.SIZE;
        if (array.length() != expected) {
            throw new InvalidSaveException("Tahta " + expected + " hücre olmalı, bulunan: " + array.length());
        }
        int[] cells = new int[expected];
        for (int i = 0; i < expected; i++) {
            cells[i] = toInt(array.get(i), KEY_CELLS + "[" + i + "]");
        }
        return cells; // 0..5 aralık denetimini Board kurucusu yapar.
    }

    private static Piece[] readTray(JSONObject root) throws JSONException {
        JSONArray array = readArray(root, KEY_TRAY);
        if (array.length() != GameState.TRAY_SIZE) {
            throw new InvalidSaveException("Tepsi " + GameState.TRAY_SIZE + " yuvalı olmalı, bulunan: " + array.length());
        }
        Piece[] tray = new Piece[GameState.TRAY_SIZE];
        for (int i = 0; i < tray.length; i++) {
            Object item = array.get(i);
            if (item == JSONObject.NULL) {
                tray[i] = null; // kullanılmış yuva
                continue;
            }
            if (!(item instanceof JSONObject)) {
                throw new InvalidSaveException("Tepsi yuvası nesne veya null olmalı: " + i);
            }
            JSONObject p = (JSONObject) item;
            Object shape = require(p, KEY_SHAPE);
            if (!(shape instanceof String)) {
                throw new InvalidSaveException("shapeId metin olmalı");
            }
            int color = readInt(p, KEY_COLOR);
            // Bilinmeyen şekil veya 1..5 dışı renk -> IllegalArgumentException -> geçersiz kayıt.
            tray[i] = PieceCatalog.create((String) shape, color);
        }
        return tray;
    }

    private static Object require(JSONObject obj, String key) throws JSONException {
        if (!obj.has(key) || obj.isNull(key)) {
            throw new InvalidSaveException("Eksik alan: " + key);
        }
        return obj.get(key);
    }

    private static JSONArray readArray(JSONObject obj, String key) throws JSONException {
        Object value = require(obj, key);
        if (!(value instanceof JSONArray)) {
            throw new InvalidSaveException(key + " dizi olmalı");
        }
        return (JSONArray) value;
    }

    private static boolean readBoolean(JSONObject obj, String key) throws JSONException {
        Object value = require(obj, key);
        if (!(value instanceof Boolean)) {
            throw new InvalidSaveException(key + " true/false olmalı");
        }
        return (Boolean) value;
    }

    private static int readInt(JSONObject obj, String key) throws JSONException {
        return toInt(require(obj, key), key);
    }

    private static long readLong(JSONObject obj, String key) throws JSONException {
        return toLong(require(obj, key), key);
    }

    private static int toInt(Object value, String name) {
        long result = toLong(value, name);
        if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) {
            throw new InvalidSaveException(name + " aralık dışı");
        }
        return (int) result;
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
