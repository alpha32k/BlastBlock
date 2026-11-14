package com.blastblock.game.presentation.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.blastblock.game.R;

import java.util.Random;

/**
 * Oyun ekranının "süs" efektleri: parçacıklar, çizgi boyunca ışık geçişi, ekran sarsıntısı,
 * büyük "pop" yazıları, küçük rozetler ve yukarı süzülen "+puan" yazıları.
 *
 * <p><b>Neden ayrı sınıf?</b> {@link GameView} zaten tahta, tepsi ve sürüklemeyi çiziyor. Efektleri
 * buraya ayırmak iki sınıfı da okunur tutar. Bu sınıf oyun kuralı bilmez; yalnızca "şurada şu renkte
 * patlama yap", "şu yazıyı göster" gibi görsel istekleri yerine getirir. Efektler tamamen görseldir:
 * oyun durumu zaten güncellenmiştir, bu yüzden efekt sürerken oyuncu yeni parça alabilir.
 *
 * <p><b>Performans:</b> {@link #draw} her karede çağrılır. Orada hiçbir nesne üretilmez:
 * <ul>
 *   <li>Parçacıklar sabit boyutlu bir "nesne havuzunda" tutulur; her özellik ayrı bir ilkel dizidir
 *       (x[], y[], hız[] ...). Yeni parçacık, boş bir havuz yuvasını doldurur; havuz doluysa eklenmez.</li>
 *   <li>Yazı ve ışık kayıtları da sabit boyutlu dizilerdir.</li>
 *   <li>Tüm Paint ve gradyanlar kurucuda ya da {@link #setCellSize} içinde bir kez oluşturulur.</li>
 * </ul>
 * Zaman {@link SystemClock#uptimeMillis()} ile ölçülür; her efekt kendi başlangıç anından bu yana
 * geçen süreye bakarak çizilir. Böylece ayrı ayrı ValueAnimator nesnesi gerekmez.
 */
final class GameEffectsRenderer {

    // ------------------------------------------------------------------ süreler ve sınırlar

    /** Havuzdaki en fazla parçacık sayısı (8x8 tahtanın tamamı silinse bile yeterli değilse fazlası atlanır). */
    private static final int MAX_PARTICLES = 400;
    private static final long PARTICLE_LIFE_MS = 550;
    /** Aynı anda en fazla kaç çizgide ışık geçişi (8 satır + 8 sütun). */
    private static final int MAX_SWEEPS = 16;
    private static final long SWEEP_MS = 250;
    private static final long SHAKE_MS = 200;
    /** Sarsıntı genliği (dp). Yüksek yoğunluklu ekranlarda fazla sert olmasın diye {@link #SHAKE_MAX_PX} ile sınırlanır. */
    private static final float SHAKE_DP = 3f;
    /** Sarsıntının en fazla kaç piksel olabileceği (rahatsız etmeyecek, hafif bir titreme). */
    private static final float SHAKE_MAX_PX = 6f;
    /** Aynı anda ekranda en fazla kaç yazı (pop/rozet/+puan). Dolarsa en eskisinin yerine yazılır. */
    private static final int MAX_TEXTS = 8;
    /** Pop yazısının toplam ömrü: ~900 ms tam görünür, sonra solar. */
    private static final long POP_MS = 1200;
    private static final long POP_FADE_START_MS = 900;
    /** "+puan" yazısının ömrü. */
    private static final long POINTS_MS = 800;

    /** Yazı türleri: büyük ölçekli pop, küçük rozet (o da pop'lar) ve yukarı süzülen puan. */
    private static final int KIND_POP = 0;
    private static final int KIND_POINTS = 1;

    // ------------------------------------------------------------------ çizim araçları

    private final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sweepPaint = new Paint();
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final float density;
    private final int glowColor;

    private float cellSize;
    /** Yazıların taşmaması için izin verilen en büyük genişlik (tahta genişliğinin %94'ü). */
    private float maxTextWidth;

    // ------------------------------------------------------------------ parçacık havuzu (ilkel diziler)

