package com.blastblock.game.domain.catalog;

import com.blastblock.game.domain.model.Piece;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Oyundaki tüm parça şekillerinin sabit kataloğu.
 *
 * <p>Her şekil okunur, değişmeyen bir kimlikle kaydedilir (ör. "bar3_h", "l4_a"). Bu kimlikler
 * kayıt dosyasına yazıldığı için <b>asla değiştirilmemelidir</b>; aksi hâlde eski kayıtlar açılamaz.
 * Oyunda döndürme olmadığından bir şeklin her yönü ayrı bir kimliktir.
 *
 * <p>Şekiller okunması kolay olsun diye küçük metin çizimleriyle tanımlanır: {@code X} dolu hücre,
 * {@code .} boş hücredir, satırlar {@code |} ile ayrılır. Örneğin {@code "X.|XX"} şu şekildir:
 * <pre>
 *   X.
 *   XX
 * </pre>
 */
public final class PieceCatalog {

    /** Kullanılabilen renk sayısı; renk kimlikleri 1..COLOR_COUNT, 0 boş hücredir. */
    public static final int COLOR_COUNT = 5;

    /** Kimlik → hücre listesi. LinkedHashMap ekleme sırasını korur, böylece ids() sırası sabittir. */
    private static final Map<String, int[][]> SHAPES = new LinkedHashMap<>();
    private static final List<String> IDS;

    static {
        // Tek hücre
        add("single", "X");

        // Düz çubuklar (yatay ve dikey)
        add("bar2_h", "XX");
        add("bar2_v", "X|X");
        add("bar3_h", "XXX");
        add("bar3_v", "X|X|X");
        add("bar4_h", "XXXX");
        add("bar4_v", "X|X|X|X");
        add("bar5_h", "XXXXX");
        add("bar5_v", "X|X|X|X|X");

        // Kareler
        add("square2", "XX|XX");
        add("square3", "XXX|XXX|XXX");

        // 3 hücreli köşe, 4 yön
        add("corner3_a", "X.|XX");
        add("corner3_b", "XX|X.");
        add("corner3_c", "XX|.X");
        add("corner3_d", ".X|XX");

        // 4 hücreli L, 4 yön
        add("l4_a", "X.|X.|XX");
        add("l4_b", "XXX|X..");
        add("l4_c", "XX|.X|.X");
        add("l4_d", "..X|XXX");

        // L'nin aynası J, 4 yön
        add("j4_a", ".X|.X|XX");
        add("j4_b", "X..|XXX");
        add("j4_c", "XX|X.|X.");
        add("j4_d", "XXX|..X");

        // T, 4 yön
        add("t4_a", "XXX|.X.");
        add("t4_b", ".X|XX|.X");
        add("t4_c", ".X.|XXX");
        add("t4_d", "X.|XX|X.");

        // S ve Z (yatay ve dikey)
        add("s4_h", ".XX|XX.");
        add("s4_v", "X.|XX|.X");
        add("z4_h", "XX.|.XX");
        add("z4_v", ".X|XX|X.");

        IDS = Collections.unmodifiableList(new ArrayList<>(SHAPES.keySet()));
    }

    private PieceCatalog() {
    }

    /** Tüm şekil kimlikleri, değişmez ve her zaman aynı sırada. */
    public static List<String> ids() {
        return IDS;
    }

    /**
     * Verilen şekil ve renkte yeni bir parça üretir.
     *
     * @throws IllegalArgumentException kimlik bilinmiyorsa veya renk 1..5 dışındaysa
     */
    public static Piece create(String shapeId, int colorId) {
        int[][] cells = shapeId == null ? null : SHAPES.get(shapeId);
        if (cells == null) {
            throw new IllegalArgumentException("Bilinmeyen şekil kimliği: " + shapeId);
        }
        return new Piece(shapeId, colorId, cells); // Piece kurucusu diziyi kopyalar.
    }

    /** Metin çizimini {satır, sütun} hücre listesine çevirip kataloğa ekler. */
    private static void add(String id, String drawing) {
        String[] rows = drawing.split("\\|");
        List<int[]> cells = new ArrayList<>();
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < rows[r].length(); c++) {
                if (rows[r].charAt(c) == 'X') {
                    cells.add(new int[]{r, c});
                }
            }
        }
        if (SHAPES.put(id, cells.toArray(new int[0][])) != null) {
            throw new IllegalStateException("Tekrarlanan şekil kimliği: " + id);
        }
    }
}
