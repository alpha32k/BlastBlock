package com.blastblock.game.domain.engine;

import com.blastblock.game.domain.model.Board;
import com.blastblock.game.domain.model.GameState;

/**
 * {@link GameEngine#place} çağrısının sonucu.
 *
 * <p>Yeni oyun durumunun yanında, ekranın animasyon yapabilmesi için ara bilgileri de taşır:
 * silme öncesi tahta ({@link #getPlacedBoard()}), silinen satır ve sütunlar, kazanılan puan,
 * kullanılan çarpan ve yeni üçlü gelip gelmediği. Geçersiz hamlede {@code isPlaced()} false olur,
 * durum değişmeden geri verilir ve puan 0'dır.
 */
public final class MoveResult {

    private static final int[] NONE = new int[0];

    private final boolean placed;
    private final GameState state;
    private final Board placedBoard;
    private final long scoreDelta;
    private final int[] clearedRows;
    private final int[] clearedColumns;
    private final int multiplier;
    private final boolean newTray;

    MoveResult(boolean placed, GameState state, Board placedBoard, long scoreDelta,
               int[] clearedRows, int[] clearedColumns, int multiplier, boolean newTray) {
        this.placed = placed;
        this.state = state;
        this.placedBoard = placedBoard;
        this.scoreDelta = scoreDelta;
        this.clearedRows = clearedRows.clone();
        this.clearedColumns = clearedColumns.clone();
        this.multiplier = multiplier;
        this.newTray = newTray;
    }

    /** Geçersiz hamle sonucu: durum aynen korunur. */
    static MoveResult rejected(GameState state) {
        return new MoveResult(false, state, state.getBoard(), 0, NONE, NONE, 0, false);
    }

    public boolean isPlaced() {
        return placed;
    }

    public GameState getState() {
        return state;
    }

    /** Parça konduktan sonraki, çizgiler silinmeden önceki tahta. */
    public Board getPlacedBoard() {
        return placedBoard;
    }

    public long getScoreDelta() {
        return scoreDelta;
    }

    public int[] getClearedRows() {
        return clearedRows.clone();
    }

    public int[] getClearedColumns() {
        return clearedColumns.clone();
    }

    /** Silinen toplam çizgi sayısı (satır + sütun). */
    public int getLinesCleared() {
        return clearedRows.length + clearedColumns.length;
    }

    /** Temizleme puanına uygulanan çarpan; çizgi silinmediyse 0. */
    public int getMultiplier() {
        return multiplier;
    }

    /** Bu hamleyle tepsiye yeni üç parça geldi mi? */
    public boolean isNewTray() {
        return newTray;
    }
}
