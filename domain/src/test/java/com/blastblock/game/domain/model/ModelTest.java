package com.blastblock.game.domain.model;

import com.blastblock.game.domain.catalog.PieceCatalog;
import org.junit.Test;

import static org.junit.Assert.*;

/** Model sınıflarının girdi doğrulaması ve değişmezliği. */
public class ModelTest {

    @Test
    public void board_copiesInputAndOutputArrays() {
        int[] cells = new int[64];
        Board board = new Board(cells);
        cells[0] = 3;
        assertTrue("Girdi dizisi değişince tahta değişmemeli", board.isEmpty(0, 0));

        int[] out = board.getCells();
        out[1] = 4;
        assertTrue("Çıktı dizisi değişince tahta değişmemeli", board.isEmpty(0, 1));
    }

    @Test
    public void board_rowMajorIndexing() {
        int[] cells = new int[64];
        cells[2 * 8 + 5] = 4;
        Board board = new Board(cells);
        assertEquals(4, board.getCell(2, 5));
        assertFalse(board.isEmpty(2, 5));
        assertTrue(board.isEmpty(5, 2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void board_rejectsWrongSize() {
        new Board(new int[63]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void board_rejectsNull() {
        new Board(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void board_rejectsColorAboveFive() {
        int[] cells = new int[64];
        cells[10] = 6;
        new Board(cells);
    }

    @Test(expected = IllegalArgumentException.class)
    public void board_rejectsNegativeCell() {
        int[] cells = new int[64];
        cells[10] = -1;
        new Board(cells);
    }

    @Test(expected = IllegalArgumentException.class)
    public void board_getCellOutsideThrows() {
        Board.empty().getCell(8, 0);
    }

    @Test
    public void piece_normalizesAndMeasures() {
        Piece p = new Piece("x", 2, new int[][]{{3, 5}, {4, 5}, {4, 6}});
        assertArrayEquals(new int[][]{{0, 0}, {1, 0}, {1, 1}}, p.getCells());
        assertEquals(2, p.getWidth());
        assertEquals(2, p.getHeight());
        assertEquals(3, p.getCellCount());
    }

    @Test
    public void piece_copiesArrays() {
        int[][] cells = {{0, 0}, {0, 1}};
        Piece p = new Piece("x", 1, cells);
        cells[1][1] = 7;
        assertArrayEquals(new int[][]{{0, 0}, {0, 1}}, p.getCells());

        int[][] out = p.getCells();
        out[0][0] = 9;
        assertArrayEquals(new int[][]{{0, 0}, {0, 1}}, p.getCells());
    }

    @Test(expected = IllegalArgumentException.class)
    public void piece_rejectsBadColor() {
        new Piece("x", 0, new int[][]{{0, 0}});
    }

    @Test(expected = IllegalArgumentException.class)
    public void piece_rejectsEmptyCells() {
        new Piece("x", 1, new int[0][]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void piece_rejectsDuplicateCells() {
        new Piece("x", 1, new int[][]{{0, 0}, {0, 0}});
    }

    @Test
    public void gameState_copiesTray() {
        Piece[] tray = {PieceCatalog.create("single", 1), null, null};
        GameState s = new GameState(Board.empty(), tray, 0, 0, false);
        tray[1] = PieceCatalog.create("square2", 1);
        assertNull(s.getTrayPiece(1));

        Piece[] out = s.getTray();
        out[0] = null;
        assertNotNull(s.getTrayPiece(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void gameState_rejectsWrongTrayLength() {
        new GameState(Board.empty(), new Piece[2], 0, 0, false);
    }

    @Test(expected = IllegalArgumentException.class)
    public void gameState_rejectsStreakAboveFive() {
        new GameState(Board.empty(), new Piece[3], 0, 6, true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void gameState_rejectsNegativeScore() {
        new GameState(Board.empty(), new Piece[3], -1, 0, true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void gameState_rejectsBadSlot() {
        new GameState(Board.empty(), new Piece[3], 0, 0, true).getTrayPiece(3);
    }
}
