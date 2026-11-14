package com.blastblock.game.domain.engine;

import com.blastblock.game.domain.catalog.PieceCatalog;
import com.blastblock.game.domain.model.Board;
import com.blastblock.game.domain.model.GameState;
import com.blastblock.game.domain.model.Piece;

import java.util.Random;

/** A replayable UTC puzzle. Keep the seed, shape pool and draw order stable across devices. */
public final class DailyPuzzle {
    private static final long MILLIS_PER_DAY = 86_400_000L;
    private static final String[] SHAPES = {
            "single", "bar2_h", "bar2_v", "bar3_h", "bar3_v", "bar4_h", "bar4_v",
            "square2", "square3", "corner3_a", "corner3_b", "corner3_c", "corner3_d",
            "l4_a", "l4_b", "l4_c", "l4_d", "t4_a", "t4_b", "t4_c", "t4_d",
            "s4_h", "s4_v", "z4_h", "z4_v"
    };

    private final long day;

    public DailyPuzzle(long day) {
        this.day = day;
    }

    public static long dayAt(long timeMillis) {
        return Math.floorDiv(timeMillis, MILLIS_PER_DAY);
    }

    public GameEngine newEngine() {
        return new GameEngine(new TraySequence(day));
    }

    static final class TraySequence {
        private final long day;
        private final Random pieces = new Random();

        TraySequence(long day) {
            this.day = day;
        }

        GameState start() {
            pieces.setSeed(day * 0x9E3779B97F4A7C15L + 91);
            Random layout = new Random(day * 0x9E3779B97F4A7C15L + 13);
            int[] cells = new int[Board.SIZE * Board.SIZE];
            int row = 2 + layout.nextInt(4);
            int gap = layout.nextInt(Board.SIZE - 1);
            int color = 1 + layout.nextInt(5);
            for (int col = 0; col < Board.SIZE; col++) {
                if (col != gap && col != gap + 1) {
                    cells[row * Board.SIZE + col] = color;
                }
            }
            // A second cluster gives the player a choice without blocking the opening clear.
            int otherRow = row >= 4 ? 0 : Board.SIZE - 2;
            int otherCol = layout.nextInt(Board.SIZE - 2);
            cells[otherRow * Board.SIZE + otherCol] = 1 + layout.nextInt(5);
            cells[(otherRow + 1) * Board.SIZE + otherCol] = cells[otherRow * Board.SIZE + otherCol];
            cells[(otherRow + 1) * Board.SIZE + otherCol + 1] = cells[otherRow * Board.SIZE + otherCol];
            Piece[] tray = nextTray();
            tray[0] = PieceCatalog.create("bar2_h", 1 + pieces.nextInt(5));
            return new GameState(new Board(cells), tray, 0, 0, false);
        }

        Piece[] nextTray() {
            Piece[] tray = new Piece[GameState.TRAY_SIZE];
            for (int slot = 0; slot < tray.length; slot++) {
                tray[slot] = PieceCatalog.create(SHAPES[pieces.nextInt(SHAPES.length)],
                        1 + pieces.nextInt(5));
            }
            return tray;
        }
    }
}