    private final boolean[] pActive = new boolean[MAX_PARTICLES];
    private final float[] pX = new float[MAX_PARTICLES];
    private final float[] pY = new float[MAX_PARTICLES];
    private final float[] pVx = new float[MAX_PARTICLES];
    private final float[] pVy = new float[MAX_PARTICLES];
    private final float[] pRot = new float[MAX_PARTICLES];
    private final float[] pVrot = new float[MAX_PARTICLES];
    private final float[] pSize = new float[MAX_PARTICLES];
    private final int[] pColor = new int[MAX_PARTICLES];
    private final long[] pStart = new long[MAX_PARTICLES];
    /** Son güncelleme anı; parçacıklar kare başına geçen süreyle (dt) ilerletilir. */
    private long lastUpdateMs;
    private int activeParticles;

    // ------------------------------------------------------------------ ışık geçişleri

    private final boolean[] sActive = new boolean[MAX_SWEEPS];
    private final boolean[] sIsRow = new boolean[MAX_SWEEPS];
    /** Çizginin başlangıç noktası (satırsa sol kenar, sütunsa üst kenar) ve uzunluğu. */
    private final float[] sStartX = new float[MAX_SWEEPS];
    private final float[] sStartY = new float[MAX_SWEEPS];
    private final float[] sLength = new float[MAX_SWEEPS];
    private final long[] sStart = new long[MAX_SWEEPS];

    // ------------------------------------------------------------------ sarsıntı

    private long shakeStart = -1;
    private float shakeAmplitude;

    // ------------------------------------------------------------------ yazılar

    private final boolean[] tActive = new boolean[MAX_TEXTS];
    private final int[] tKind = new int[MAX_TEXTS];
    private final String[] tText = new String[MAX_TEXTS];
    private final int[] tColor = new int[MAX_TEXTS];
    private final float[] tSize = new float[MAX_TEXTS];
    private final float[] tX = new float[MAX_TEXTS];
    private final float[] tY = new float[MAX_TEXTS];
    private final long[] tStart = new long[MAX_TEXTS];

    GameEffectsRenderer(@NonNull Context context) {
        density = context.getResources().getDisplayMetrics().density;
        glowColor = ContextCompat.getColor(context, R.color.bb_fx_glow);
        particlePaint.setStyle(Paint.Style.FILL);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setShadowLayer(8f * density / 2f, 0f, 3f * density / 2f,
                ContextCompat.getColor(context, R.color.bb_fx_text_shadow));
    }

    /**
     * Tahta hücre boyu değişince (onSizeChanged) çağrılır. Işık geçişinin gradyanı burada bir kez
     * kurulur: 0..2 hücre genişliğinde "saydam → parlak → saydam" yatay şerit. Çizerken tuval
     * kaydırılır (sütunlar için 90° döndürülür); böylece her karede yeni gradyan gerekmez.
     */
    void setCellSize(float cellSize, float boardWidth) {
        this.cellSize = cellSize;
        this.maxTextWidth = boardWidth * 0.94f;
        int transparent = glowColor & 0x00FFFFFF;
        sweepPaint.setShader(new LinearGradient(0f, 0f, cellSize * 2f, 0f,
                new int[]{transparent, glowColor, transparent}, null, Shader.TileMode.CLAMP));
        shakeAmplitude = Math.min(SHAKE_MAX_PX, SHAKE_DP * density);
    }

    // ================================================================== efekt ekleme

