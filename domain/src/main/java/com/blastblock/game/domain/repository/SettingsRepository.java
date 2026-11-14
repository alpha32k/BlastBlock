package com.blastblock.game.domain.repository;

/** Kullanıcı ayarlarının saklanması için arayüz. */
public interface SettingsRepository {

    /** Titreşim açık mı? Varsayılan değer true. */
    boolean isVibrationEnabled();

    void setVibrationEnabled(boolean enabled);

    boolean isSoundEnabled();

    void setSoundEnabled(boolean enabled);

    boolean hasSeenTutorial();

    void markTutorialSeen();
}
