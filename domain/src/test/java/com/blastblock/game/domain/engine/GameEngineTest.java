package com.blastblock.game.domain.engine;

import com.blastblock.game.domain.model.Board;
import com.blastblock.game.domain.model.GameState;
import com.blastblock.game.domain.model.Piece;
import org.junit.Test;

import java.util.Random;

import static com.blastblock.game.domain.TestBoards.board;
import static com.blastblock.game.domain.TestBoards.state;
import static com.blastblock.game.domain.TestBoards.tray;
import static org.junit.Assert.*;

public class GameEngineTest {

    private final GameEngine engine = new GameEngine(new Random(123));

    private static final String E = "........";

    // ---------------------------------------------------------------- yeni oyun

    @Test
    public void newGameIsEmptyWithFullTray() {
        GameState s = engine.newGame();
        assertEquals(Board.empty(), s.getBoard());
        assertEquals(0, s.getScore());
        assertEquals(0, s.getStreak());
        assertFalse(s.isGameOver());
        for (Piece p : s.getTray()) {
            assertNotNull(p);
        }
    }

    @Test
    public void sameSeedSameGame() {
        assertEquals(new GameEngine(new Random(9)).newGame(), new GameEngine(new Random(9)).newGame());
    }

    // ---------------------------------------------------------------- geçersiz hamleler

    @Test
    public void outOfBoardAndOverlapAreInvalid() {
        Board b = board("1.......", E, E, E, E, E, E, E);
        GameState s = state(b, tray("bar2_h", "bar2_v", "single"), 0, 0);

        assertFalse("sağdan taşma", engine.preview(s, 0, 0, 7).isValid());
        assertFalse("alttan taşma", engine.preview(s, 1, 7, 0).isValid());
        assertFalse("negatif satır", engine.preview(s, 2, -1, 0).isValid());
        assertFalse("negatif sütun", engine.preview(s, 2, 0, -1).isValid());
        assertFalse("dolu hücre", engine.preview(s, 2, 0, 0).isValid());
        assertFalse("kısmen dolu hücre", engine.preview(s, 1, 0, 0).isValid());

        assertTrue("sağ kenara tam oturur", engine.preview(s, 0, 0, 6).isValid());
        assertTrue("alt kenara tam oturur", engine.preview(s, 1, 6, 0).isValid());
    }

    @Test
    public void invalidMoveDoesNotChangeState() {
        Board b = board("1.......", E, E, E, E, E, E, E);
        GameState s = state(b, tray("bar2_h", null, "single"), 50, 2);

        assertRejected(s, engine.place(s, 0, 0, 7));   // taşma
        assertRejected(s, engine.place(s, 2, 0, 0));   // dolu hücre
        assertRejected(s, engine.place(s, 1, 3, 3));   // boş yuva
        assertRejected(s, engine.place(s, 5, 3, 3));   // olmayan yuva
        assertRejected(s, engine.place(s, -1, 3, 3));  // olmayan yuva
    }

    @Test
    public void cannotPlaceWhenGameOver() {
        GameState s = new GameState(Board.empty(), tray("single", null, null), 10, 0, true);
        assertRejected(s, engine.place(s, 0, 0, 0));
        assertFalse(engine.preview(s, 0, 0, 0).isValid());
    }

    private static void assertRejected(GameState before, MoveResult r) {
        assertFalse(r.isPlaced());
        assertSame(before, r.getState());
        assertEquals(0, r.getScoreDelta());
        assertEquals(0, r.getLinesCleared());
        assertFalse(r.isNewTray());
    }

    // ---------------------------------------------------------------- satır / sütun silme

    @Test
    public void singleRowClears() {
        Board b = board("111111..", E, E, E, E, E, E, E);
        GameState s = state(b, tray("bar2_h", "single", "single"), 0, 0);

        MoveResult r = engine.place(s, 0, 0, 6);
        assertTrue(r.isPlaced());
        assertArrayEquals(new int[]{0}, r.getClearedRows());
        assertArrayEquals(new int[0], r.getClearedColumns());
        assertEquals(1, r.getLinesCleared());
        assertEquals(Board.empty(), r.getState().getBoard());
        // Silme öncesi tahtada satır dolu görünmeli (animasyon için).
        assertFalse(r.getPlacedBoard().isEmpty(0, 7));
        assertEquals(20 + 100, r.getScoreDelta());
        assertEquals(1, r.getState().getStreak());
        assertNull(r.getState().getTrayPiece(0));
    }

