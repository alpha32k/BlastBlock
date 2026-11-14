package com.blastblock.game.presentation.state;

import com.blastblock.game.domain.model.GameState;

/**
 * Oyun ekranının o anki görüntüsünü tarif eden değişmez durum nesnesi.
 *
 * <p>MVVM'de View (GameActivity/GameView) kendi başına karar vermez; yalnızca bu nesneyi çizer.
 * Her değişiklikte yeni bir nesne üretilir, böylece ekran her zaman tutarlı bir anlık görüntü görür.
 */
public final class GameUiState {

    private final GameState game;
    private final long highScore;
    private final boolean loading;

    /**
     * @param game      oyun durumu; yükleme sürerken ya da kayıt okunamadığında (LOAD_FAILED) null
     * @param highScore gösterilecek rekor (kayıtlı rekor ile mevcut skorun büyüğü)
     * @param loading   kayıt okunuyor mu
     */
    public GameUiState(GameState game, long highScore, boolean loading) {
        this.game = game;
        this.highScore = highScore;
        this.loading = loading;
    }

    /** Yükleme ekranı için hazır durum. */
    public static GameUiState loading() {
        return new GameUiState(null, 0, true);
    }

    /** Oyun durumu; yükleme sürerken ya da kayıt okunamadığında null. */
    public GameState getGame() {
        return game;
    }

    public long getHighScore() {
        return highScore;
    }

    public boolean isLoading() {
        return loading;
    }
}