    /**
     * Silinen bir hücreden 6–8 renkli kare parçacık saçar.
     *
     * @param centerX hücre merkezinin x'i (piksel)
     * @param centerY hücre merkezinin y'si (piksel)
     * @param color   parçacık rengi (hücrenin rengi)
     */
    void burstCell(float centerX, float centerY, int color) {
        long now = SystemClock.uptimeMillis();
        if (activeParticles == 0) {
            lastUpdateMs = now; // havuz boşken ilk parçacık: dt sıfırdan başlasın
        }
        int count = 6 + random.nextInt(3);
        int searchFrom = 0;
        for (int n = 0; n < count; n++) {
            int i = findFreeParticle(searchFrom);
            if (i < 0) {
                return; // havuz dolu: fazlası atlanır (yavaş telefonu korur)
            }
            searchFrom = i + 1;
            pActive[i] = true;
            activeParticles++;
            pX[i] = centerX + (random.nextFloat() - 0.5f) * cellSize * 0.5f;
            pY[i] = centerY + (random.nextFloat() - 0.5f) * cellSize * 0.5f;
            // Hız birimi: piksel/saniye. Yatayda her iki yöne, dikeyde çoğunlukla yukarı fırlar.
            pVx[i] = (random.nextFloat() - 0.5f) * cellSize * 7f;
            pVy[i] = -(1.5f + random.nextFloat() * 4f) * cellSize;
            pRot[i] = random.nextFloat() * 360f;
            pVrot[i] = (random.nextFloat() - 0.5f) * 720f; // derece/saniye
            pSize[i] = cellSize * (0.12f + random.nextFloat() * 0.12f);
            pColor[i] = color;
            pStart[i] = now;
        }
    }

    private int findFreeParticle(int from) {
        for (int i = from; i < MAX_PARTICLES; i++) {
            if (!pActive[i]) {
                return i;
            }
        }
        return -1;
    }

    /** Bir satır boyunca soldan sağa ışık geçişi. (left, top) satırın sol-üstü, length tahta genişliği. */
    void sweepRow(float left, float top, float length) {
        addSweep(true, left, top, length);
    }

    /** Bir sütun boyunca yukarıdan aşağı ışık geçişi. (left, top) sütunun sol-üstü. */
    void sweepColumn(float left, float top, float length) {
        addSweep(false, left, top, length);
    }

    private void addSweep(boolean isRow, float x, float y, float length) {
        int slot = -1;
        long oldest = Long.MAX_VALUE;
        for (int i = 0; i < MAX_SWEEPS; i++) {
            if (!sActive[i]) {
                slot = i;
                break;
            }
            if (sStart[i] < oldest) {
                oldest = sStart[i];
                slot = i;
            }
        }
        sActive[slot] = true;
        sIsRow[slot] = isRow;
        sStartX[slot] = x;
        sStartY[slot] = y;
        sLength[slot] = length;
        sStart[slot] = SystemClock.uptimeMillis();
    }

    /** Hafif ekran sarsıntısı başlatır (3+ çizgi silinince). */
    void shake() {
        shakeStart = SystemClock.uptimeMillis();
    }

    /**
     * Büyük "pop" yazısı: ölçek 0.6 → 1.15 → 1.0, ~900 ms görünür, sonra solar.
     *
     * @param sizeCells yazı boyu, hücre boyunun katı olarak (başlık ≈ 0.9, rozet ≈ 0.5)
     */
    void addPop(@NonNull String text, int color, float centerX, float centerY, float sizeCells) {
        addText(KIND_POP, text, color, centerX, centerY, sizeCells);
    }

    /** Küçük "+puan" yazısı: verilen noktadan yukarı süzülür ve söner. */
    void addPoints(@NonNull String text, int color, float x, float y) {
        addText(KIND_POINTS, text, color, x, y, 0.5f);
    }

    private void addText(int kind, String text, int color, float x, float y, float sizeCells) {
        int slot = -1;
        long oldest = Long.MAX_VALUE;
        for (int i = 0; i < MAX_TEXTS; i++) {
            if (!tActive[i]) {
                slot = i;
                break;
            }
            if (tStart[i] < oldest) {
                oldest = tStart[i];
                slot = i;
            }
        }
        // Yazı boyu hücreye göre; tahtaya sığmıyorsa (pop en büyük 1.15 ölçeğe çıkar) küçültülür.
        // Ölçüm burada, ekleme anında bir kez yapılır; onDraw'da değil.
        float size = cellSize * sizeCells;
        textPaint.setTextSize(size);
        float width = textPaint.measureText(text) * (kind == KIND_POP ? 1.15f : 1f);
        if (width > maxTextWidth && width > 0f) {
            size *= maxTextWidth / width;
        }
        tActive[slot] = true;
        tKind[slot] = kind;
        tText[slot] = text;
        tColor[slot] = color;
        tSize[slot] = size;
        tX[slot] = x;
        tY[slot] = y;
        tStart[slot] = SystemClock.uptimeMillis();
    }