    @Test
    public void singleColumnClears() {
        Board b = board("...1....", "...1....", "...1....", "...1....",
                "...1....", "...1....", "...1....", E);
        GameState s = state(b, tray("single", "single", "single"), 0, 0);

        MoveResult r = engine.place(s, 1, 7, 3);
        assertArrayEquals(new int[0], r.getClearedRows());
        assertArrayEquals(new int[]{3}, r.getClearedColumns());
        assertEquals(Board.empty(), r.getState().getBoard());
        assertEquals(10 + 100, r.getScoreDelta());
    }

    @Test
    public void rowAndColumnClearTogetherIntersectionOnce() {
        // Satır 2 ve sütun 5, (2,5) dışında dolu. Ayrıca (7,0)'da kalıcı bir blok var.
        Board b = board(
                ".....1..",
                ".....1..",
                "11111.11",
                ".....1..",
                ".....1..",
                ".....1..",
                ".....1..",
                "2....1..");
        GameState s = state(b, tray("single", "single", "single"), 0, 0);

        MovePreview p = engine.preview(s, 0, 2, 5);
        assertTrue(p.isValid());
        assertArrayEquals(new int[]{2}, p.getCompletedRows());
        assertArrayEquals(new int[]{5}, p.getCompletedColumns());

        MoveResult r = engine.place(s, 0, 2, 5);
        assertEquals(2, r.getLinesCleared());
        assertArrayEquals(new int[]{2}, r.getClearedRows());
        assertArrayEquals(new int[]{5}, r.getClearedColumns());
        Board after = r.getState().getBoard();
        for (int i = 0; i < 8; i++) {
            assertTrue(after.isEmpty(2, i));
            assertTrue(after.isEmpty(i, 5));
        }
        assertEquals("ilgisiz blok yerinde kalmalı", 2, after.getCell(7, 0));
        assertEquals(10 + 250, r.getScoreDelta());
    }

    // ---------------------------------------------------------------- puan ve seri

    /** Satır 0-1 ve 2-3, sütun 6-7 dışında dolu: square2 her biri için 2 çizgi siler. */
    private static Board twoDoubleRows() {
        return board("111111..", "111111..", "111111..", "111111..", E, E, E, E);
    }

    @Test
    public void fourCellsTwoLinesFirstClearIs290ThenConsecutive540() {
        GameState s = state(twoDoubleRows(), tray("square2", "square2", "single"), 0, 0);

        MoveResult first = engine.place(s, 0, 0, 6);
        assertEquals(2, first.getLinesCleared());
        assertEquals(1, first.getMultiplier());
        assertEquals(290, first.getScoreDelta());
        assertEquals(1, first.getState().getStreak());

        MoveResult second = engine.place(first.getState(), 1, 2, 6);
        assertEquals(2, second.getLinesCleared());
        assertEquals(2, second.getMultiplier());
        assertEquals(540, second.getScoreDelta());
        assertEquals(2, second.getState().getStreak());
        assertEquals(830, second.getState().getScore());
    }

    @Test
    public void multiplierCappedAtFive() {
        Board b = board("1111111.", E, E, E, E, E, E, E);
        GameState s = state(b, tray("single", "single", "single"), 1000, 5);

        MoveResult r = engine.place(s, 0, 0, 7);
        assertEquals(5, r.getMultiplier());
        assertEquals(5, r.getState().getStreak());
        assertEquals(10 + 100 * 5, r.getScoreDelta());
        assertEquals(1510, r.getState().getScore());
    }

    @Test
    public void moveWithoutClearResetsStreak() {
        GameState s = state(Board.empty(), tray("square2", "single", "single"), 300, 3);
        MoveResult r = engine.place(s, 0, 3, 3);
        assertTrue(r.isPlaced());
        assertEquals(0, r.getState().getStreak());
        assertEquals(0, r.getMultiplier());
        assertEquals(40, r.getScoreDelta());
        assertEquals(340, r.getState().getScore());
    }

    // ---------------------------------------------------------------- yeni üçlü

    @Test
    public void newTrayAfterAllThreeUsed() {
        GameState s = state(Board.empty(), tray("single", "single", "single"), 0, 0);
        MoveResult r1 = engine.place(s, 0, 0, 0);
        assertFalse(r1.isNewTray());
        MoveResult r2 = engine.place(r1.getState(), 1, 0, 1);
        assertFalse(r2.isNewTray());
        assertNull(r2.getState().getTrayPiece(0));
        assertNull(r2.getState().getTrayPiece(1));

        MoveResult r3 = engine.place(r2.getState(), 2, 0, 2);
        assertTrue(r3.isNewTray());
        for (Piece p : r3.getState().getTray()) {
            assertNotNull(p);
        }
    }

