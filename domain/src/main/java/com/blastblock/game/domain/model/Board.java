package com.blastblock.game.domain.model;

import java.util.Arrays;

/**
 * 8x8 oyun tahtası.
 *
 * <p>Tahta tek boyutlu, 64 elemanlı bir dizi olarak saklanır (satır-öncelikli düzen):
 * {@code indeks = satır * 8 + sütun}. Hücre değeri 0 ise hücre boştur, 1..5 ise o hücrede
 * ilgili renkte bir blok vardır.
 *
 * <p>Sınıf değişmezdir (immutable): oluşturulduktan sonra içeriği değişmez. Dışarıdan gelen
 * dizi kopyalanır, dışarıya verilen dizi de kopyadır; böylece hiçbir kod tahtayı gizlice
 * bozamaz. Bir hamle yeni bir {@code Board} nesnesi üretir.
 */
public final class Board {

    /** Tahtanın bir kenarındaki hücre sayısı. */
    public static final int SIZE = 8;

    /** Hücrede bulunabilecek en büyük renk kimliği (PieceCatalog.COLOR_COUNT ile aynı). */
    private static final int MAX_COLOR = 5;

    private final int[] cells;

    /**
     * @param cells 64 elemanlı, satır-öncelikli hücre dizisi; her değer 0..5 olmalıdır
     * @throws IllegalArgumentException dizi null, boyutu yanlış veya değer aralık dışıysa
     */
    public Board(int[] cells) {
        if (cells == null || cells.length != SIZE * SIZE) {
            throw new IllegalArgumentException("Tahta 64 hücre içermelidir");
        }
        for (int value : cells) {
            if (value < 0 || value > MAX_COLOR) {
                throw new IllegalArgumentException("Geçersiz hücre değeri: " + value);
            }
        }
        this.cells = cells.clone();
    }

    /** Tamamen boş bir tahta döndürür. */
    public static Board empty() {
        return new Board(new int[SIZE * SIZE]);
    }

    /** Verilen koordinatın tahta içinde olup olmadığını söyler. */
    public static boolean isInside(int row, int col) {
        return row >= 0 && row < SIZE && col >= 0 && col < SIZE;
    }

    public int getCell(int row, int col) {
        checkInside(row, col);
        return cells[row * SIZE + col];
    }

    /** Hücrelerin bir kopyasını döndürür. */
    public int[] getCells() {
        return cells.clone();
    }

    public boolean isEmpty(int row, int col) {
        return getCell(row, col) == 0;
    }

    private static void checkInside(int row, int col) {
        if (!isInside(row, col)) {
            throw new IllegalArgumentException("Tahta dışı koordinat: (" + row + ", " + col + ")");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Board)) return false;
        return Arrays.equals(cells, ((Board) o).cells);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(cells);
    }
}
