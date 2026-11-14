package com.blastblock.game.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.blastblock.game.domain.repository.SettingsRepository;

/**
 * Stores player preferences and onboarding progress.
 *
 * <p>Rekorla aynı tercih dosyasını ({@link PrefsHighScoreRepository#PREFS_NAME}) kullanır;
 * anahtarlar farklı olduğu için çakışmaz. Ayar hiç değiştirilmediyse titreşim açık (true) kabul
 * edilir.
 */
public final class PrefsSettingsRepository implements SettingsRepository {

    static final String KEY_VIBRATION = "vibration_enabled";
    private static final String KEY_SOUND = "sound_enabled";
    private static final String KEY_TUTORIAL = "tutorial_seen";

    private final SharedPreferences prefs;

    public PrefsSettingsRepository(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PrefsHighScoreRepository.PREFS_NAME, Context.MODE_PRIVATE);
    }

    @Override
    public boolean isVibrationEnabled() {
        return prefs.getBoolean(KEY_VIBRATION, true);
    }

    @Override
    public void setVibrationEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_VIBRATION, enabled).apply();
    }

    @Override
    public boolean isSoundEnabled() {
        return prefs.getBoolean(KEY_SOUND, true);
    }

    @Override
    public void setSoundEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SOUND, enabled).apply();
    }

    @Override
    public boolean hasSeenTutorial() {
        return prefs.getBoolean(KEY_TUTORIAL, false);
    }

    @Override
    public void markTutorialSeen() {
        prefs.edit().putBoolean(KEY_TUTORIAL, true).apply();
    }
}
