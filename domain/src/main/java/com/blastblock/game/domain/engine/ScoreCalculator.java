package com.blastblock.game.domain.engine;

/**
 * Puan hesabı.
 *
 * <p>Bir hamlenin puanı iki parçadan oluşur:
 * <ol>
 *   <li><b>Yerleştirme puanı:</b> parçadaki her hücre için 10 puan → {@code 10 · N}.</li>
 *   <li><b>Temizleme puanı:</b> L çizgi (satır + sütun) silindiyse
 *       {@code (100 · L + 50 · max(0, L − 1)) · çarpan}. Aynı anda birden fazla çizgi silmek
 *       her ek çizgi için 50 bonus getirir (kombo).</li>
 * </ol>
 * Çarpan, seri (streak) değeridir: art arda çizgi silen her hamle seriyi 1 artırır (en fazla 5),
 * çizgi silmeyen bir hamle seriyi 0'a indirir.
 *
 * <p>Örnek: 4 hücreli parça 2 çizgi siler. İlk temizlemede 40 + 250·1 = 290,
 * hemen ardından yine 2 çizgi silinirse 40 + 250·2 = 540.
 */
public final class ScoreCalculator {

    public static final int MAX_MULTIPLIER = 5;

    private ScoreCalculator() {
    }

    /** Parçayı tahtaya koymanın puanı: hücre başına 10. */
    public static long placementPoints(int cells) {
        if (cells < 0) {
            throw new IllegalArgumentException("Hücre sayısı negatif olamaz");
        }
        return 10L * cells;
    }

    /** L çizgi silmenin puanı, verilen çarpanla. L = 0 ise 0. */
    public static long clearPoints(int lines, int multiplier) {
        if (lines < 0 || multiplier < 0) {
            throw new IllegalArgumentException("Çizgi sayısı ve çarpan negatif olamaz");
        }
        if (lines == 0) {
            return 0;
        }
        long base = 100L * lines + 50L * Math.max(0, lines - 1);
        return base * multiplier;
    }

    /** Hamleden sonraki seri: çizgi silindiyse bir artar (en çok 5), silinmediyse 0 olur. */
    public static int nextStreak(int streak, int lines) {
        if (lines > 0) {
            return Math.min(streak + 1, MAX_MULTIPLIER);
        }
        return 0;
    }
}
