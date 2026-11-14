package com.blastblock.game.presentation.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.blastblock.game.R;
import com.blastblock.game.domain.catalog.PieceCatalog;
import com.blastblock.game.domain.engine.MovePreview;
import com.blastblock.game.domain.engine.MoveResult;
import com.blastblock.game.domain.engine.PlacementTarget;
import com.blastblock.game.domain.model.Board;
import com.blastblock.game.domain.model.GameState;
import com.blastblock.game.domain.model.Piece;

import java.util.Arrays;

/**
 * Oyun alanı: 8x8 tahtayı, alttaki 3 parça yuvasını (tepsi), sürüklenen parçayı ve animasyonları
 * Canvas ile çizen özel View.
 *
 * <p><b>MVVM'deki yeri:</b> Bu sınıf yalnızca çizer ve dokunuşları yorumlar; kural bilmez.
 * "Parça şu kesirli konumdayken nereye oturur?" sorusunu {@link Callbacks#onResolve}, "oraya konursa
 * hangi çizgiler dolar?" sorusunu {@link Callbacks#onPreview}, "bırak" isteğini {@link Callbacks#onDrop}
 * ile dışarıya (GameActivity → GameViewModel) iletir. ViewModel'i doğrudan tanımaz.
 *
 * <p><b>Akıcı sürükleme (v1.1):</b>
 * <ul>
 *   <li>Parça alınınca 120 ms'lik "kaldırma" animasyonu: tepsi boyundan tam hücre boyuna büyür ve
 *       parmağın üstündeki yerine yükselir. Parmak takibi doğrudandır (gecikme yok); animasyon yalnız
 *       parmağa göre ofseti değiştirir, her MOVE'da yeni animasyon başlatılmaz.</li>
 *   <li>Bırakınca geçerliyse hamle HEMEN yapılır (durum tutarlı kalır), parça ise 100 ms boyunca
 *       hedef hücrelere kayıp küçük bir sıçrama ve parlamayla "yerine oturur" (yalnız görsel).</li>
 *   <li>Silme animasyonu ve efektler sürerken dokunma kilitlenmez; yalnız geri dönmekte olan yuva o an
 *       alınamaz.</li>
 * </ul>
 *
 * <p><b>Performans kuralı:</b> {@link #onDraw} saniyede 60 kez çağrılabilir. Orada yeni nesne
 * oluşturmak çöp toplayıcıyı yorar ve takılmaya yol açar. Bu yüzden tüm Paint, RectF, gradyan ve
 * <i>önceden boyanmış blok bitmap'leri</i> kurucuda ya da {@link #onSizeChanged} içinde BİR KEZ
 * oluşturulur. Sabit boyutta (tahta ve tepsi hücresi) bloklar ölçeklenmeden, renk filtresi
 * uygulanmadan doğrudan kopyalanır; ölçekleme yalnız kısa geçiş animasyonlarında yapılır.
 */
public class GameView extends View {

    /** GameView'in dış dünyaya (Activity'ye) yönelttiği sorular/istekler. */
    public interface Callbacks {
        /** Parça {@code slot} yuvasından alınıp sol-üst hücresi (row, col)'a konursa ne olur? */
        MovePreview onPreview(int slot, int row, int col);

        /**
         * Parçanın sol-üst hücresinin kesirli tahta konumuna göre bırakılacağı hedefi çözer
         * (doğrudan yer ya da yakındaki uygun yer). {@code previous} eşitlik bozmada kullanılır.
         */
        PlacementTarget onResolve(int slot, float fracRow, float fracCol, PlacementTarget previous);

        /** Oyuncu parçayı geçerli bir yere bıraktı. */
        void onDrop(int slot, int row, int col);

        /** Oyuncu tepsiden bir parça aldı (kısa titreşim için). */
        void onPickUp();
    }

    // ------------------------------------------------------------------ yerleşim oranları

    /** Tahta, genişliğin en fazla %92'sini kaplar. */
    private static final float BOARD_WIDTH_RATIO = 0.92f;
    /** Tahta, yüksekliğin en fazla %62'sini kaplar (kalanı tepsi içindir). */
    private static final float BOARD_HEIGHT_RATIO = 0.62f;
    /** Tepsideki parçaların hücresi, tahta hücresinin %55'i kadardır. */
    private static final float TRAY_CELL_RATIO = 0.55f;
    /** En uzun parça 5 hücredir; tepsi yuvası bu uzunluğa göre ayarlanır. */
    private static final int MAX_PIECE_CELLS = 5;
    /** Bir parçanın en fazla hücre sayısı (5x5 kutu); "yerine oturma" önbelleği için. */
    private static final int MAX_PIECE_AREA = MAX_PIECE_CELLS * MAX_PIECE_CELLS;
    /**
     * Sürüklenen parçanın alt kenarı parmağın kaç hücre yukarısında durur. v1.0'da 1.5 idi; parça
     * parmaktan fazla uzak kaldığı için "ağır" hissettiriyordu, 0.9 hem görünür hem yakın.
     */
    private static final float DRAG_LIFT_CELLS = 0.9f;
    /** Hücreler arası boşluk (hücre boyunun oranı). */
    private static final float CELL_GAP_RATIO = 0.06f;
    /** Köşe yuvarlaklığı (hücre boyunun oranı). */
    private static final float CORNER_RATIO = 0.18f;
    /**
     * Kesirli konum bu kadar (hücre) değişmedikçe hedef yeniden çözülmez. Parmak titremesinde
     * motoru boşuna çağırmamak için küçük bir eşik.
     */
    private static final float FRAC_EPSILON = 0.02f;

    // ------------------------------------------------------------------ animasyon süreleri

    private static final long CLEAR_DURATION_MS = 350;
    private static final long LIFT_DURATION_MS = 120;
    private static final long SETTLE_DURATION_MS = 100;
    private static final long RETURN_DURATION_MS = 160;
    private static final long TRAY_APPEAR_DURATION_MS = 250;
    /** "Yerine oturma"nın ilk %70'i kayma, kalanı küçük ölçek sıçraması + parlama. */
    private static final float SETTLE_SLIDE_PART = 0.7f;

    /** Önizleme gölgesinin saydamlığı (0..255). */
    private static final int PREVIEW_ALPHA = 110;
    /** Blokların üst kenarındaki parlaklığın saydamlığı (0..255). */
    private static final int HIGHLIGHT_ALPHA = 80;

    // ------------------------------------------------------------------ durum

    @Nullable private GameState state;
    private int tutorialRow = -1;
    private int tutorialCol = -1;
    private final Paint tutorialPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    @Nullable private Callbacks callbacks;
    private final GameEffectsRenderer effects;

    // ------------------------------------------------------------------ yerleşim (onSizeChanged'de hesaplanır)

    private float cellSize;      // tahta hücresinin piksel boyu
    private float boardLeft;     // tahtanın sol-üst köşesi (piksel)
    private float boardTop;
    private float boardSize;     // tahtanın kenar uzunluğu = cellSize * 8
    private float boardPadding;  // tahta çerçevesinin iç boşluğu
    private float trayTop;       // tepsi alanının üst sınırı
    private float trayBottom;    // tepsi alanının alt sınırı
    private float trayCellSize;  // tepsideki küçültülmüş hücre boyu
    private float slotWidth;     // her yuvanın genişliği = boardSize / 3

