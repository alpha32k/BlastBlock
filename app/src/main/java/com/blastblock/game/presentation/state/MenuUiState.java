package com.blastblock.game.presentation.state;

/** The classic save status, high score and preferences shown in the menu. */
public final class MenuUiState {

    private final long highScore;
    private final boolean savedGame;
    private final boolean vibrationEnabled;
    private final boolean soundEnabled;
    private final boolean tutorialSeen;
    private final boolean saveReadFailed;

    public MenuUiState(long highScore, boolean savedGame, boolean vibrationEnabled,
                       boolean soundEnabled, boolean tutorialSeen, boolean saveReadFailed) {
        this.highScore = highScore;
        this.savedGame = savedGame;
        this.vibrationEnabled = vibrationEnabled;
        this.soundEnabled = soundEnabled;
        this.tutorialSeen = tutorialSeen;
        this.saveReadFailed = saveReadFailed;
    }

    public long getHighScore() {
        return highScore;
    }

    /** Whether the classic button can resume a saved game. */
    public boolean hasSavedGame() {
        return savedGame;
    }

    public boolean isVibrationEnabled() {
        return vibrationEnabled;
    }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public boolean hasSeenTutorial() {
        return tutorialSeen;
    }

    public boolean hasSaveReadFailed() {
        return saveReadFailed;
    }
}
