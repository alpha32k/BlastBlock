package com.blastblock.game.domain.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * Oyunun belirli bir andaki tam durumu: tahta, üç yuvalı tepsi, skor, seri ve oyun bitti bilgisi.
 *
 * <p>Değişmezdir: her hamle yeni bir {@code GameState} üretir. Bu sayede ekran katmanı
 * elindeki durumu güvenle okuyabilir, kayıt katmanı da arka planda aynı nesneyi diske yazabilir.
 *
 * <p>Tepsi her zaman 3 elemanlıdır; {@code null} eleman o yuvadaki parçanın kullanıldığını gösterir.
 * Seri (streak) art arda çizgi temizleyen hamle sayısıdır ve 0..5 aralığındadır.
 */
public final class GameState {

    public static final int TRAY_SIZE = 3;
    public static final int MAX_STREAK = 5;

    private final Board board;
    private final Piece[] tray;
    private final long score;
    private final int streak;
    private final boolean gameOver;

    /**
     * @throws IllegalArgumentException tahta null, tepsi 3 elemanlı değil, skor negatif
     *                                  veya seri 0..5 dışında ise
     */
    public GameState(Board board, Piece[] tray, long score, int streak, boolean gameOver) {
        if (board == null) {
            throw new IllegalArgumentException("Tahta null olamaz");
        }
        if (tray == null || tray.length != TRAY_SIZE) {
            throw new IllegalArgumentException("Tepsi 3 yuvalı olmalıdır");
        }
        if (score < 0) {
            throw new IllegalArgumentException("Skor negatif olamaz");
        }
        if (streak < 0 || streak > MAX_STREAK) {
            throw new IllegalArgumentException("Seri 0..5 arasında olmalıdır");
        }
        this.board = board;
        this.tray = tray.clone(); // Piece zaten değişmez; dizinin sığ kopyası yeterli.
        this.score = score;
        this.streak = streak;
        this.gameOver = gameOver;
    }

    public Board getBoard() {
        return board;
    }

    /** Tepsinin kopyası (null = kullanılmış yuva). */
    public Piece[] getTray() {
        return tray.clone();
    }

    /**
     * @param slot 0..2
     * @return o yuvadaki parça veya kullanıldıysa null
     * @throws IllegalArgumentException yuva numarası geçersizse
     */
    public Piece getTrayPiece(int slot) {
        if (slot < 0 || slot >= TRAY_SIZE) {
            throw new IllegalArgumentException("Geçersiz yuva: " + slot);
        }
        return tray[slot];
    }

    public long getScore() {
        return score;
    }

    public int getStreak() {
        return streak;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GameState)) return false;
        GameState other = (GameState) o;
        return score == other.score
                && streak == other.streak
                && gameOver == other.gameOver
                && board.equals(other.board)
                && Arrays.equals(tray, other.tray);
    }

    @Override
    public int hashCode() {
        return Objects.hash(board, Arrays.hashCode(tray), score, streak, gameOver);
    }
}
