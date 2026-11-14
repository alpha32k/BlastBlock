package com.blastblock.game.domain.engine;

import com.blastblock.game.domain.catalog.PieceCatalog;
import com.blastblock.game.domain.model.Board;
import com.blastblock.game.domain.model.GameState;
import com.blastblock.game.domain.model.Piece;

import java.util.Arrays;
import java.util.Random;

/**
 * Oyunun kurallarını uygulayan motor.
 *
 * <p>Motorun kendisi durum tutmaz: her metot bir {@link GameState} alır ve yeni bir durum (veya
 * sonuç) döndürür. Bu "saf fonksiyon" yaklaşımı test yazmayı kolaylaştırır ve ekranın elindeki
 * eski durumun yanlışlıkla bozulmasını engeller.
 *
 * <p>Bir hamlenin adımları ({@link #place}):
 * <ol>
 *   <li>Hamle geçerli mi? (oyun bitmemiş, yuva dolu, parça tahta içinde ve boş hücrelere düşüyor)</li>
 *   <li>Parçayı yerleştir.</li>
 *   <li>Dolu satır ve sütunları <b>aynı tahtadan</b> bul, birlikte sil.</li>
 *   <li>Puanı ve seriyi hesapla.</li>
 *   <li>Üç yuva da boşaldıysa adil üreticiyle yeni üçlü getir.</li>
 *   <li>Kalan parçalardan hiçbiri sığmıyorsa oyunu bitir.</li>
 * </ol>
 */
public final class GameEngine {

    private final FairPieceGenerator generator;
    private final DailyPuzzle.TraySequence daily;

    public GameEngine(Random random) {
        this.generator = new FairPieceGenerator(random);
        this.daily = null;
    }

    GameEngine(DailyPuzzle.TraySequence daily) {
        this.generator = null;
        this.daily = daily;
    }

    /** Boş tahta, sıfır puan ve yeni bir üçlüyle oyun başlatır. */
    public GameState newGame() {
        if (daily != null) return daily.start();
        Board board = Board.empty();
        return new GameState(board, generator.nextTray(board), 0, 0, false);
    }

    /**
     * Kayıttan okunan bir durumu doğrular ve oyunda kullanılabilir hâle getirir.
     *
     * <p>Denetlenenler: 64 hücre ve her biri 0..5; tepsi 3 yuvalı; seri 0..5; skor ≥ 0;
     * tepsideki parçalar katalogdaki şekillerle birebir aynı; oyun bitmemişse en az bir parça mevcut.
     * Oyun bitmemiş görünüyor ama kalan parçaların hiçbiri sığmıyorsa durum "oyun bitti" olarak döner.
     *
     * @throws IllegalArgumentException durum tutarsızsa
     */
    public GameState restore(GameState snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Kayıt boş");
        }
        // Board ve GameState kurucuları da denetler; burada kayıt dosyasına karşı ikinci bir güvence var.
        int[] cells = snapshot.getBoard().getCells();
        if (cells.length != Board.SIZE * Board.SIZE) {
            throw new IllegalArgumentException("Tahta 64 hücre olmalı");
        }
        for (int value : cells) {
            if (value < 0 || value > PieceCatalog.COLOR_COUNT) {
                throw new IllegalArgumentException("Geçersiz hücre değeri: " + value);
            }
        }
        Piece[] tray = snapshot.getTray();
        if (tray.length != GameState.TRAY_SIZE) {
            throw new IllegalArgumentException("Tepsi 3 yuvalı olmalı");
        }
        if (snapshot.getStreak() < 0 || snapshot.getStreak() > GameState.MAX_STREAK) {
            throw new IllegalArgumentException("Seri 0..5 olmalı");
        }
        if (snapshot.getScore() < 0) {
            throw new IllegalArgumentException("Skor negatif olamaz");
        }

        int remaining = 0;
        for (Piece piece : tray) {
            if (piece == null) {
                continue;
            }
            remaining++;
            // Parça, katalogdaki aynı kimlikli şekille hücre hücre aynı olmalı.
            Piece expected = PieceCatalog.create(piece.getShapeId(), piece.getColorId());
            if (!Arrays.deepEquals(expected.getCells(), piece.getCells())) {
                throw new IllegalArgumentException("Parça katalogla uyuşmuyor: " + piece.getShapeId());
            }
        }
        if (!snapshot.isGameOver() && remaining == 0) {
            throw new IllegalArgumentException("Bitmemiş oyunda tepside parça yok");
        }

