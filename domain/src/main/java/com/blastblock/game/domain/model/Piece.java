package com.blastblock.game.domain.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * Tepside duran, tahtaya yerleştirilebilen bir parça.
 *
 * <p>Parça; bir şekil kimliği ({@code shapeId}), bir renk (1..5) ve hücre listesinden oluşur.
 * Her hücre {@code {satır, sütun}} çiftidir ve parçanın kendi sol üst köşesine göredir.
 * Kurucu hücreleri <b>normalize</b> eder: en küçük satır ve en küçük sütun 0 olacak biçimde
 * kaydırır. Böylece aynı şekil her zaman aynı koordinatlarla temsil edilir ve yerleştirmede
 * "(0,0) hücresi tahtadaki (satır, sütun) noktasına gelir" kuralı tutarlı çalışır.
 *
 * <p>Sınıf değişmezdir; diziler kopyalanarak saklanır ve kopyalanarak döndürülür.
 */
public final class Piece {

    private static final int MAX_COLOR = 5;

    private final String shapeId;
    private final int colorId;
    private final int[][] cells;
    private final int width;
    private final int height;

    /**
     * @param shapeId boş olmayan şekil kimliği
     * @param colorId 1..5 arası renk
     * @param cells   en az bir hücre; her eleman {satır, sütun}; tekrar eden hücre olamaz
     * @throws IllegalArgumentException girdi geçersizse
     */
    public Piece(String shapeId, int colorId, int[][] cells) {
        if (shapeId == null || shapeId.isEmpty()) {
            throw new IllegalArgumentException("Şekil kimliği boş olamaz");
        }
        if (colorId < 1 || colorId > MAX_COLOR) {
            throw new IllegalArgumentException("Renk 1..5 arasında olmalıdır: " + colorId);
        }
        if (cells == null || cells.length == 0) {
            throw new IllegalArgumentException("Parça en az bir hücre içermelidir");
        }

        // En küçük satır/sütunu bul; ardından tüm hücreleri bu kadar kaydırarak normalize et.
        int minRow = Integer.MAX_VALUE;
        int minCol = Integer.MAX_VALUE;
        for (int[] cell : cells) {
            if (cell == null || cell.length != 2) {
                throw new IllegalArgumentException("Her hücre {satır, sütun} çifti olmalıdır");
            }
            minRow = Math.min(minRow, cell[0]);
            minCol = Math.min(minCol, cell[1]);
        }

        int[][] normalized = new int[cells.length][];
        int maxRow = 0;
        int maxCol = 0;
        for (int i = 0; i < cells.length; i++) {
            int r = cells[i][0] - minRow;
            int c = cells[i][1] - minCol;
            for (int j = 0; j < i; j++) {
                if (normalized[j][0] == r && normalized[j][1] == c) {
                    throw new IllegalArgumentException("Parçada tekrar eden hücre var");
                }
            }
            normalized[i] = new int[]{r, c};
            maxRow = Math.max(maxRow, r);
            maxCol = Math.max(maxCol, c);
        }

        this.shapeId = shapeId;
        this.colorId = colorId;
        this.cells = normalized;
        this.height = maxRow + 1;
        this.width = maxCol + 1;
    }

    public String getShapeId() {
        return shapeId;
    }

    public int getColorId() {
        return colorId;
    }

    /** Normalize edilmiş hücrelerin derin kopyası. */
    public int[][] getCells() {
        int[][] copy = new int[cells.length][];
        for (int i = 0; i < cells.length; i++) {
            copy[i] = cells[i].clone();
        }
        return copy;
    }

    /** Parçanın kapladığı sütun sayısı. */
    public int getWidth() {
        return width;
    }

    /** Parçanın kapladığı satır sayısı. */
    public int getHeight() {
        return height;
    }

    public int getCellCount() {
        return cells.length;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Piece)) return false;
        Piece other = (Piece) o;
        return colorId == other.colorId
                && shapeId.equals(other.shapeId)
                && Arrays.deepEquals(cells, other.cells);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shapeId, colorId, Arrays.deepHashCode(cells));
    }

    @Override
    public String toString() {
        return "Piece{" + shapeId + ", renk=" + colorId + "}";
    }
}
