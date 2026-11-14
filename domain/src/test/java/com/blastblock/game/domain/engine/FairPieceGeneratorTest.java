package com.blastblock.game.domain.engine;

import com.blastblock.game.domain.catalog.PieceCatalog;
import com.blastblock.game.domain.model.Board;
import com.blastblock.game.domain.model.Piece;
import org.junit.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.*;

public class FairPieceGeneratorTest {

    /** Yalnızca (4,4) hücresi boş, geri kalanı dolu bir tahta. */
    private static Board almostFull() {
        int[] cells = new int[64];
        Arrays.fill(cells, 2);
        cells[4 * 8 + 4] = 0;
        return new Board(cells);
    }

    @Test
    public void almostFullBoardAlwaysGetsAFittingPiece() {
        Board board = almostFull();
        for (long seed = 0; seed < 50; seed++) {
            Piece[] tray = new FairPieceGenerator(new Random(seed)).nextTray(board);
            boolean hasSingle = false;
            for (Piece p : tray) {
                hasSingle |= p.getShapeId().equals("single");
            }
            assertTrue("Tohum " + seed + ": tepside sığan tekli parça olmalı", hasSingle);
        }
    }

    @Test
    public void trayHasThreePiecesWithValidColors() {
        FairPieceGenerator gen = new FairPieceGenerator(new Random(7));
        for (int i = 0; i < 100; i++) {
            Piece[] tray = gen.nextTray(Board.empty());
            assertEquals(3, tray.length);
            for (Piece p : tray) {
                assertNotNull(p);
                assertTrue(p.getColorId() >= 1 && p.getColorId() <= PieceCatalog.COLOR_COUNT);
                assertTrue(PieceCatalog.ids().contains(p.getShapeId()));
            }
        }
    }

    @Test
    public void sameSeedGivesSameTray() {
        Piece[] a = new FairPieceGenerator(new Random(42)).nextTray(Board.empty());
        Piece[] b = new FairPieceGenerator(new Random(42)).nextTray(Board.empty());
        assertArrayEquals(a, b);
    }

    @Test
    public void fullBoardStillProducesTray() {
        int[] cells = new int[64];
        Arrays.fill(cells, 1);
        Piece[] tray = new FairPieceGenerator(new Random(1)).nextTray(new Board(cells));
        assertEquals(3, tray.length);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullRandomRejected() {
        new FairPieceGenerator(null);
    }
}