        if (!snapshot.isGameOver() && !hasAnyMove(snapshot)) {
            return withGameOver(snapshot);
        }
        return snapshot;
    }

    /**
     * Yuvadaki parça (row, col) noktasına bırakılırsa ne olacağını hesaplar; durumu ve rastgele
     * üreteci değiştirmez. Geçerliyse hangi satır ve sütunların dolacağını da söyler.
     */
    public MovePreview preview(GameState state, int slot, int row, int col) {
        Piece piece = playablePiece(state, slot);
        if (piece == null || !BoardRules.canPlace(state.getBoard(), piece, row, col)) {
            return MovePreview.invalid();
        }
        Board placed = BoardRules.place(state.getBoard(), piece, row, col);
        return new MovePreview(true, BoardRules.fullRows(placed), BoardRules.fullColumns(placed));
    }

    /** Hamleyi uygular. Geçersizse durum aynen döner ve {@code isPlaced()} false olur. */
    public MoveResult place(GameState state, int slot, int row, int col) {
        Piece piece = playablePiece(state, slot);
        if (piece == null || !BoardRules.canPlace(state.getBoard(), piece, row, col)) {
            return MoveResult.rejected(state);
        }

        // 1) Yerleştir.
        Board placed = BoardRules.place(state.getBoard(), piece, row, col);

        // 2) Dolu satır ve sütunları AYNI tahtadan bul, sonra birlikte sil.
        int[] rows = BoardRules.fullRows(placed);
        int[] columns = BoardRules.fullColumns(placed);
        int lines = rows.length + columns.length;
        Board cleared = lines > 0 ? BoardRules.clearLines(placed, rows, columns) : placed;

        // 3) Seri ve puan: çarpan, bu hamleden sonraki yeni seri değeridir.
        int streak = ScoreCalculator.nextStreak(state.getStreak(), lines);
        int multiplier = lines > 0 ? streak : 0;
        long delta = ScoreCalculator.placementPoints(piece.getCellCount())
                + ScoreCalculator.clearPoints(lines, multiplier);

        // 4) Kullanılan yuvayı boşalt; üçü de boşaldıysa yeni üçlü getir.
        Piece[] tray = state.getTray();
        tray[slot] = null;
        boolean newTray = false;
        if (tray[0] == null && tray[1] == null && tray[2] == null) {
            tray = daily != null ? daily.nextTray() : generator.nextTray(cleared);
            newTray = true;
        }

        // 5) Oyun sonu: kalan parçaların hiçbiri hiçbir yere sığmıyorsa oyun biter.
        GameState next = new GameState(cleared, tray, state.getScore() + delta, streak, false);
        if (!hasAnyMove(next)) {
            next = withGameOver(next);
        }
        return new MoveResult(true, next, placed, delta, rows, columns, multiplier, newTray);
    }

    /** Tepsideki kullanılmamış parçalardan en az biri tahtada bir yere sığıyor mu? */
    public boolean hasAnyMove(GameState state) {
        if (state == null) {
            throw new IllegalArgumentException("Durum null olamaz");
        }
        for (Piece piece : state.getTray()) {
            if (piece != null && BoardRules.fitsAnywhere(state.getBoard(), piece)) {
                return true;
            }
        }
        return false;
    }

    /** Oyun sürüyorsa ve yuva geçerli ve doluysa o parçayı, değilse null döndürür. */
    private static Piece playablePiece(GameState state, int slot) {
        if (state == null) {
            throw new IllegalArgumentException("Durum null olamaz");
        }
        if (state.isGameOver() || slot < 0 || slot >= GameState.TRAY_SIZE) {
            return null;
        }
        return state.getTrayPiece(slot);
    }

    private static GameState withGameOver(GameState s) {
        return new GameState(s.getBoard(), s.getTray(), s.getScore(), s.getStreak(), true);
    }
}