    // ------------------------------------------------------------------ çizim araçları (bir kez oluşturulur)

    private final Paint boardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyCellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lineHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint invalidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Yakına kaydırılmış (snapped) hedefin parlak çerçevesi. */
    private final Paint snapFramePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Önbellekteki bitmap'i aynı boyutta kopyalayan kalem (filtre yok). */
    private final Paint bitmapPaint = new Paint();
    /** Yalnız geçiş animasyonlarında, bitmap'i ölçekleyerek çizen kalem. */
    private final Paint scaledBitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    /** Renk kimliğine (1..5) göre blok renkleri; 0. indeks kullanılmaz. */
    private final int[] blockColors = new int[PieceCatalog.COLOR_COUNT + 1];
    private final int textColor;
    /** Doku yoksa kullanılan, dikey gradyanlı kod çizimi kalemleri (renk başına bir tane). */
    private final Paint[] gradientPaints = new Paint[PieceCatalog.COLOR_COUNT + 1];
    /** Gri dokuyu renge boyayan kalemler (renk başına bir tane). */
    private final Paint[] texturePaints = new Paint[PieceCatalog.COLOR_COUNT + 1];
    /** Gri tonlu cam blok dokusu; yüklenemezse null ve kod çizimi kullanılır. */
    @Nullable private final Bitmap blockTexture;

    /**
     * ÖNCEDEN BOYANMIŞ BLOK ÖNBELLEĞİ: renk başına, tahta hücresi boyunda bir bitmap. Doku + renk
     * filtresi (MULTIPLY) her karede 64 hücre için tekrar hesaplanmasın diye onSizeChanged'de bir kez
     * boyanır; onDraw yalnızca hazır pikselleri kopyalar.
     */
    private final Bitmap[] boardBlocks = new Bitmap[PieceCatalog.COLOR_COUNT + 1];
    /** Aynı önbellek, tepsi hücresi boyunda (tepsideki küçük parçalar için). */
    private final Bitmap[] trayBlocks = new Bitmap[PieceCatalog.COLOR_COUNT + 1];

    private final RectF tmpRect = new RectF();
    private final boolean[] clearingMask = new boolean[Board.SIZE * Board.SIZE];

    /**
     * Yuva başına parça ve hücre önbelleği. {@code Piece.getCells()} her çağrıda dizinin KOPYASINI
     * döndürür; onDraw'da çağırmak her karede yeni dizi üretirdi. Bu yüzden hücreler yalnızca durum
     * değişince ({@link #setGameState}) bir kez alınır ve çizim bu önbelleği kullanır.
     */
    private final Piece[] slotPieces = new Piece[GameState.TRAY_SIZE];
    private final int[][][] slotCells = new int[GameState.TRAY_SIZE][][];

    private static final int[] NO_LINES = new int[0];
    /** Önizlemede dolacak satır/sütunlar; dokunma olayında bir kez alınır (onDraw'da değil). */
    private int[] previewRows = NO_LINES;
    private int[] previewCols = NO_LINES;

    // ------------------------------------------------------------------ sürükleme durumu

    private int dragSlot = -1;          // sürüklenen yuva; -1 = sürükleme yok
    private float touchX;               // parmağın son konumu
    private float touchY;
    /** Kaldırma animasyonunun ilerlemesi: 0 = tepside, 1 = tam boy ve parmağın üstünde. */
    private float liftProgress = 1f;
    /** Alındığı anda parçanın tepsideki sol-üstünün parmağa göre ofseti (kaldırmanın başlangıcı). */
    private float liftFromDx;
    private float liftFromDy;
    @Nullable private ValueAnimator liftAnimator;

    /** Çözülmüş hedef (önizleme ve bırakma AYNI hedefi kullanır). Hiçbir zaman null değil. */
    @NonNull private PlacementTarget target = PlacementTarget.invalid();
    /** Parçanın merkezi tahtanın üstünde mi? Değilse hedef çözülmez ve önizleme çizilmez. */
    private boolean pieceOverBoard;
    /** Son çözümde kullanılan kesirli konum (gereksiz çözümü önler). */
    private float lastFracRow = Float.NaN;
    private float lastFracCol = Float.NaN;
    /** Geçersiz hedefte kırmızı gölgenin çizileceği yuvarlanmış hücre. */
    private int roundedRow;
    private int roundedCol;
    /** Son önizleme istenen hücre (gereksiz çağrıyı önler). */
    private int previewRow = Integer.MIN_VALUE;
    private int previewCol = Integer.MIN_VALUE;
    @Nullable private MovePreview preview;
    /** İkinci parmak gibi bir iptalden sonra parmak kalkana kadar dokunuşları yok say. */
    private boolean ignoreUntilUp;

    // ------------------------------------------------------------------ animasyon durumu

    @Nullable private ValueAnimator clearAnimator;
    /**
     * Silinen hücrelerin silme öncesi renkleri (animasyon süresince yalnız bu hücreler kaybolarak
     * çizilir). Tahtanın geri kalanı her zaman GÜNCEL durumdan çizilir; böylece silme animasyonu
     * sürerken yerleştirilen yeni parça hemen görünür.
     */
    private final int[] clearingColors = new int[Board.SIZE * Board.SIZE];
    private float clearProgress;

    @Nullable private ValueAnimator returnAnimator;
    private int returnSlot = -1;
    private float returnFromLeft;
    private float returnFromTop;
    private float returnFromSize;
    private float returnProgress;

    @Nullable private ValueAnimator trayAnimator;
    private float trayAppear = 1f;

    /**
     * "Yerine oturma" animasyonu. Hamle zaten yapıldığı için yuvanın parçası değişmiş olabilir; bu
     * yüzden parçanın göreli hücreleri ve rengi buraya kopyalanır. Animasyon sürerken hedef hücreler
     * ({@link #settleMask}) tahtada çizilmez; onların yerine kayan parça çizilir.
     */
    @Nullable private ValueAnimator settleAnimator;
    private float settleProgress;
    private final int[] settleCellRows = new int[MAX_PIECE_AREA];
    private final int[] settleCellCols = new int[MAX_PIECE_AREA];
    private int settleCount;
    private int settleColorId;
    private int settleWidth;
    private int settleHeight;
    private float settleFromLeft;
    private float settleFromTop;
    private float settleFromSize;
    private float settleToLeft;
    private float settleToTop;
    private final boolean[] settleMask = new boolean[Board.SIZE * Board.SIZE];

    /** Son bırakılan parçanın üst-orta noktası: "+puan" yazısı buradan yükselir. */
    private float lastDropX;
    private float lastDropY;

    public GameView(Context context) {
        this(context, null);
    }

