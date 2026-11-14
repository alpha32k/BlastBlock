package com.blastblock.game.domain.engine;

import com.blastblock.game.domain.model.Board;
import com.blastblock.game.domain.model.GameState;
import com.blastblock.game.domain.model.Piece;
import org.junit.Test;
import static org.junit.Assert.*;

public class DailyPuzzleTest {
    @Test
    public void utcBoundarySelectsNewDay() {
        assertEquals(0, DailyPuzzle.dayAt(86_399_999));
        assertEquals(1, DailyPuzzle.dayAt(86_400_000));
        assertEquals(-1, DailyPuzzle.dayAt(-1));
    }

    @Test
    public void sameDayReplaysTheOpeningAndNextTrayEvenWithDifferentMoves() {
        long day = 20_770;
        GameEngine first = new DailyPuzzle(day).newEngine();
        GameEngine second = new DailyPuzzle(day).newEngine();
        GameState opening = first.newGame();
        GameState a = spendTray(first, opening, false);
        GameState b = spendTray(second, second.newGame(), true);
        assertFalse(java.util.Arrays.equals(a.getBoard().getCells(), b.getBoard().getCells()));
        assertSameOpeningTray(a, b);
        assertSameOpeningTray(opening, first.newGame());
        assertArrayEquals(opening.getBoard().getCells(), first.newGame().getBoard().getCells());
        assertFalse(java.util.Arrays.equals(opening.getBoard().getCells(),
                new DailyPuzzle(day + 1).newEngine().newGame().getBoard().getCells()));
    }

    @Test
    public void openingHasALineClearAndAPlayableTrayAcrossDates() {
        for (long day = 20_700; day < 21_100; day++) {
            GameEngine engine = new DailyPuzzle(day).newEngine();
            GameState game = engine.newGame();
            assertTrue(engine.hasAnyMove(game));
            boolean canClear = false;
            for (int row = 0; row < Board.SIZE; row++) {
                for (int col = 0; col < Board.SIZE; col++) {
                    if (engine.preview(game, 0, row, col).getCompletedRows().length > 0) {
                        canClear = true;
                    }
                }
            }
            assertTrue("day " + day, canClear);
        }
    }

    private static GameState spendTray(GameEngine engine, GameState game, boolean reverse) {
        for (int slot = 0; slot < GameState.TRAY_SIZE; slot++) {
            MoveResult move = null;
            for (int i = 0; i < Board.SIZE * Board.SIZE; i++) {
                int index = reverse ? Board.SIZE * Board.SIZE - 1 - i : i;
                move = engine.place(game, slot, index / Board.SIZE, index % Board.SIZE);
                if (move.isPlaced()) break;
            }
            assertNotNull(move);
            assertTrue(move.isPlaced());
            game = move.getState();
        }
        return game;
    }

    private static void assertSameOpeningTray(GameState first, GameState second) {
        for (int slot = 0; slot < GameState.TRAY_SIZE; slot++) {
            Piece a = first.getTrayPiece(slot);
            Piece b = second.getTrayPiece(slot);
            assertEquals(a.getShapeId(), b.getShapeId());
            assertEquals(a.getColorId(), b.getColorId());
        }
    }
}
