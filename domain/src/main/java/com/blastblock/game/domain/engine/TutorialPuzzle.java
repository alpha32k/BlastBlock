package com.blastblock.game.domain.engine;

import com.blastblock.game.domain.catalog.PieceCatalog;
import com.blastblock.game.domain.model.Board;
import com.blastblock.game.domain.model.GameState;
import com.blastblock.game.domain.model.Piece;

import java.util.Objects;

/** One guided move with the same placement and line-clear rules as the full game. */
public final class TutorialPuzzle {
    public static final int TARGET_ROW = 4;
    public static final int TARGET_COL = 4;
    private final GameEngine engine;
    private GameState game;
    private boolean completed;

    public TutorialPuzzle(GameEngine engine) {
        this.engine = Objects.requireNonNull(engine);
        int[] cells = new int[Board.SIZE * Board.SIZE];
        for (int col = 0; col < Board.SIZE; col++) {
            if (col != TARGET_COL) cells[TARGET_ROW * Board.SIZE + col] = 1;
        }
        Piece[] tray = new Piece[GameState.TRAY_SIZE];
        tray[1] = PieceCatalog.create("single", 3);
        game = new GameState(new Board(cells), tray, 0, 0, false);
    }

    public static final int SLOT = 1;

    public GameState getState() {
        return game;
    }

    public boolean isCompleted() {
        return completed;
    }

    public MovePreview preview(int slot, int row, int col) {
        return accepts(slot, row, col) ? engine.preview(game, slot, row, col) : MovePreview.invalid();
    }

    public PlacementTarget resolve(int slot, float row, float col) {
        if (completed || slot != SLOT || Math.abs(row - TARGET_ROW) > 0.85f
                || Math.abs(col - TARGET_COL) > 0.85f
                || !Float.isFinite(row) || !Float.isFinite(col)) {
            return PlacementTarget.invalid();
        }
        return PlacementTarget.at(TARGET_ROW, TARGET_COL,
                Math.round(row) != TARGET_ROW || Math.round(col) != TARGET_COL);
    }

    public MoveResult place(int slot, int row, int col) {
        if (!accepts(slot, row, col)) return MoveResult.rejected(game);
        MoveResult result = engine.place(game, slot, row, col);
        completed = result.isPlaced();
        if (completed) {
            GameState cleared = result.getState();
            game = new GameState(cleared.getBoard(), new Piece[GameState.TRAY_SIZE],
                    cleared.getScore(), cleared.getStreak(), true);
        }
        return result;
    }

    private boolean accepts(int slot, int row, int col) {
        return !completed && slot == SLOT && row == TARGET_ROW && col == TARGET_COL;
    }
}