    public GameView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        boardPaint.setColor(ContextCompat.getColor(context, R.color.bb_board));
        emptyCellPaint.setColor(ContextCompat.getColor(context, R.color.bb_board_cell));
        lineHighlightPaint.setColor(ContextCompat.getColor(context, R.color.bb_line_highlight));
        invalidPaint.setColor(ContextCompat.getColor(context, R.color.bb_preview_invalid));
        highlightPaint.setColor(ContextCompat.getColor(context, R.color.white));
        flashPaint.setColor(ContextCompat.getColor(context, R.color.white));
        snapFramePaint.setColor(ContextCompat.getColor(context, R.color.bb_fx_glow));
        snapFramePaint.setStyle(Paint.Style.STROKE);
        textColor = ContextCompat.getColor(context, R.color.bb_text_primary);

        int[] colorRes = {0, R.color.bb_turquoise, R.color.bb_coral, R.color.bb_amber,
                R.color.bb_violet, R.color.bb_mint};
        for (int id = 1; id <= PieceCatalog.COLOR_COUNT; id++) {
            blockColors[id] = ContextCompat.getColor(context, colorRes[id]);
            gradientPaints[id] = new Paint(Paint.ANTI_ALIAS_FLAG);
            // MULTIPLY: dokudaki her pikselin rengi blok rengiyle çarpılır. Doku gri tonlu olduğu
            // için açık yerler renge, koyu yerler rengin koyusuna dönüşür; saydam köşeler saydam kalır.
            // Bu pahalı işlem artık yalnızca önbellek bitmap'leri boyanırken yapılır.
            texturePaints[id] = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            texturePaints[id].setColorFilter(
                    new PorterDuffColorFilter(blockColors[id], PorterDuff.Mode.MULTIPLY));
        }

