package com.blastblock.game.domain.engine;

/**
 * Bir parçanın belirli bir konuma bırakılması durumunda ne olacağının önizlemesi.
 *
 * <p>Ekran, parmak tahtanın üzerinde gezerken bu bilgiyle parçanın gölgesini yeşil/kırmızı
 * çizer ve dolacak satır/sütunları vurgular. Önizleme hiçbir durumu değiştirmez.
 */
public final class MovePreview {

    private static final int[] NONE = new int[0];
    private static final MovePreview INVALID = new MovePreview(false, NONE, NONE);

    private final boolean valid;
    private final int[] completedRows;
    private final int[] completedColumns;

    MovePreview(boolean valid, int[] completedRows, int[] completedColumns) {
        this.valid = valid;
        this.completedRows = completedRows.clone();
        this.completedColumns = completedColumns.clone();
    }

    /** Yerleştirilemeyen konum için ortak önizleme nesnesi. */
    public static MovePreview invalid() {
        return INVALID;
    }

    public boolean isValid() {
        return valid;
    }

    /** Bu hamleyle tamamen dolacak satırlar (artan sırada, kopya). */
    public int[] getCompletedRows() {
        return completedRows.clone();
    }

    /** Bu hamleyle tamamen dolacak sütunlar (artan sırada, kopya). */
    public int[] getCompletedColumns() {
        return completedColumns.clone();
    }
}
