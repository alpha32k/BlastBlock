package com.blastblock.game.presentation.ui;

import android.app.Activity;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Sistem çubukları (üstteki durum çubuğu, alttaki gezinme çubuğu, kamera çentiği) için boşluk.
 *
 * <p>Uygulama içeriği ekranın kenarından kenarına çizilir ("edge-to-edge"; Android 15'ten itibaren
 * zorunlu). Bu durumda düğmeler ve skor sistem çubuklarının ALTINDA kalabilir. Çözüm: Android'in
 * bildirdiği çubuk boyutlarını ("insets") içerik görünümüne iç boşluk (padding) olarak eklemek.
 */
final class SystemBarInsets {

    private SystemBarInsets() {
    }

    /**
     * Pencereyi kenardan kenara çizime geçirir ve {@code content} görünümüne sistem çubukları kadar
     * padding ekler. Görünümün XML'deki kendi padding'i korunur, üstüne eklenir.
     */
    static void apply(Activity activity, View content) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        final int baseLeft = content.getPaddingLeft();
        final int baseTop = content.getPaddingTop();
        final int baseRight = content.getPaddingRight();
        final int baseBottom = content.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(content, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(baseLeft + bars.left, baseTop + bars.top,
                    baseRight + bars.right, baseBottom + bars.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(content);
    }
}
