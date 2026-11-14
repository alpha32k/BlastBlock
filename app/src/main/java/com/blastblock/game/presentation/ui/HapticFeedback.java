package com.blastblock.game.presentation.ui;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import androidx.annotation.Nullable;

/**
 * Kısa titreşim (dokunsal geri bildirim) yardımcısı.
 *
 * <p>Android sürümlerine göre üç farklı yol vardır:
 * <ul>
 *   <li>API 31+ (Android 12): titreşim motoru {@link VibratorManager} üzerinden alınır;</li>
 *   <li>API 26+ (Android 8): titreşim {@link VibrationEffect#createOneShot} ile tarif edilir;</li>
 *   <li>API 24–25: eski {@code vibrate(long)} metodu kullanılır.</li>
 * </ul>
 * Ayarlardan titreşim kapatıldıysa ({@link #setEnabled(boolean)}) hiçbir şey yapılmaz.
 */
public final class HapticFeedback {

    /** Parça yerleştirmede kısa tık. */
    public static final long PLACE_MS = 15;
    /** Satır/sütun silmede biraz daha uzun titreşim. */
    public static final long CLEAR_MS = 40;

    @Nullable private final Vibrator vibrator;
    private volatile boolean enabled = true;

    public HapticFeedback(Context context) {
        Context app = context.getApplicationContext();
        Vibrator v;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager manager = app.getSystemService(VibratorManager.class);
            v = manager != null ? manager.getDefaultVibrator() : null;
        } else {
            v = getLegacyVibrator(app);
        }
        vibrator = (v != null && v.hasVibrator()) ? v : null;
    }

    @SuppressWarnings("deprecation")
    @Nullable
    private static Vibrator getLegacyVibrator(Context context) {
        return (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    /** Kullanıcının titreşim ayarı (menüdeki aç/kapa). */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Verilen süre kadar titreşir; ayar kapalıysa ya da cihazda motor yoksa sessizce geçer. */
    public void vibrate(long millis) {
        if (!enabled || vibrator == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrateLegacy(vibrator, millis);
        }
    }

    @SuppressWarnings("deprecation")
    private static void vibrateLegacy(Vibrator vibrator, long millis) {
        vibrator.vibrate(millis);
    }
}
