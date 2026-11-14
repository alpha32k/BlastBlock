package com.blastblock.game.presentation.viewmodel;

import com.blastblock.game.domain.engine.TopScores;
import com.blastblock.game.domain.model.GameState;
import com.blastblock.game.domain.model.ScoreRecord;
import com.blastblock.game.domain.repository.GameLoadResult;
import com.blastblock.game.domain.repository.GameRepository;
import com.blastblock.game.domain.repository.HighScoreRepository;
import com.blastblock.game.domain.repository.ScoreHistoryRepository;
import com.blastblock.game.domain.repository.SettingsRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Testler için bellek içi sahte (fake) repository'ler. Disk kullanmaz, çağrıları sayar. */
final class Fakes {

    private Fakes() {
    }

    static final class FakeGameRepository implements GameRepository {
        GameLoadResult loadResult = GameLoadResult.empty();
        boolean failLoad;
        boolean failSave;
        boolean failClear;
        int saveCount;
        int clearCount;
        GameState lastSaved;

        @Override
        public GameLoadResult load() throws IOException {
            if (failLoad) {
                throw new IOException("sahte okuma hatası");
            }
            return loadResult;
        }

        @Override
        public void save(GameState state) throws IOException {
            if (failSave) {
                throw new IOException("sahte yazma hatası");
            }
            saveCount++;
            lastSaved = state;
            loadResult = GameLoadResult.active(state);
        }

        @Override
        public void clearSavedGame() throws IOException {
            if (failClear) {
                throw new IOException("sahte silme hatası");
            }
            clearCount++;
            lastSaved = null;
            loadResult = GameLoadResult.empty();
        }
    }

    static final class FakeHighScoreRepository implements HighScoreRepository {
        long highScore;
        int submitCount;
        long lastSubmitted = -1;

        @Override
        public long getHighScore() {
            return highScore;
        }

        @Override
        public boolean submitScore(long score) {
            submitCount++;
            lastSubmitted = score;
            if (score > highScore) {
                highScore = score;
                return true;
            }
            return false;
        }
    }

    static final class FakeSettingsRepository implements SettingsRepository {
        boolean vibration = true;
        boolean sound = true;
        boolean tutorialSeen;

        @Override
        public boolean isSoundEnabled() {
            return sound;
        }

        @Override
        public void setSoundEnabled(boolean enabled) {
            sound = enabled;
        }

        @Override
        public boolean hasSeenTutorial() {
            return tutorialSeen;
        }

        @Override
        public void markTutorialSeen() {
            tutorialSeen = true;
        }

        @Override
        public boolean isVibrationEnabled() {
            return vibration;
        }

        @Override
        public void setVibrationEnabled(boolean enabled) {
            vibration = enabled;
        }
    }

    /**
     * In-memory game history with controls to simulate read and write failures.
     */
    static final class FakeScoreHistoryRepository implements ScoreHistoryRepository {
        List<ScoreRecord> scores = new ArrayList<>();
        boolean failRead;
        boolean failAdd;
        boolean throwRuntimeOnAdd;
        int readCount;
        int addCount;
        ScoreRecord lastAdded;

        @Override
        public List<ScoreRecord> getScores(long offset, int limit) throws IOException {
            readCount++;
            if (failRead) {
                throw new IOException("sahte liste okuma hatası");
            }
            int from = (int) Math.min(offset, scores.size());
            int to = (int) Math.min(offset + limit, scores.size());
            return new ArrayList<>(scores.subList(from, to));
        }

        @Override
        public int addScore(ScoreRecord record) throws IOException {
            addCount++;
            lastAdded = record;
            if (failAdd) {
                throw new IOException("sahte liste yazma hatası");
            }
            if (throwRuntimeOnAdd) {
                throw new IllegalStateException("sahte beklenmedik hata");
            }
            TopScores.Result result = TopScores.insert(scores, record);
            scores.add(record);
            scores.sort(java.util.Comparator.comparingLong(ScoreRecord::getScore).reversed()
                    .thenComparingLong(ScoreRecord::getFinishedAtMillis));
            return result.getRank();
        }
    }
}
