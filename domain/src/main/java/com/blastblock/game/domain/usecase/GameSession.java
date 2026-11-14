package com.blastblock.game.domain.usecase;

import com.blastblock.game.domain.engine.GameEngine;
import com.blastblock.game.domain.model.GameState;
import com.blastblock.game.domain.model.ScoreRecord;
import com.blastblock.game.domain.repository.GameLoadResult;
import com.blastblock.game.domain.repository.GameRepository;
import com.blastblock.game.domain.repository.HighScoreRepository;
import com.blastblock.game.domain.repository.ScoreHistoryRepository;

import java.io.IOException;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Coordinates saved games and scores. Call persistence operations on a serial I/O executor. */
public final class GameSession {

    private final GameEngine engine;
    private final GameRepository games;
    private final HighScoreRepository highScores;
    private final ScoreHistoryRepository history;
    private final LongSupplier clock;

    public GameSession(GameEngine engine, GameRepository games, HighScoreRepository highScores,
                       ScoreHistoryRepository history, LongSupplier clock) {
        this.engine = Objects.requireNonNull(engine);
        this.games = Objects.requireNonNull(games);
        this.highScores = Objects.requireNonNull(highScores);
        this.history = Objects.requireNonNull(history);
        this.clock = Objects.requireNonNull(clock);
    }

    private GameSession(GameEngine engine, HighScoreRepository dailyScores,
                        ScoreHistoryRepository history, LongSupplier clock) {
        this.engine = Objects.requireNonNull(engine);
        this.highScores = Objects.requireNonNull(dailyScores);
        this.games = null;
        this.history = Objects.requireNonNull(history);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Daily attempts keep their best score and completed runs in history. */
    public static GameSession daily(GameEngine engine, HighScoreRepository dailyScores,
                                    ScoreHistoryRepository history, LongSupplier clock) {
        return new GameSession(engine, dailyScores, history, clock);
    }

    public OpenResult open(boolean startNew) {
        long highScore = highScores.getHighScore();
        if (startNew || games == null) {
            return startGame(highScore, false);
        }

        GameState restored = null;
        boolean invalidSave;
        try {
            GameLoadResult loaded = games.load();
            invalidSave = loaded.getStatus() == GameLoadResult.Status.INVALID;
            if (loaded.getStatus() == GameLoadResult.Status.ACTIVE) {
                try {
                    restored = engine.restore(loaded.getState());
                } catch (IllegalArgumentException e) {
                    invalidSave = true;
                }
            }
        } catch (IOException e) {
            return new OpenResult(null, highScore, false, true, false);
        }

        if (restored != null && !restored.isGameOver()) {
            return new OpenResult(restored, highScore, false, false, false);
        }
        if (restored != null) {
            highScore = finish(restored).highScore;
        } else if (invalidSave) {
            clear();
        }
        return startGame(highScore, invalidSave);
    }

    public FinishResult finish(GameState finished) {
        boolean newHighScore = highScores.submitScore(finished.getScore());
        long highScore = Math.max(highScores.getHighScore(), finished.getScore());
        int rank = 0;
        boolean historyFailed = false;
        try {
            rank = history.addScore(new ScoreRecord(finished.getScore(), clock.getAsLong(),
                    games == null));
        } catch (IOException | RuntimeException e) {
            historyFailed = true;
        }
        if (games == null) return new FinishResult(highScore, newHighScore, rank, historyFailed);
        boolean saved = save(finished);
        boolean cleared = clear();
        return new FinishResult(highScore, newHighScore, rank, historyFailed || (!saved && !cleared));
    }

    public boolean save(GameState state) {
        if (games == null) {
            highScores.submitScore(state.getScore());
            return true;
        }
        try {
            games.save(state);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private OpenResult startGame(long highScore, boolean invalidSave) {
        GameState state = engine.newGame();
        return new OpenResult(state, highScore, invalidSave, false, !save(state));
    }

    private boolean clear() {
        try {
            games.clearSavedGame();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static final class OpenResult {
        public final GameState game;
        public final long highScore;
        public final boolean invalidSave;
        public final boolean loadFailed;
        public final boolean saveFailed;

        private OpenResult(GameState game, long highScore, boolean invalidSave,
                           boolean loadFailed, boolean saveFailed) {
            this.game = game;
            this.highScore = highScore;
            this.invalidSave = invalidSave;
            this.loadFailed = loadFailed;
            this.saveFailed = saveFailed;
        }
    }

    public static final class FinishResult {
        public final long highScore;
        public final boolean newHighScore;
        public final int topRank;
        public final boolean saveFailed;

        private FinishResult(long highScore, boolean newHighScore, int topRank, boolean saveFailed) {
            this.highScore = highScore;
            this.newHighScore = newHighScore;
            this.topRank = topRank;
            this.saveFailed = saveFailed;
        }
    }
}