        blockTexture = loadTexture();
        effects = new GameEffectsRenderer(context);
    }

    /** Doku görselini yükler; herhangi bir sorunda null döner ve kod çizimine geçilir. */
    @Nullable
    private Bitmap loadTexture() {
        try {
            return BitmapFactory.decodeResource(getResources(), R.drawable.bb_block_texture);
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    // ================================================================== dışarıdan çağrılanlar

    public void setCallbacks(@Nullable Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    public void setTutorialTarget(int row, int col) {
        tutorialRow = row;
        tutorialCol = col;
        tutorialPaint.setColor(ContextCompat.getColor(getContext(), R.color.bb_amber));
        tutorialPaint.setStyle(Paint.Style.STROKE);
        invalidate();
    }

    /** Çizilecek oyun durumunu verir (her LiveData güncellemesinde). */
    public void setGameState(@Nullable GameState newState) {
        this.state = newState;
        for (int slot = 0; slot < GameState.TRAY_SIZE; slot++) {
            Piece piece = newState != null ? newState.getTrayPiece(slot) : null;
            if (piece != slotPieces[slot]) {
                // Bu yuvadaki parça değişti (kullanıldı, yeni üçlü ya da yeni oyun geldi).
                // Sürüklenen ya da geri dönen parça buysa, artık başka bir parçayı göstereceği
                // için sürüklemeyi/animasyonu güvenle bırak.
                if (slot == dragSlot) {
                    cancelDrag();
                }
                if (slot == returnSlot && returnAnimator != null) {
                    returnAnimator.cancel();
                }
                slotPieces[slot] = piece;
                slotCells[slot] = piece != null ? piece.getCells() : null;
            }
        }
        if (dragSlot >= 0 && (newState == null || newState.isGameOver())) {
            cancelDrag();
        }
        invalidate();
    }

    /**
     * Geçerli bir hamle sonrası animasyonları başlatır: silinen satır/sütunlar beyazlaşıp küçülerek
     * kaybolur ve parçacıklara ayrılır, çizgi boyunca ışık geçer, 3+ çizgide ekran hafifçe sarsılır;
     * yeni üçlü geldiyse tepsi hafifçe büyüyerek belirir. Hepsi yalnızca görseldir: oyun durumu zaten
     * güncellendiği için bu sırada oyuncu yeni parça alabilir.
     */
    public void playMoveAnimation(@NonNull MoveResult result) {
        if (result.isNewTray()) {
            startTrayAppear();
        }
        if (result.getLinesCleared() <= 0) {
            return;
        }
        // Önceki silme animasyonu sürüyorsa bitir; yerine bu hamlenin silme animasyonu başlar.
        if (clearAnimator != null) {
            clearAnimator.cancel();
        }
        int[] rows = result.getClearedRows();
        int[] cols = result.getClearedColumns();
        // Silinecek hücreleri işaretle: satırlar ve sütunlar birlikte (kesişim bir kez).
        Arrays.fill(clearingMask, false);
        for (int row : rows) {
            for (int col = 0; col < Board.SIZE; col++) {
                clearingMask[row * Board.SIZE + col] = true;
            }
            effects.sweepRow(boardLeft, boardTop + row * cellSize, boardSize);
        }
        for (int col : cols) {
            for (int row = 0; row < Board.SIZE; row++) {
                clearingMask[row * Board.SIZE + col] = true;
            }
            effects.sweepColumn(boardLeft + col * cellSize, boardTop, boardSize);
        }
        Board placed = result.getPlacedBoard();
        // Silinen her hücrenin rengini sakla ve hücrenin renginde parçacıklar saç.
        Arrays.fill(clearingColors, 0);
        for (int i = 0; i < clearingMask.length; i++) {
            if (!clearingMask[i]) {
                continue;
            }
            int row = i / Board.SIZE;
            int col = i % Board.SIZE;
            int colorId = placed.getCell(row, col);
            clearingColors[i] = colorId;
            if (colorId >= 1 && colorId <= PieceCatalog.COLOR_COUNT) {
                effects.burstCell(boardLeft + (col + 0.5f) * cellSize,
                        boardTop + (row + 0.5f) * cellSize, blockColors[colorId]);
            }
        }
        if (result.getLinesCleared() >= 3) {
            effects.shake();
        }

        clearProgress = 0f;
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(CLEAR_DURATION_MS);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(@NonNull ValueAnimator a) {
                clearProgress = (float) a.getAnimatedValue();
                invalidate();
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                if (clearAnimator != a) {
                    return; // yerine yenisi başlamış; onun durumuna dokunma
                }
                // Animasyon bitti: kaybolan hücreler artık çizilmez.
                clearAnimator = null;
                invalidate();
            }
        });
        clearAnimator = animator;
        animator.start();
        postInvalidateOnAnimation();
    }

    /**
     * Tahtanın ortasında büyük "pop" yazısı (ör. "Muhteşem!") ve isteğe bağlı altında daha küçük
     * ikinci satır (ör. "3 Çizgi Kombo!"). Metinler Activity'de strings.xml'den hazırlanır.
     */
    public void showComboPop(@NonNull String title, @Nullable String subtitle) {
        float cx = boardLeft + boardSize / 2f;
        float cy = boardTop + boardSize / 2f;
        if (subtitle != null) {
            effects.addPop(title, blockColors[3], cx, cy - cellSize * 0.45f, 0.9f);
            effects.addPop(subtitle, textColor, cx, cy + cellSize * 0.55f, 0.55f);
        } else {
            effects.addPop(title, blockColors[3], cx, cy, 0.9f);
        }
        postInvalidateOnAnimation();
    }

    /** Seri çarpanı rozeti (ör. "Seri ×3"): pop yazısının altında, daha küçük. */
    public void showStreakBadge(@NonNull String text) {
        effects.addPop(text, blockColors[2], boardLeft + boardSize / 2f,
                boardTop + boardSize / 2f + cellSize * 1.5f, 0.5f);
        postInvalidateOnAnimation();
    }

    /** "Tahta Temiz!" yazısı: pop yazısının üstünde. */
    public void showBoardClear(@NonNull String text) {
        effects.addPop(text, blockColors[5], boardLeft + boardSize / 2f,
                boardTop + boardSize / 2f - cellSize * 1.9f, 0.9f);
        postInvalidateOnAnimation();
    }

    /** "Yeni rekor!" parlaması: tahtanın üst kısmında. */
    public void showNewRecord(@NonNull String text) {
        effects.addPop(text, blockColors[1], boardLeft + boardSize / 2f,
                boardTop + cellSize * 1.1f, 0.8f);
        postInvalidateOnAnimation();
    }

    /** Küçük "+puan" yazısı: son bırakılan parçanın üstünden yukarı süzülür. */
    public void showPointsGained(@NonNull String text) {
        effects.addPoints(text, textColor, lastDropX, lastDropY);
        postInvalidateOnAnimation();
    }

    // ================================================================== yerleşim

    /**
     * Ekran boyutu değişince (ilk açılışta da) tahta ve tepsinin yerini hesaplar.
     *
     * <p>Hücre boyu iki sınırın küçüğüdür: tahta genişliğin %92'sine VE yüksekliğin %62'sine
     * sığmalıdır. Böylece uzun (20:9) ekranda genişlik, kısa/geniş ekranda yükseklik belirleyici
     * olur ve hiçbir oranda taşma olmaz. Kalan yükseklik tepsiye verilir; tahta+tepsi grubu
     * dikeyde ortalanır.
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) {
            return;
        }
        cellSize = Math.min(w * BOARD_WIDTH_RATIO / Board.SIZE, h * BOARD_HEIGHT_RATIO / Board.SIZE);
        boardSize = cellSize * Board.SIZE;
        boardPadding = cellSize * 0.15f;
        slotWidth = boardSize / 3f;

        // Tepsi yüksekliği: en uzun parça (5 hücre) + biraz boşluk; ama ekranda kalan yeri aşmaz.
        float gapBelowBoard = cellSize * 0.5f;
        float wantedTray = cellSize * TRAY_CELL_RATIO * MAX_PIECE_CELLS + cellSize * 0.6f;
        float availableTray = h - (boardSize + 2 * boardPadding) - gapBelowBoard;
        float trayHeight = Math.max(0f, Math.min(wantedTray, availableTray));

        // Tahta+tepsi grubunu dikeyde ortala (fazla yer varsa üstte ve altta eşit boşluk kalır).
        float groupHeight = boardSize + 2 * boardPadding + gapBelowBoard + trayHeight;
        float groupTop = Math.max(0f, (h - groupHeight) / 2f);

        boardLeft = (w - boardSize) / 2f;
        boardTop = groupTop + boardPadding;
        trayTop = boardTop + boardSize + boardPadding + gapBelowBoard;
        trayBottom = trayTop + trayHeight;

        // Tepsi hücresi: %55 hedefi, ama 5 hücrelik parça hem yuva genişliğine hem tepsi
        // yüksekliğine sığmalı.
        trayCellSize = Math.min(cellSize * TRAY_CELL_RATIO,
                Math.min(slotWidth * 0.9f, trayHeight * 0.9f) / MAX_PIECE_CELLS);

        // Gradyanlar hücre boyuna bağlı olduğu için burada (onDraw'da değil) bir kez kurulur.
        // Gradyan 0..cellSize yerel koordinatında tanımlıdır; paintBlockDirect tuvali kaydırıp ölçekler.
        for (int id = 1; id <= PieceCatalog.COLOR_COUNT; id++) {
            int color = blockColors[id];
            int top = ColorUtils.blendARGB(color, Color.WHITE, 0.30f);
            int bottom = ColorUtils.blendARGB(color, Color.BLACK, 0.30f);
            gradientPaints[id].setShader(new LinearGradient(0f, 0f, 0f, cellSize,
                    top, bottom, Shader.TileMode.CLAMP));
        }
        snapFramePaint.setStrokeWidth(Math.max(1f, cellSize * 0.07f));
        buildBlockCache();
        effects.setCellSize(cellSize, boardSize);
    }

    /**
     * Renk başına iki önbellek bitmap'i (tahta ve tepsi hücresi boyunda) boyar. Eski bitmap'ler
     * serbest bırakılır. Bellek yetmezse ilgili önbellek null kalır ve doğrudan çizime geçilir.
     */
    private void buildBlockCache() {
        for (int id = 1; id <= PieceCatalog.COLOR_COUNT; id++) {
            recycle(boardBlocks[id]);
            recycle(trayBlocks[id]);
            boardBlocks[id] = createBlockBitmap(cellSize, id);
            trayBlocks[id] = createBlockBitmap(trayCellSize, id);
        }
    }

    private static void recycle(@Nullable Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    @Nullable
    private Bitmap createBlockBitmap(float size, int colorId) {
        int px = (int) Math.ceil(size);
        if (px <= 0) {
            return null;
        }
        try {
            Bitmap bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
            paintBlockDirect(new Canvas(bitmap), 0f, 0f, size, colorId, 255);
            return bitmap;
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    // ================================================================== çizim

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (cellSize <= 0f) {
            return;
        }
        // Sarsıntı ofseti yalnız tahta ve tepsiye uygulanır; parmaktaki parça sabit kalır.
        float shakeX = effects.shakeX();
        float shakeY = effects.shakeY();
        canvas.save();
        canvas.translate(shakeX, shakeY);
        drawBoard(canvas);
        if (state != null) {
            drawPreview(canvas);
            effects.drawSweeps(canvas);
            drawSettlingPiece(canvas);
            drawTray(canvas);
        }
        canvas.restore();
        if (state != null) {
            drawDraggedPiece(canvas);
            drawReturningPiece(canvas);
        }
        // Parçacıklar ve yazılar her şeyin üstünde. Biri sürüyorsa bir sonraki kareyi iste.
        if (effects.drawOverlay(canvas)) {
            postInvalidateOnAnimation();
        }
    }

    /** Tahta zemini, boş hücreler, dolu hücreler ve silme animasyonu. */
    private void drawBoard(Canvas canvas) {
        float corner = cellSize * CORNER_RATIO;
        tmpRect.set(boardLeft - boardPadding, boardTop - boardPadding,
                boardLeft + boardSize + boardPadding, boardTop + boardSize + boardPadding);
        canvas.drawRoundRect(tmpRect, corner * 1.5f, corner * 1.5f, boardPaint);

        float gap = cellSize * CELL_GAP_RATIO;
        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                float x = boardLeft + col * cellSize;
                float y = boardTop + row * cellSize;
                tmpRect.set(x + gap, y + gap, x + cellSize - gap, y + cellSize - gap);
                canvas.drawRoundRect(tmpRect, corner, corner, emptyCellPaint);
                if (row == tutorialRow && col == tutorialCol) {
                    tutorialPaint.setStrokeWidth(gap);
                    tmpRect.inset(gap / 2f, gap / 2f);
                    canvas.drawRoundRect(tmpRect, corner, corner, tutorialPaint);
                }
            }
        }
        if (state == null) {
            return;
        }

        // Her zaman GÜNCEL tahta çizilir. Silme animasyonu sürüyorsa, silinen (artık boş olan)
        // hücreler eski renkleriyle beyazlaşıp küçülerek ayrıca çizilir.
        Board board = state.getBoard();
        boolean settling = settleAnimator != null;
        boolean clearing = clearAnimator != null;
        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                int colorId = board.getCell(row, col);
                int index = row * Board.SIZE + col;
                float x = boardLeft + col * cellSize;
                float y = boardTop + row * cellSize;
                if (colorId == 0) {
                    if (clearing && clearingMask[index] && clearingColors[index] != 0) {
                        drawClearingCell(canvas, x, y, clearingColors[index]);
                    }
                    continue;
                }
                if (settling && settleMask[index]) {
                    continue; // bu hücreye şu an kayarak oturan parça çiziliyor
                }
                drawBlock(canvas, x, y, cellSize, colorId, 255);
            }
        }
    }

    /**
     * Silinen bir hücre: ilk %35'lik sürede beyaza döner, sonra merkezine doğru küçülerek kaybolur.
     */
    private void drawClearingCell(Canvas canvas, float x, float y, int colorId) {
        float p = clearProgress;
        float whiten = Math.min(1f, p / 0.35f);
        float scale = p < 0.35f ? 1f : 1f - (p - 0.35f) / 0.65f;
        if (scale <= 0f) {
            return;
        }
        float size = cellSize * scale;
        float left = x + (cellSize - size) / 2f;
        float top = y + (cellSize - size) / 2f;
        drawBlock(canvas, left, top, size, colorId, 255);

        float gap = size * CELL_GAP_RATIO;
        float corner = size * CORNER_RATIO;
        tmpRect.set(left + gap, top + gap, left + size - gap, top + size - gap);
        flashPaint.setAlpha((int) (230 * whiten));
        canvas.drawRoundRect(tmpRect, corner, corner, flashPaint);
    }

    /**
     * Tek bir blok çizer. (left, top) bloğun hücre kutusunun sol-üstü, size hücre boyudur.
     *
     * <p>Boyut tahta ya da tepsi hücresine eşitse önceden boyanmış bitmap ölçeklenmeden kopyalanır
     * (en hızlı yol). Geçiş animasyonlarında (kaldırma, dönüş, silme küçülmesi) ara boyutlar için
     * tahta bitmap'i ölçeklenir. Önbellek yoksa (bellek yetmedi) doğrudan çizilir.
     */
    private void drawBlock(Canvas canvas, float left, float top, float size, int colorId, int alpha) {
        if (colorId < 1 || colorId > PieceCatalog.COLOR_COUNT || size <= 0f) {
            return;
        }
        Bitmap exact = size == cellSize ? boardBlocks[colorId]
                : (size == trayCellSize ? trayBlocks[colorId] : null);
        if (exact != null) {
            bitmapPaint.setAlpha(alpha);
            canvas.drawBitmap(exact, left, top, bitmapPaint);
            return;
        }
        Bitmap source = boardBlocks[colorId];
        if (source != null) {
            // Bitmap kenarı ceil(cellSize) pikseldir; aynı oranla ölçeklenince blok geometrisi korunur.
            float scale = size / cellSize;
            tmpRect.set(left, top, left + source.getWidth() * scale, top + source.getHeight() * scale);
            scaledBitmapPaint.setAlpha(alpha);
            canvas.drawBitmap(source, null, tmpRect, scaledBitmapPaint);
            return;
        }
        paintBlockDirect(canvas, left, top, size, colorId, alpha);
    }

    /**
     * Bloğu doğrudan (önbelleksiz) çizer: doku varsa renge boyanmış doku, yoksa yuvarlak köşe +
     * dikey gradyan + üst parlaklık. Önbellek bitmap'leri de bu yöntemle boyanır.
     */
    private void paintBlockDirect(Canvas canvas, float left, float top, float size, int colorId,
                                  int alpha) {
        float gap = size * CELL_GAP_RATIO;
        if (blockTexture != null) {
            Paint paint = texturePaints[colorId];
            paint.setAlpha(alpha);
            tmpRect.set(left + gap, top + gap, left + size - gap, top + size - gap);
            canvas.drawBitmap(blockTexture, null, tmpRect, paint);
            return;
        }
        // Kod çizimi: gradyan cellSize boyunda tanımlı olduğundan tuvali bloğun köşesine kaydırıp
        // size/cellSize oranında ölçekliyoruz; böylece aynı gradyan her boyutta doğru görünür.
        float scale = size / cellSize;
        float g = cellSize * CELL_GAP_RATIO;
        float corner = cellSize * CORNER_RATIO;
        canvas.save();
        canvas.translate(left, top);
        canvas.scale(scale, scale);
        Paint paint = gradientPaints[colorId];
        paint.setAlpha(alpha);
        tmpRect.set(g, g, cellSize - g, cellSize - g);
        canvas.drawRoundRect(tmpRect, corner, corner, paint);
        // Üst kenar parlaklığı: bloğun üst kısmında ince, yarı saydam beyaz bir şerit.
        highlightPaint.setAlpha(HIGHLIGHT_ALPHA * alpha / 255);
        tmpRect.set(g * 2.5f, g * 1.8f, cellSize - g * 2.5f, cellSize * 0.30f);
        canvas.drawRoundRect(tmpRect, corner * 0.6f, corner * 0.6f, highlightPaint);
        canvas.restore();
    }

    /**
     * Sürükleme sırasında çözülmüş hedef hücrelerdeki gölge ve dolacak çizgilerin vurgusu.
     * Hedef yakına kaydırıldıysa (snapped) gölgenin etrafına parlak bir çerçeve de çizilir; böylece
     * oyuncu parçanın parmağın tam altındaki yere değil, yakındaki uygun yere gideceğini görür.
     */
    private void drawPreview(Canvas canvas) {
        if (dragSlot < 0 || state == null || !pieceOverBoard) {
            return;
        }
        Piece piece = slotPieces[dragSlot];
        int[][] cells = slotCells[dragSlot];
        if (piece == null || cells == null) {
            return;
        }
        float gap = cellSize * CELL_GAP_RATIO;
        float corner = cellSize * CORNER_RATIO;
        if (target.isValid() && preview != null && preview.isValid()) {
            // Tamamlanacak satır ve sütunları açık bir şeritle vurgula.
            for (int row : previewRows) {
                canvas.drawRect(boardLeft, boardTop + row * cellSize,
                        boardLeft + boardSize, boardTop + (row + 1) * cellSize, lineHighlightPaint);
            }
            for (int col : previewCols) {
                canvas.drawRect(boardLeft + col * cellSize, boardTop,
                        boardLeft + (col + 1) * cellSize, boardTop + boardSize, lineHighlightPaint);
            }
            // Parçanın konacağı hücrelerde yarı saydam gölge.
            boolean snapped = target.isSnapped();
            for (int[] cell : cells) {
                float x = boardLeft + (target.getCol() + cell[1]) * cellSize;
                float y = boardTop + (target.getRow() + cell[0]) * cellSize;
                drawBlock(canvas, x, y, cellSize, piece.getColorId(), PREVIEW_ALPHA);
                if (snapped) {
                    tmpRect.set(x + gap, y + gap, x + cellSize - gap, y + cellSize - gap);
                    canvas.drawRoundRect(tmpRect, corner, corner, snapFramePaint);
                }
            }
        } else {
            // Yakında uygun yer yok: parmağın altındaki (yuvarlanmış) hücrelerde hafif kırmızı gölge.
            for (int[] cell : cells) {
                int row = roundedRow + cell[0];
                int col = roundedCol + cell[1];
                if (!Board.isInside(row, col)) {
                    continue;
                }
                float x = boardLeft + col * cellSize;
                float y = boardTop + row * cellSize;
                tmpRect.set(x + gap, y + gap, x + cellSize - gap, y + cellSize - gap);
                canvas.drawRoundRect(tmpRect, corner, corner, invalidPaint);
            }
        }
    }

    /** Tepsideki parçalar (sürüklenen ve geri dönen yuva hariç). Boş (null) yuvalar çizilmez. */
    private void drawTray(Canvas canvas) {
        if (state == null) {
            return;
        }
        float size = trayAppear >= 1f ? trayCellSize : trayCellSize * trayAppear;
        for (int slot = 0; slot < GameState.TRAY_SIZE; slot++) {
            Piece piece = slotPieces[slot];
            if (piece == null || slot == dragSlot || slot == returnSlot) {
                continue;
            }
            float left = trayPieceLeft(slot, piece, size);
            float top = trayPieceTop(piece, size);
            drawPiece(canvas, slot, left, top, size, 255);
        }
    }

    /** Parmağın üstünde çizilen sürüklenen parça (kaldırma animasyonu sürüyorsa ara boyda). */
    private void drawDraggedPiece(Canvas canvas) {
        if (dragSlot < 0 || state == null) {
            return;
        }
        Piece piece = slotPieces[dragSlot];
        if (piece == null) {
            return;
        }
        drawPiece(canvas, dragSlot, currentDragLeft(piece), currentDragTop(piece),
                currentDragSize(), 255);
    }

    /** Geçersiz bırakılan parçanın yuvasına kayarak ve küçülerek dönüşü. */
    private void drawReturningPiece(Canvas canvas) {
        if (returnSlot < 0 || state == null) {
            return;
        }
        Piece piece = slotPieces[returnSlot];
        if (piece == null) {
            return;
        }
        float p = returnProgress;
        float size = p >= 1f ? trayCellSize : returnFromSize + (trayCellSize - returnFromSize) * p;
        float left = returnFromLeft + (trayPieceLeft(returnSlot, piece, trayCellSize) - returnFromLeft) * p;
        float top = returnFromTop + (trayPieceTop(piece, trayCellSize) - returnFromTop) * p;
        drawPiece(canvas, returnSlot, left, top, size, 255);
    }

    /**
     * "Yerine oturma": parça bırakıldığı yerden hedef hücrelere kayar (ilk %70), sonra hafifçe büyüyüp
     * küçülür ve üstünde kısa bir beyaz parlama söner. Hücreler kopyalanmış önbellekten çizilir.
     */
    private void drawSettlingPiece(Canvas canvas) {
        if (settleAnimator == null || settleCount == 0) {
            return;
        }
        float p = settleProgress;
        float t = Math.min(1f, p / SETTLE_SLIDE_PART);
        t = 1f - (1f - t) * (1f - t); // yavaşlayarak yaklaş
        float size = t >= 1f ? cellSize : settleFromSize + (cellSize - settleFromSize) * t;
        float left = settleFromLeft + (settleToLeft - settleFromLeft) * t;
        float top = settleFromTop + (settleToTop - settleFromTop) * t;

        float bounce = 0f;  // 0..1..0 arası sıçrama eğrisi
        if (p > SETTLE_SLIDE_PART) {
            float q = (p - SETTLE_SLIDE_PART) / (1f - SETTLE_SLIDE_PART);
            bounce = (float) Math.sin(q * Math.PI);
        }
        canvas.save();
        if (bounce > 0f) {
            float scale = 1f + 0.08f * bounce;
            canvas.scale(scale, scale, left + settleWidth * size / 2f, top + settleHeight * size / 2f);
        }
        for (int i = 0; i < settleCount; i++) {
            drawBlock(canvas, left + settleCellCols[i] * size, top + settleCellRows[i] * size,
                    size, settleColorId, 255);
        }
        if (bounce > 0f) {
            float gap = size * CELL_GAP_RATIO;
            float corner = size * CORNER_RATIO;
            flashPaint.setAlpha((int) (150 * bounce));
            for (int i = 0; i < settleCount; i++) {
                float x = left + settleCellCols[i] * size;
                float y = top + settleCellRows[i] * size;
                tmpRect.set(x + gap, y + gap, x + size - gap, y + size - gap);
                canvas.drawRoundRect(tmpRect, corner, corner, flashPaint);
            }
        }
        canvas.restore();
    }

    /** Bir yuvadaki parçayı önbellekteki hücrelerle çizer (onDraw'da dizi üretmeden). */
    private void drawPiece(Canvas canvas, int slot, float left, float top, float size, int alpha) {
        Piece piece = slotPieces[slot];
        int[][] cells = slotCells[slot];
        if (piece == null || cells == null) {
            return;
        }
        int colorId = piece.getColorId();
        for (int[] cell : cells) {
            drawBlock(canvas, left + cell[1] * size, top + cell[0] * size, size, colorId, alpha);
        }
    }

    // ================================================================== koordinat yardımcıları

    /** Tepsideki parçanın sol kenarı: parça, kendi yuvasının ortasına yatayda ortalanır. */
    private float trayPieceLeft(int slot, Piece piece, float size) {
        float slotCenterX = boardLeft + slotWidth * (slot + 0.5f);
        return slotCenterX - piece.getWidth() * size / 2f;
    }

    /** Tepsideki parçanın üst kenarı: parça, tepsi alanının ortasına dikeyde ortalanır. */
    private float trayPieceTop(Piece piece, float size) {
        float centerY = (trayTop + trayBottom) / 2f;
        return centerY - piece.getHeight() * size / 2f;
    }

    /**
     * Tam kaldırılmış parçanın sol kenarının PARMAĞA göre ofseti: parça parmağa göre yatayda
     * ortalanır → −(genişlik × hücre) / 2.
     */
    private float liftedDx(Piece piece) {
        return -piece.getWidth() * cellSize / 2f;
    }

    /**
     * Tam kaldırılmış parçanın üst kenarının parmağa göre ofseti. Parçanın ALT kenarı parmağın
     * {@value #DRAG_LIFT_CELLS} hücre yukarısında durur ki parmak hedef hücreleri örtmesin:
     * alt kenar = parmak y − 0.9 × hücre; üst kenar = alt kenar − parça yüksekliği × hücre.
     */
    private float liftedDy(Piece piece) {
        return -DRAG_LIFT_CELLS * cellSize - piece.getHeight() * cellSize;
    }

    /** Şu an ekranda çizilen sürükleme boyu (kaldırma sırasında tepsi boyundan tahta boyuna). */
    private float currentDragSize() {
        return liftProgress >= 1f ? cellSize
                : trayCellSize + (cellSize - trayCellSize) * liftProgress;
    }

    /** Şu an çizilen sol kenar: parmak konumu + (tepsi ofsetinden kaldırılmış ofsete geçiş). */
    private float currentDragLeft(Piece piece) {
        return touchX + liftFromDx + (liftedDx(piece) - liftFromDx) * liftProgress;
    }

    private float currentDragTop(Piece piece) {
        return touchY + liftFromDy + (liftedDy(piece) - liftFromDy) * liftProgress;
    }

    /** Dokunulan noktanın denk geldiği tepsi yuvası; tepsi dışındaysa -1. */
    private int slotAt(float x, float y) {
        if (y < trayTop - cellSize * 0.3f || y > getHeight()) {
            return -1;
        }
        int slot = (int) Math.floor((x - boardLeft) / slotWidth);
        return Math.max(0, Math.min(GameState.TRAY_SIZE - 1, slot));
    }

    // ================================================================== dokunma

    /**
     * Yalnız oyun yoksa, bittiyse ya da View kapalıysa giriş kilitlidir. Silme animasyonu ve
     * efektler yalnız görsel olduğundan dokunuşu ENGELLEMEZ (durum zaten güncel).
     */
    private boolean isInputLocked() {
        return state == null || state.isGameOver() || !isEnabled();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                ignoreUntilUp = false;
                return onDown(event.getX(), event.getY());

            case MotionEvent.ACTION_POINTER_DOWN:
                // İkinci parmak: karışıklığı önlemek için sürüklemeyi iptal et, parça yuvasına döner.
                if (dragSlot >= 0) {
                    cancelDragWithReturn();
                }
                ignoreUntilUp = true;
                return true;

            case MotionEvent.ACTION_MOVE:
                if (ignoreUntilUp || dragSlot < 0) {
                    return true;
                }
                // Doğrudan takip: parça bu karede parmağın yeni konumunda çizilir.
                touchX = event.getX(0);
                touchY = event.getY(0);
                updateTarget();
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
                if (!ignoreUntilUp && dragSlot >= 0) {
                    touchX = event.getX();
                    touchY = event.getY();
                    onRelease();
                }
                ignoreUntilUp = false;
                performClick();
                return true;

            case MotionEvent.ACTION_CANCEL:
                // Sistem dokunuşu elimizden aldı (ör. bildirim paneli): güvenle geri dön.
                if (dragSlot >= 0) {
                    cancelDragWithReturn();
                }
                ignoreUntilUp = false;
                return true;

            default:
                return super.onTouchEvent(event);
        }
    }

    /** Erişilebilirlik: dokunuşu işleyen özel View'ler performClick'i de desteklemelidir. */
    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private boolean onDown(float x, float y) {
        if (isInputLocked() || state == null || cellSize <= 0f) {
            return true;
        }
        int slot = slotAt(x, y);
        // Yuvasına geri dönmekte olan parça o an alınamaz; diğer yuvalar serbest.
        if (slot < 0 || slot == returnSlot || slotPieces[slot] == null) {
            return true;
        }
        Piece piece = slotPieces[slot];
        dragSlot = slot;
        touchX = x;
        touchY = y;
        // Kaldırmanın başlangıcı: parçanın tepsideki yeri, parmağa göre ofset olarak.
        liftFromDx = trayPieceLeft(slot, piece, trayCellSize) - x;
        liftFromDy = trayPieceTop(piece, trayCellSize) - y;
        startLift();
        resetTarget();
        updateTarget();
        if (callbacks != null) {
            callbacks.onPickUp();
        }
        invalidate();
        return true;
    }

    /** 120 ms'lik kaldırma: parça tepsi boyundan tam boya büyür, parmağın üstüne yükselir. */
    private void startLift() {
        if (liftAnimator != null) {
            liftAnimator.cancel();
        }
        liftProgress = 0f;
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(LIFT_DURATION_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(@NonNull ValueAnimator a) {
                liftProgress = (float) a.getAnimatedValue();
                invalidate();
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                if (liftAnimator == a) {
                    liftProgress = 1f;
                    liftAnimator = null;
                    invalidate();
                }
            }
        });
        liftAnimator = animator;
        animator.start();
    }

    /** Hedef önbelleğini sıfırlar (yeni sürükleme başında ve sürükleme bitince). */
    private void resetTarget() {
        target = PlacementTarget.invalid();
        pieceOverBoard = false;
        lastFracRow = Float.NaN;
        lastFracCol = Float.NaN;
        preview = null;
        previewRows = NO_LINES;
        previewCols = NO_LINES;
        previewRow = Integer.MIN_VALUE;
        previewCol = Integer.MIN_VALUE;
    }

    /**
     * ÖNİZLEME VE BIRAKMANIN ORTAK HESABI.
     *
     * <p>Tam kaldırılmış parçanın sol-üst köşesinin KESİRLİ tahta konumu hesaplanır:
     * sütun = (parçaSol − tahtaSol) / hücre, satır = (parçaÜst − tahtaÜst) / hücre (yuvarlanmaz).
     * Kaldırma animasyonu sürse bile hesap animasyonun bitiş konumuna göre yapılır; böylece hedef,
     * animasyonun hangi karede olduğuna bağlı değildir.
     *
     * <p>Parçanın merkezi tahta alanının dışındaysa hedef geçersizdir (parça tepsiye döner). İçindeyse
     * {@link Callbacks#onResolve} doğrudan ya da yakındaki uygun yeri bulur. Çözüm yalnız kesirli
     * konum anlamlı ölçüde değiştiğinde yapılır; önizleme (dolacak çizgiler) ise yalnız çözülmüş hücre
     * değiştiğinde istenir.
     */
    private void updateTarget() {
        if (dragSlot < 0) {
            return;
        }
        Piece piece = slotPieces[dragSlot];
        if (piece == null) {
            return;
        }
        float left = touchX + liftedDx(piece);
        float top = touchY + liftedDy(piece);
        float centerX = left + piece.getWidth() * cellSize / 2f;
        float centerY = top + piece.getHeight() * cellSize / 2f;
        boolean over = centerX >= boardLeft && centerX <= boardLeft + boardSize
                && centerY >= boardTop && centerY <= boardTop + boardSize;
        float fracCol = (left - boardLeft) / cellSize;
        float fracRow = (top - boardTop) / cellSize;

        if (over == pieceOverBoard
                && Math.abs(fracRow - lastFracRow) < FRAC_EPSILON
                && Math.abs(fracCol - lastFracCol) < FRAC_EPSILON) {
            return; // anlamlı değişiklik yok: motoru boşuna yorma
        }
        pieceOverBoard = over;
        lastFracRow = fracRow;
        lastFracCol = fracCol;
        roundedRow = Math.round(fracRow);
        roundedCol = Math.round(fracCol);

        PlacementTarget resolved = null;
        if (over && callbacks != null) {
            resolved = callbacks.onResolve(dragSlot, fracRow, fracCol, target);
        }
        target = resolved != null ? resolved : PlacementTarget.invalid();

        if (!target.isValid()) {
            preview = null;
            previewRows = NO_LINES;
            previewCols = NO_LINES;
            previewRow = Integer.MIN_VALUE;
            previewCol = Integer.MIN_VALUE;
            return;
        }
        if (target.getRow() == previewRow && target.getCol() == previewCol) {
            return; // aynı hücre: önizleme zaten güncel
        }
        previewRow = target.getRow();
        previewCol = target.getCol();
        preview = callbacks != null ? callbacks.onPreview(dragSlot, previewRow, previewCol) : null;
        // Dolacak çizgiler (kopya diziler) burada, dokunma olayında bir kez alınır.
        if (preview != null && preview.isValid()) {
            previewRows = preview.getCompletedRows();
            previewCols = preview.getCompletedColumns();
        } else {
            previewRows = NO_LINES;
            previewCols = NO_LINES;
        }
    }

    /**
     * Parmak kalktı: çözülmüş hedef geçerliyse hamle HEMEN yapılır ve parça görsel olarak yerine
     * oturur; değilse parça yuvasına geri gönderilir.
     */
    private void onRelease() {
        Piece piece = dragSlot >= 0 ? slotPieces[dragSlot] : null;
        int[][] cells = dragSlot >= 0 ? slotCells[dragSlot] : null;
        if (state == null || piece == null || cells == null) {
            cancelDrag();
            return;
        }
        updateTarget(); // son konumla (önizlemeyle AYNI hesap) hedefi tazele
        if (!target.isValid() || preview == null || !preview.isValid() || callbacks == null) {
            cancelDragWithReturn();
            return;
        }
        int slot = dragSlot;
        int row = target.getRow();
        int col = target.getCol();
        startSettle(piece, cells, row, col);
        lastDropX = boardLeft + (col + piece.getWidth() / 2f) * cellSize;
        lastDropY = boardTop + row * cellSize;
        cancelDrag(); // sürükleme durumunu temizle; yeni durum LiveData ile hemen gelecek
        callbacks.onDrop(slot, row, col);
        // Motor yine de reddettiyse (yuvadaki parça değişmediyse) görsel oturmayı geri al;
        // parça zaten tepside çizilecek.
        if (slotPieces[slot] == piece && settleAnimator != null) {
            settleAnimator.cancel();
        }
    }

    /** Bırakılan parçanın hedef hücrelere kayıp oturmasını başlatır (yalnız görsel). */
    private void startSettle(Piece piece, int[][] cells, int row, int col) {
        if (settleAnimator != null) {
            settleAnimator.cancel();
        }
        settleCount = Math.min(cells.length, MAX_PIECE_AREA);
        Arrays.fill(settleMask, false);
        for (int i = 0; i < settleCount; i++) {
            settleCellRows[i] = cells[i][0];
            settleCellCols[i] = cells[i][1];
            int r = row + cells[i][0];
            int c = col + cells[i][1];
            if (Board.isInside(r, c)) {
                settleMask[r * Board.SIZE + c] = true;
            }
        }
        settleColorId = piece.getColorId();
        settleWidth = piece.getWidth();
        settleHeight = piece.getHeight();
        settleFromLeft = currentDragLeft(piece);
        settleFromTop = currentDragTop(piece);
        settleFromSize = currentDragSize();
        settleToLeft = boardLeft + col * cellSize;
        settleToTop = boardTop + row * cellSize;
        settleProgress = 0f;

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(SETTLE_DURATION_MS);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(@NonNull ValueAnimator a) {
                settleProgress = (float) a.getAnimatedValue();
                invalidate();
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                if (settleAnimator == a) {
                    settleAnimator = null;
                    settleCount = 0;
                    Arrays.fill(settleMask, false);
                    invalidate();
                }
            }
        });
        settleAnimator = animator;
        animator.start();
    }

    /** Sürüklemeyi animasyonsuz bırakır. */
    private void cancelDrag() {
        dragSlot = -1;
        if (liftAnimator != null) {
            liftAnimator.cancel();
        }
        liftProgress = 1f;
        resetTarget();
        invalidate();
    }

    /** Sürüklemeyi bırakır ve parçayı kısa bir animasyonla yuvasına döndürür. */
    private void cancelDragWithReturn() {
        Piece piece = dragSlot >= 0 ? slotPieces[dragSlot] : null;
        if (state == null || piece == null) {
            cancelDrag();
            return;
        }
        if (returnAnimator != null) {
            returnAnimator.cancel(); // bitiş dinleyicisi returnSlot'u sıfırlar; önce bitir
        }
        // Dönüş, parçanın ŞU AN çizildiği yerden ve boydan başlar (kaldırma yarıda kalmış olabilir).
        returnSlot = dragSlot;
        returnFromLeft = currentDragLeft(piece);
        returnFromTop = currentDragTop(piece);
        returnFromSize = currentDragSize();
        cancelDrag();
        returnProgress = 0f;
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(RETURN_DURATION_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(@NonNull ValueAnimator a) {
                returnProgress = (float) a.getAnimatedValue();
                invalidate();
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                if (returnAnimator == a) {
                    returnSlot = -1;
                    returnAnimator = null;
                    invalidate();
                }
            }
        });
        returnAnimator = animator;
        animator.start();
    }

    /** Yeni üçlü gelince tepsi parçalarını küçükten normal boya büyüterek gösterir. */
    private void startTrayAppear() {
        if (trayAnimator != null) {
            trayAnimator.cancel();
        }
        trayAppear = 0.3f;
        ValueAnimator animator = ValueAnimator.ofFloat(0.3f, 1f);
        animator.setDuration(TRAY_APPEAR_DURATION_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(@NonNull ValueAnimator a) {
                trayAppear = (float) a.getAnimatedValue();
                invalidate();
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                if (trayAnimator == a) {
                    trayAppear = 1f;
                    trayAnimator = null;
                    invalidate();
                }
            }
        });
        trayAnimator = animator;
        animator.start();
    }

    /** Ekrandan ayrılınca süren animasyonları ve efektleri durdur (gereksiz çizim olmasın). */
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        ValueAnimator[] all = {clearAnimator, returnAnimator, trayAnimator, liftAnimator,
                settleAnimator};
        for (ValueAnimator a : all) {
            if (a != null) {
                a.cancel();
            }
        }
        effects.clear();
    }
}
