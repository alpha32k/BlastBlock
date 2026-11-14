package com.blastblock.game.domain.repository;

/** Cihazdaki en yüksek skorun (rekor) saklanması için arayüz. */
public interface HighScoreRepository {

    /** Kayıtlı rekor; hiç oynanmadıysa 0. */
    long getHighScore();

    /**
     * Skoru rekorla karşılaştırır; daha büyükse yeni rekor olarak kaydeder.
     *
     * @return yeni rekor kırıldıysa true
     */
    boolean submitScore(long score);
}
