package com.blastblock.game.presentation.state;

import com.blastblock.game.domain.engine.MoveResult;

/**
 * ViewModel'den ekrana gönderilen tek seferlik olay (değişmez).
 *
 * <p>Ekran durumu ({@link GameUiState}) "şu an ne gösterilmeli" sorusunu cevaplar; olaylar ise
 * "şu an bir kez ne olmalı" sorusunu: silme animasyonu, titreşim, oyun sonu penceresi, uyarı mesajı.
 * Olaylar {@link Event} içinde gönderilir, böylece yalnızca bir kez işlenir.
 */
public final class UiEvent {

    /** Olay türleri. */
    public enum Type {
        /** Geçerli bir hamle uygulandı (animasyon ve titreşim için MoveResult dolu). */
        MOVE_COMMITTED,
        /** A completed game with its result, record status and optional rank banner. */
        GAME_OVER,
        /** Kayıtlı oyun bozuktu; silindi ve yeni oyun başlatıldı. */
        INVALID_SAVE,
        /** Oyun diske kaydedilemedi. */
        SAVE_FAILED,
        /**
         * Kayıt geçici bir hatayla okunamadı (IOException). Kayıt silinmez, oyun başlatılmaz;
         * ekran hata mesajı gösterip menüye döner.
         */
        LOAD_FAILED
    }

    private final Type type;
    private final MoveResult moveResult;
    private final boolean newHighScore;
    private final int topRank;

    private UiEvent(Type type, MoveResult moveResult, boolean newHighScore, int topRank) {
        this.type = type;
        this.moveResult = moveResult;
        this.newHighScore = newHighScore;
        this.topRank = topRank;
    }

    public static UiEvent moveCommitted(MoveResult result) {
        return new UiEvent(Type.MOVE_COMMITTED, result, false, 0);
    }

    /**
     * Oyun sonu olayı.
     *
     * @param newHighScore bu oyunla rekor kırıldı mı
     * @param topRank the leaderboard rank to celebrate, or 0 if there is no banner
     */
    public static UiEvent gameOver(MoveResult result, boolean newHighScore, int topRank) {
        return new UiEvent(Type.GAME_OVER, result, newHighScore, topRank);
    }

    public static UiEvent invalidSave() {
        return new UiEvent(Type.INVALID_SAVE, null, false, 0);
    }

    public static UiEvent saveFailed() {
        return new UiEvent(Type.SAVE_FAILED, null, false, 0);
    }

    public static UiEvent loadFailed() {
        return new UiEvent(Type.LOAD_FAILED, null, false, 0);
    }

    public Type getType() {
        return type;
    }

    /** MOVE_COMMITTED ve GAME_OVER'da dolu, diğer türlerde null. */
    public MoveResult getMoveResult() {
        return moveResult;
    }

    /** Yalnızca GAME_OVER'da anlamlı: bu oyunla yeni rekor kırıldı mı? */
    public boolean isNewHighScore() {
        return newHighScore;
    }

    /** Returns the leaderboard rank shown on the game-over banner, or 0. */
    public int getTopRank() {
        return topRank;
    }
}
