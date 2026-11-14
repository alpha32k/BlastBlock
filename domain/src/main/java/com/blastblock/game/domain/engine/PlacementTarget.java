package com.blastblock.game.domain.engine;

/**
 * Sürükleme sırasında parçanın bırakılacağı hedef hücre.
 *
 * <p>{@link PlacementResolver} parmağın kesirli konumunu bu nesneye çevirir. Hedef ya geçersizdir
 * (parça yakınlarda hiçbir yere sığmıyor) ya da parçanın (0,0) göreli hücresinin konacağı
 * {@code (satır, sütun)} noktasını taşır. {@code snapped} bayrağı, hedefin parmağın tam altındaki
 * yer olmadığını, yakındaki boş bir yere "kaydırıldığını" söyler.
 *
 * <p>Sınıf değişmezdir; geçersiz hedef tek bir ortak nesneyle temsil edilir.
 */
public final class PlacementTarget {

    private static final PlacementTarget INVALID = new PlacementTarget(false, 0, 0, false);

    private final boolean valid;
    private final int row;
    private final int col;
    private final boolean snapped;

    private PlacementTarget(boolean valid, int row, int col, boolean snapped) {
        this.valid = valid;
        this.row = row;
        this.col = col;
        this.snapped = snapped;
    }

    /** Parçanın bırakılamayacağını belirten hedef. */
    public static PlacementTarget invalid() {
        return INVALID;
    }

    /**
     * Geçerli bir hedef üretir.
     *
     * @param row     parçanın sol-üst hücresinin konacağı satır
     * @param col     parçanın sol-üst hücresinin konacağı sütun
     * @param snapped hedef doğrudan değil de yakına kaydırılarak bulunduysa true
     */
    public static PlacementTarget at(int row, int col, boolean snapped) {
        return new PlacementTarget(true, row, col, snapped);
    }

    public boolean isValid() {
        return valid;
    }

    /** Hedef satır; geçersiz hedefte anlamı yoktur (0 döner). */
    public int getRow() {
        return row;
    }

    /** Hedef sütun; geçersiz hedefte anlamı yoktur (0 döner). */
    public int getCol() {
        return col;
    }

    /** Hedef parmağın tam altındaki yer değil, yakındaki boş bir yere kaydırıldıysa true. */
    public boolean isSnapped() {
        return snapped;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlacementTarget)) return false;
        PlacementTarget other = (PlacementTarget) o;
        if (!valid || !other.valid) {
            // Tüm geçersiz hedefler birbirine eşittir.
            return valid == other.valid;
        }
        return row == other.row && col == other.col && snapped == other.snapped;
    }

    @Override
    public int hashCode() {
        if (!valid) {
            return 0;
        }
        int result = 31 + row;
        result = 31 * result + col;
        result = 31 * result + (snapped ? 1 : 2);
        return result;
    }

    @Override
    public String toString() {
        if (!valid) {
            return "PlacementTarget{geçersiz}";
        }
        return "PlacementTarget{" + row + ", " + col + (snapped ? ", kaydırıldı" : "") + "}";
    }
}