    /** Tüm efektleri hemen bırakır (ör. yeni oyun). */
    void clear() {
        java.util.Arrays.fill(pActive, false);
        activeParticles = 0;
        java.util.Arrays.fill(sActive, false);
        java.util.Arrays.fill(tActive, false);
        java.util.Arrays.fill(tText, null);
        shakeStart = -1;
    }

    // ================================================================== sarsıntı ofseti

    /**
     * Şu anki sarsıntının x ofseti (piksel). Sönümlü sinüs: genlik zamanla doğrusal olarak sıfıra iner.
     * {@link GameView} tahta ve tepsiyi çizmeden önce tuvali bu kadar kaydırır.
     */
    float shakeX() {
        float p = shakeProgress();
        if (p < 0f) {
            return 0f;
        }
        return (float) Math.sin(p * Math.PI * 9.0) * shakeAmplitude * (1f - p);
    }

    /** Şu anki sarsıntının y ofseti (piksel); x'ten farklı frekansla, daha hafif. */
    float shakeY() {
        float p = shakeProgress();
        if (p < 0f) {
            return 0f;
        }
        return (float) Math.cos(p * Math.PI * 7.0) * shakeAmplitude * 0.6f * (1f - p);
    }

    /** 0..1 arası ilerleme; sarsıntı yoksa -1. */
    private float shakeProgress() {
        if (shakeStart < 0) {
            return -1f;
        }
        long elapsed = SystemClock.uptimeMillis() - shakeStart;
        if (elapsed >= SHAKE_MS) {
            shakeStart = -1;
            return -1f;
        }
        return elapsed / (float) SHAKE_MS;
    }

    // ================================================================== çizim

    /** Işık geçişlerini çizer (tahtanın üstüne, sarsıntı ofsetiyle). Sürüyorsa true döner. */
    boolean drawSweeps(@NonNull Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        boolean running = false;
        float band = cellSize * 2f;
        for (int i = 0; i < MAX_SWEEPS; i++) {
            if (!sActive[i]) {
                continue;
            }
            float p = (now - sStart[i]) / (float) SWEEP_MS;
            if (p >= 1f) {
                sActive[i] = false;
                continue;
            }
            running = true;
            // Şerit, çizginin başından (−band) sonuna kadar kayar: konum = −band + p·(uzunluk + band).
            float offset = -band + p * (sLength[i] + band);
            canvas.save();
            if (sIsRow[i]) {
                canvas.translate(sStartX[i], sStartY[i]);
                canvas.clipRect(0f, 0f, sLength[i], cellSize);
                canvas.translate(offset, 0f);
                canvas.drawRect(0f, 0f, band, cellSize, sweepPaint);
            } else {
                // Sütun: tuvali 90° döndürünce yatay şerit dikey olur; +x yönü aşağıyı gösterir.
                canvas.translate(sStartX[i] + cellSize, sStartY[i]);
                canvas.rotate(90f);
                canvas.clipRect(0f, 0f, sLength[i], cellSize);
                canvas.translate(offset, 0f);
                canvas.drawRect(0f, 0f, band, cellSize, sweepPaint);
            }
            canvas.restore();
        }
        return running;
    }

    /**
     * Parçacıkları ve yazıları çizer (her şeyin en üstünde). Herhangi bir efekt sürüyorsa true döner;
     * GameView bu durumda bir sonraki kareyi ister.
     */
    boolean drawOverlay(@NonNull Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        boolean running = drawParticles(canvas, now);
        running |= drawTexts(canvas, now);
        running |= shakeStart >= 0;
        return running;
    }