    // ---------------------------------------------------------------- oyun sonu

    /**
     * Her satır ve sütunda tam iki boş hücre olan, boş hücrelerin hiçbiri yan yana olmayan tahta:
     * boşluklar (i, i) ve (i, i+2 mod 8). Çizgi tamamlanmaz ve yalnızca tekli parça sığar.
     */
    private static Board isolatedHoles() {
        int[] cells = new int[64];
        java.util.Arrays.fill(cells, 3);
        for (int i = 0; i < 8; i++) {
            cells[i * 8 + i] = 0;
            cells[i * 8 + (i + 2) % 8] = 0;
        }
        return new Board(cells);
    }

    @Test
    public void gameOverWhenNoRemainingPieceFits() {
        GameState s = state(isolatedHoles(), tray("single", "bar2_h", "square3"), 0, 0);
        assertTrue(engine.hasAnyMove(s));

        MoveResult r = engine.place(s, 0, 0, 0);
        assertTrue(r.isPlaced());
        assertEquals(0, r.getLinesCleared());
        assertTrue(r.getState().isGameOver());
        assertFalse(engine.hasAnyMove(r.getState()));
    }

    @Test
    public void notGameOverWhileSomethingFits() {
        GameState s = state(isolatedHoles(), tray("single", "single", "bar2_h"), 0, 0);
        MoveResult r = engine.place(s, 0, 0, 0);
        assertFalse(r.getState().isGameOver());
    }

    // ---------------------------------------------------------------- önizleme

    @Test
    public void previewDoesNotChangeStateOrRandom() {
        GameEngine a = new GameEngine(new Random(5));
        GameEngine b = new GameEngine(new Random(5));
        GameState s = state(twoDoubleRows(), tray("square2", "single", "single"), 0, 0);
        GameState copy = state(twoDoubleRows(), tray("square2", "single", "single"), 0, 0);

        MovePreview p = a.preview(s, 0, 0, 6);
        assertTrue(p.isValid());
        assertArrayEquals(new int[]{0, 1}, p.getCompletedRows());
        assertArrayEquals(new int[0], p.getCompletedColumns());
        for (int i = 0; i < 20; i++) {
            a.preview(s, i % 3, i % 8, (i * 3) % 8);
        }

        assertEquals(copy, s);
        assertEquals("Random tüketilmemeli", b.newGame(), a.newGame());
    }

    @Test
    public void previewWithoutLinesHasEmptyArrays() {
        GameState s = state(Board.empty(), tray("single", "single", "single"), 0, 0);
        MovePreview p = engine.preview(s, 0, 4, 4);
        assertTrue(p.isValid());
        assertEquals(0, p.getCompletedRows().length);
        assertEquals(0, p.getCompletedColumns().length);
        assertFalse(MovePreview.invalid().isValid());
    }

    // ---------------------------------------------------------------- restore

    @Test
    public void restoreAcceptsValidState() {
        GameState s = state(twoDoubleRows(), tray("square2", null, "l4_a"), 500, 2);
        assertEquals(s, engine.restore(s));
    }

    @Test
    public void restoreMarksGameOverWhenNothingFits() {
        GameState s = state(isolatedHoles(), tray(null, "bar2_h", "square3"), 0, 0);
        assertTrue(engine.restore(s).isGameOver());
    }

    @Test(expected = IllegalArgumentException.class)
    public void restoreRejectsNull() {
        engine.restore(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void restoreRejectsActiveGameWithEmptyTray() {
        engine.restore(state(Board.empty(), tray(null, null, null), 0, 0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void restoreRejectsPieceNotMatchingCatalog() {
        Piece fake = new Piece("single", 1, new int[][]{{0, 0}, {0, 1}});
        engine.restore(state(Board.empty(), new Piece[]{fake, null, null}, 0, 0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void restoreRejectsUnknownShape() {
        Piece fake = new Piece("bilinmeyen", 1, new int[][]{{0, 0}});
        engine.restore(state(Board.empty(), new Piece[]{fake, null, null}, 0, 0));
    }

    @Test
    public void restoreAcceptsFinishedGameWithEmptyTray() {
        GameState s = new GameState(Board.empty(), tray(null, null, null), 70, 0, true);
        assertTrue(engine.restore(s).isGameOver());
    }
}
