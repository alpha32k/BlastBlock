package com.blastblock.game.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.blastblock.game.domain.repository.HighScoreRepository;

/**
 * Rekoru (en yüksek skor) SharedPreferences içinde saklar.
 *
 * <p>Tek bir sayı için ayrı dosya/veritabanı gerekmez; SharedPreferences küçük anahtar-değer
 * verileri için Android'in hazır, kalıcı deposudur. Ayarlarla aynı dosya
 * ({@value #PREFS_NAME}) kullanılır.
 *
 * <p>{@code apply()} değişikliği hemen bellekte günceller, diske ise arka planda yazar; böylece
 * çağıran iş parçacığı beklemez. {@code synchronized}, "oku-karşılaştır-yaz" adımlarının iki
 * iş parçacığı arasında karışmamasını sağlar.
 */
public final class PrefsHighScoreRepository implements HighScoreRepository {

    public static final String PREFS_NAME = "blast_block_prefs";
    static final String KEY_HIGH_SCORE = "high_score";

    private final SharedPreferences prefs;
    private final String scoreKey;

    public PrefsHighScoreRepository(Context context) {
        this(context, KEY_HIGH_SCORE);
    }

    public static PrefsHighScoreRepository daily(Context context, long day) {
        return new PrefsHighScoreRepository(context, "daily_best_" + day);
    }

    private PrefsHighScoreRepository(Context context, String scoreKey) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.scoreKey = scoreKey;
    }

    @Override
    public synchronized long getHighScore() {
        return prefs.getLong(scoreKey, 0L);
    }

    @Override
    public synchronized boolean submitScore(long score) {
        if (score <= getHighScore()) {
            return false; // rekor kırılmadı, hiçbir şey yazılmaz
        }
        prefs.edit().putLong(scoreKey, score).apply();
        return true;
    }
}