    private boolean drawParticles(Canvas canvas, long now) {
        if (activeParticles == 0) {
            return false;
        }
        // dt: son kareden bu yana geçen süre (saniye). Takılan bir karede aşırı sıçramayı önlemek
        // için 50 ms ile sınırlanır.
        float dt = Math.min(0.05f, (now - lastUpdateMs) / 1000f);
        lastUpdateMs = now;
        float gravity = cellSize * 14f; // piksel/saniye²
        for (int i = 0; i < MAX_PARTICLES; i++) {
            if (!pActive[i]) {
                continue;
            }
            float age = (now - pStart[i]) / (float) PARTICLE_LIFE_MS;
            if (age >= 1f) {
                pActive[i] = false;
                activeParticles--;
                continue;
            }
            // Basit fizik: hız yerçekimiyle artar, konum hızla ilerler (Euler adımı).
            pVy[i] += gravity * dt;
            pX[i] += pVx[i] * dt;
            pY[i] += pVy[i] * dt;
            pRot[i] += pVrot[i] * dt;

            float half = pSize[i] * (1f - 0.4f * age) / 2f; // ömür boyunca hafifçe küçülür
            particlePaint.setColor(pColor[i]);
            particlePaint.setAlpha((int) (255 * (1f - age * age)));
            canvas.save();
            canvas.translate(pX[i], pY[i]);
            canvas.rotate(pRot[i]);
            canvas.drawRect(-half, -half, half, half, particlePaint);
            canvas.restore();
        }
        return activeParticles > 0;
    }

    private boolean drawTexts(Canvas canvas, long now) {
        boolean running = false;
        for (int i = 0; i < MAX_TEXTS; i++) {
            if (!tActive[i]) {
                continue;
            }
            long elapsed = now - tStart[i];
            long life = tKind[i] == KIND_POP ? POP_MS : POINTS_MS;
            if (elapsed >= life) {
                tActive[i] = false;
                tText[i] = null;
                continue;
            }
            running = true;
            textPaint.setTextSize(tSize[i]);
            textPaint.setColor(tColor[i]);
            // Yazının dikey ortası: drawText taban çizgisini alır, bu yüzden yarım yükseklik eklenir.
            float baselineShift = tSize[i] * 0.35f;
            if (tKind[i] == KIND_POP) {
                textPaint.setAlpha(popAlpha(elapsed));
                float scale = popScale(elapsed);
                canvas.save();
                canvas.scale(scale, scale, tX[i], tY[i]);
                canvas.drawText(tText[i], tX[i], tY[i] + baselineShift, textPaint);
                canvas.restore();
            } else {
                float p = elapsed / (float) POINTS_MS;
                // Yavaşlayarak yükselir (1-(1-p)²) ve son %40'ta söner.
                float rise = cellSize * 1.3f * (1f - (1f - p) * (1f - p));
                float alpha = p < 0.6f ? 1f : 1f - (p - 0.6f) / 0.4f;
                textPaint.setAlpha((int) (255 * alpha));
                canvas.drawText(tText[i], tX[i], tY[i] - rise + baselineShift, textPaint);
            }
        }
        return running;
    }

    /** Pop ölçeği: 0–180 ms'de 0.6→1.15, 180–300 ms'de 1.15→1.0, sonra 1.0. */
    private static float popScale(long elapsed) {
        if (elapsed < 180) {
            float p = elapsed / 180f;
            return 0.6f + 0.55f * (1f - (1f - p) * (1f - p));
        }
        if (elapsed < 300) {
            return 1.15f - 0.15f * ((elapsed - 180) / 120f);
        }
        return 1f;
    }

    /** Pop saydamlığı: hızlı belirir, ~900 ms tam görünür, sonra solar. */
    private static int popAlpha(long elapsed) {
        if (elapsed < 80) {
            return (int) (255 * (elapsed / 80f));
        }
        if (elapsed < POP_FADE_START_MS) {
            return 255;
        }
        float p = (elapsed - POP_FADE_START_MS) / (float) (POP_MS - POP_FADE_START_MS);
        return (int) (255 * Math.max(0f, 1f - p));
    }
}
