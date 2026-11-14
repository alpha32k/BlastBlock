package com.blastblock.game.presentation.ui;

import android.animation.ValueAnimator;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.blastblock.game.BlastBlockApplication;
import com.blastblock.game.R;
import com.blastblock.game.di.AppContainer;
import com.blastblock.game.domain.engine.MovePreview;
import com.blastblock.game.domain.engine.MoveResult;
import com.blastblock.game.domain.engine.PlacementTarget;
import com.blastblock.game.domain.model.Board;
import com.blastblock.game.domain.model.GameState;
import com.blastblock.game.domain.repository.SettingsRepository;
import com.blastblock.game.presentation.state.Event;
import com.blastblock.game.presentation.state.GameUiState;
import com.blastblock.game.presentation.state.UiEvent;
import com.blastblock.game.presentation.viewmodel.GameViewModel;

import java.text.NumberFormat;
import java.text.DateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Oyun ekranı.
 *
 * <p>Görevleri: {@link GameViewModel}'in durumunu (skor, rekor, seri, tahta) ekrana yansıtmak,
 * tek seferlik olaylara (animasyon, titreşim, oyun bitti, hata mesajı) tepki vermek ve
 * {@link GameView}'den gelen sürükle-bırak isteklerini ViewModel'e iletmek. Oyun kurallarını bilmez.
 *
 * <p>Ekran döndürme vb. durumlarda Activity yeniden oluşturulur ama ViewModel ve içindeki oyun
 * korunur; {@code open()} ikinci kez çağrıldığında yeni oyun BAŞLATMAZ.
 */
public final class GameActivity extends AppCompatActivity {

    public static final String EXTRA_DAILY_DAY = "dailyDay";

    /** Silme animasyonu (~350 ms) bitsin diye "Oyun bitti" katmanı bu kadar gecikmeyle açılır. */
    private static final long GAME_OVER_DELAY_MS = 600;
    /** Yeniden oluşturmada "Tekrar Oyna", ViewModel'in arka plan işi bitsin diye bu kadar sonra açılır. */
    private static final long PLAY_AGAIN_ENABLE_DELAY_MS = 300;
    /** Skor sayacının sayarak artma süresi. */
    private static final long SCORE_COUNT_MS = 300;

    /** Parça alınınca çok kısa "tık" titreşimi (yerleştirmeden de kısa). */
    private static final long PICKUP_VIBRATE_MS = 8;
    /** Skor sayacının "pop" ölçeği ve yarı süresi. */
    private static final float SCORE_POP_SCALE = 1.15f;
    private static final long SCORE_POP_HALF_MS = 110;

    private static final String STATE_NEW_RECORD = "newRecord";
    private static final String STATE_TOP_RANK = "topRank";
    private static final String STATE_RECORD_BASELINE = "recordBaseline";
    private static final String STATE_RECORD_FX_SHOWN = "recordFxShown";
    private static final String STATE_LAST_FINAL_SCORE = "lastFinalScore";

    private GameViewModel viewModel;
    private SettingsRepository settingsRepository;
    private AppContainer container;
    private HapticFeedback haptics;
    private GameSound sound;
    private boolean daily;

    private GameView gameView;
    private TextView scoreText;
    private TextView highScoreText;
    private TextView streakText;
    private View progress;
    private View gameOverOverlay;
    private TextView gameOverScore;
    private TextView gameOverHighScore;
    private View gameOverNewRecord;
    private TextView gameOverTopRank;
    private Button playAgainButton;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final NumberFormat numberFormat = NumberFormat.getIntegerInstance();
    private final Runnable enablePlayAgain = () -> playAgainButton.setEnabled(true);
    private Runnable pendingGameOver;
    private ValueAnimator scoreAnimator;

    /** Skor sayacında şu an yazan değer (sayarak artma animasyonu için). */
    private long displayedScore = -1;
    /** Bu Activity örneğinde ilk oyun durumu henüz çizilmedi mi? (Yeniden oluşturmayı anlamak için.) */
    private boolean firstGameRender = true;
    /** Son "Oyun bitti" katmanında "Yeni rekor!" gösteriliyor mu? (Döndürmede korunur.) */
    private boolean newRecordShown;
    /** Son "Oyun bitti" katmanında gösterilen "En iyi 10" sırası (0 = yok; döndürmede korunur). */
    private int topRankShown;

    /**
     * Oyun içi "Yeni rekor!" parlaması için: bu oyun başladığında geçerli olan rekor. -1 = henüz
     * alınmadı (ilk çizimde ya da "Tekrar Oyna"dan sonra durumdan alınır).
     */
    private long recordBaseline = -1;
    /** Bu oyunda "Yeni rekor!" parlaması gösterildi mi? (Oyun başına bir kez; döndürmede korunur.) */
    private boolean recordFxShown;
    /** Son biten oyunun skoru: rekor henüz güncellenmemişse yeni oyunun eşiği için kullanılır. */
    private long lastFinalScore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        setContentView(R.layout.activity_game);
        SystemBarInsets.apply(this, findViewById(R.id.game_content));
        SystemBarInsets.apply(this, findViewById(R.id.game_over_overlay));

        if (savedInstanceState != null) {
            newRecordShown = savedInstanceState.getBoolean(STATE_NEW_RECORD, false);
            topRankShown = savedInstanceState.getInt(STATE_TOP_RANK, 0);
            recordBaseline = savedInstanceState.getLong(STATE_RECORD_BASELINE, -1);
            recordFxShown = savedInstanceState.getBoolean(STATE_RECORD_FX_SHOWN, false);
            lastFinalScore = savedInstanceState.getLong(STATE_LAST_FINAL_SCORE, 0);
        }

        gameView = findViewById(R.id.game_view);
        scoreText = findViewById(R.id.game_score);
        highScoreText = findViewById(R.id.game_high_score);
        streakText = findViewById(R.id.game_streak);
        progress = findViewById(R.id.game_progress);
        gameOverOverlay = findViewById(R.id.game_over_overlay);
        gameOverScore = findViewById(R.id.game_over_score);
        gameOverHighScore = findViewById(R.id.game_over_high_score);
        gameOverNewRecord = findViewById(R.id.game_over_new_record);
        gameOverTopRank = findViewById(R.id.game_over_top_rank);
        playAgainButton = findViewById(R.id.game_over_play_again);

        container = ((BlastBlockApplication) getApplication()).getContainer();
        settingsRepository = container.getSettingsRepository();
        haptics = new HapticFeedback(this);
        sound = new GameSound(this);

        daily = getIntent().hasExtra(EXTRA_DAILY_DAY);
        long day = getIntent().getLongExtra(EXTRA_DAILY_DAY, 0);
        if (daily) {
            DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(this);
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            TextView title = findViewById(R.id.game_daily_title);
            title.setText(getString(R.string.daily_game_title,
                    dateFormat.format(new Date(day * 86_400_000L))));
            title.setVisibility(View.VISIBLE);
            ((TextView) findViewById(R.id.game_high_score_label)).setText(R.string.daily_best_label);
        }
        viewModel = new ViewModelProvider(this, daily
                ? container.getDailyViewModelFactory(day) : container.getViewModelFactory())
                .get(GameViewModel.class);

        // GameView, ViewModel'i tanımaz; yalnızca bu geri çağrıları bilir.
        gameView.setCallbacks(new GameView.Callbacks() {
            @Override
            public MovePreview onPreview(int slot, int row, int col) {
                return viewModel.preview(slot, row, col);
            }

            @Override
            public PlacementTarget onResolve(int slot, float fracRow, float fracCol,
                                             PlacementTarget previous) {
                return viewModel.resolveTarget(slot, fracRow, fracCol, previous);
            }

            @Override
            public void onDrop(int slot, int row, int col) {
                viewModel.place(slot, row, col);
            }

            @Override
            public void onPickUp() {
                haptics.vibrate(PICKUP_VIBRATE_MS);
            }
        });

        findViewById(R.id.game_menu_button).setOnClickListener(v -> finish());
        playAgainButton.setOnClickListener(v -> {
            hideGameOverOverlay();
            recordBaseline = -1; // yeni oyunun rekor eşiği ilk çizimde yeniden alınır
            recordFxShown = false;
            viewModel.newGame();
        });
        findViewById(R.id.game_over_menu).setOnClickListener(v -> finish());

        // The system back gesture returns to the menu just like the Menu button.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });

        viewModel.getUiState().observe(this, this::render);
        viewModel.getEvents().observe(this, this::onEvent);

        viewModel.open(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        sound.setEnabled(settingsRepository.isSoundEnabled());
        // Titreşim ayarı menüde değişmiş olabilir; SharedPreferences okumasını arka planda yap.
        container.getIoExecutor().execute(
                () -> haptics.setEnabled(settingsRepository.isVibrationEnabled()));
    }

    @Override
    protected void onPause() {
        sound.setEnabled(false);
        super.onPause();
        // Uygulama arka plana giderse (telefon çaldı, ana ekran tuşu) oyun kaybolmasın.
        viewModel.saveNow();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_NEW_RECORD, newRecordShown);
        outState.putInt(STATE_TOP_RANK, topRankShown);
        outState.putLong(STATE_RECORD_BASELINE, recordBaseline);
        outState.putBoolean(STATE_RECORD_FX_SHOWN, recordFxShown);
        outState.putLong(STATE_LAST_FINAL_SCORE, lastFinalScore);
    }

    @Override
    protected void onDestroy() {
        sound.release();
        handler.removeCallbacksAndMessages(null);
        if (scoreAnimator != null) {
            scoreAnimator.cancel();
        }
        scoreText.animate().cancel();
        super.onDestroy();
    }

    // ================================================================== durum

    /** ViewModel'den gelen durumu ekrana yansıtır. */
    private void render(GameUiState state) {
        if (state == null || state.isLoading()) {
            progress.setVisibility(View.VISIBLE);
            return;
        }
        progress.setVisibility(View.GONE);
        GameState game = state.getGame();
        if (game == null) {
            return; // Kayıt okunamadı: LOAD_FAILED olayı mesajı gösterip ekranı kapatacak.
        }
        gameView.setGameState(game);
        if (recordBaseline < 0 && !game.isGameOver()) {
            // Bu oyunun rekor eşiği: oyun başındaki rekor. Kayıtlı oyuna devam ediliyorsa ve skor
            // eşiği zaten geçmişse parlama daha önce gösterilmiş sayılır (tekrar gösterilmez).
            recordBaseline = Math.max(state.getHighScore(), lastFinalScore);
            // Ekrandaki rekor, ViewModel'de max(kayıtlı rekor, mevcut skor) olarak gelir. Devam edilen
            // oyunun skoru bu değere eşitse rekoru bu oyun tutuyor demektir: bildirim zaten gösterilmiş.
            recordFxShown = game.getScore() > 0 && game.getScore() >= recordBaseline;
        }
        setScore(game.getScore(), firstGameRender);
        highScoreText.setText(numberFormat.format(state.getHighScore()));
        if (game.getStreak() > 0) {
            streakText.setText(getString(R.string.game_streak, game.getStreak()));
            streakText.setVisibility(View.VISIBLE);
        } else {
            streakText.setVisibility(View.INVISIBLE);
        }

        if (game.isGameOver()) {
            // Normalde katman GAME_OVER olayıyla açılır. Ama Activity yeniden oluşturulduysa olay
            // zaten işlenmiş olabilir ve tekrar gelmez; bu yüzden ilk çizimde durumdan anlıyoruz.
            if (firstGameRender && gameOverOverlay.getVisibility() != View.VISIBLE) {
                showGameOverOverlay(game.getScore(), state.getHighScore(), newRecordShown,
                        topRankShown, true);
            }
        } else if (gameOverOverlay.getVisibility() == View.VISIBLE) {
            hideGameOverOverlay();
        }
        firstGameRender = false;
    }

    /** Skoru yazar; anında değilse kısa bir animasyonla sayarak artırır. */
    private void setScore(long score, boolean immediate) {
        if (scoreAnimator != null) {
            scoreAnimator.cancel();
            scoreAnimator = null;
        }
        if (immediate || displayedScore < 0 || score <= displayedScore) {
            displayedScore = score;
            scoreText.setText(numberFormat.format(score));
            return;
        }
        popScore();
        final long from = displayedScore;
        final long to = score;
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(SCORE_COUNT_MS);
        animator.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            displayedScore = from + Math.round((to - from) * f);
            scoreText.setText(numberFormat.format(displayedScore));
        });
        scoreAnimator = animator;
        animator.start();
    }

    /** Skor sayacında kısa ölçek "pop"u: 1.0 → 1.15 → 1.0. */
    private void popScore() {
        scoreText.animate().cancel();
        scoreText.setScaleX(1f);
        scoreText.setScaleY(1f);
        scoreText.animate()
                .scaleX(SCORE_POP_SCALE)
                .scaleY(SCORE_POP_SCALE)
                .setDuration(SCORE_POP_HALF_MS)
                .withEndAction(() -> scoreText.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(SCORE_POP_HALF_MS))
                .start();
    }

    // ================================================================== olaylar

    /** Tek seferlik olaylar: getContentIfNotHandled sayesinde her olay yalnızca bir kez işlenir. */
    private void onEvent(Event<UiEvent> wrapper) {
        if (wrapper == null) {
            return;
        }
        UiEvent event = wrapper.getContentIfNotHandled();
        if (event == null) {
            return;
        }
        switch (event.getType()) {
            case MOVE_COMMITTED:
                onMoveCommitted(event.getMoveResult());
                break;
            case GAME_OVER:
                scheduleGameOver(event);
                break;
            case INVALID_SAVE:
                Toast.makeText(this, R.string.error_invalid_save, Toast.LENGTH_LONG).show();
                break;
            case SAVE_FAILED:
                Toast.makeText(this, R.string.error_save_failed, Toast.LENGTH_SHORT).show();
                break;
            case LOAD_FAILED:
                // Kayıt korunuyor: burada kesinlikle yeni oyun başlatılmaz, yalnızca menüye dönülür.
                Toast.makeText(this, R.string.error_load_failed, Toast.LENGTH_LONG).show();
                finish();
                break;
            default:
                break;
        }
    }

    /**
     * Geçerli hamle: silme animasyonu ve efektler, kombo/övgü/seri/puan yazıları ve titreşim.
     * Hangi yazının gösterileceğine burada karar verilir (metinler strings.xml'den); GameView
     * yalnızca çizer.
     */
    private void onMoveCommitted(MoveResult result) {
        if (result == null) {
            return;
        }
        gameView.playMoveAnimation(result);
        sound.play(result);
        // Her yerleştirmede küçük "+puan", bırakılan parçanın üstünden yükselir.
        if (result.getScoreDelta() > 0) {
            gameView.showPointsGained(getString(R.string.game_points_gained, result.getScoreDelta()));
        }
        int lines = result.getLinesCleared();
        if (lines >= 2) {
            String combo = getString(R.string.fx_combo_lines, lines);
            if (lines >= 4) {
                gameView.showComboPop(getString(R.string.fx_praise_amazing), combo);
            } else if (lines == 3) {
                gameView.showComboPop(getString(R.string.fx_praise_great), combo);
            } else {
                gameView.showComboPop(combo, null);
            }
        }
        if (lines > 0 && result.getMultiplier() > 1) {
            gameView.showStreakBadge(getString(R.string.fx_streak_badge, result.getMultiplier()));
        }
        if (lines > 0 && isBoardEmpty(result.getState().getBoard())) {
            gameView.showBoardClear(getString(R.string.fx_board_clear));
        }
        // Oyun sırasında rekor ilk kez geçilince bir kez "Yeni rekor!" (hiç rekor yokken değil).
        long score = result.getState().getScore();
        if (!recordFxShown && recordBaseline > 0 && score > recordBaseline) {
            recordFxShown = true;
            gameView.showNewRecord(getString(R.string.fx_new_record));
        }
        haptics.vibrate(lines > 0 ? HapticFeedback.CLEAR_MS : HapticFeedback.PLACE_MS);
    }

    /** Tahtada hiç dolu hücre kalmadı mı? ("Tahta Temiz!" yalnız görsel bir kutlamadır.) */
    private static boolean isBoardEmpty(Board board) {
        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                if (!board.isEmpty(row, col)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** "Oyun bitti" katmanını silme animasyonu bittikten sonra açar. */
    private void scheduleGameOver(UiEvent event) {
        final MoveResult result = event.getMoveResult();
        // Yeni rekor bilgisi olay işlendiği AN kaydedilir: katman açılmadan (600 ms içinde) Activity
        // yeniden oluşturulursa onSaveInstanceState ile korunur ve "Yeni rekor!" kaybolmaz.
        newRecordShown = event.isNewHighScore();
        topRankShown = Math.max(0, event.getTopRank());
        final boolean newRecord = newRecordShown;
        final int topRank = topRankShown;
        if (pendingGameOver != null) {
            handler.removeCallbacks(pendingGameOver);
        }
        pendingGameOver = () -> {
            pendingGameOver = null;
            sound.playLoss();
            GameUiState state = viewModel.getUiState().getValue();
            long score = result != null ? result.getState().getScore()
                    : (state != null && state.getGame() != null ? state.getGame().getScore() : 0);
            long high = state != null ? state.getHighScore() : score;
            showGameOverOverlay(score, Math.max(high, score), newRecord, topRank, false);
        };
        handler.postDelayed(pendingGameOver, GAME_OVER_DELAY_MS);
    }

    private void showGameOverOverlay(long score, long highScore, boolean newRecord, int topRank,
                                     boolean delayPlayAgain) {
        newRecordShown = newRecord;
        topRankShown = topRank;
        lastFinalScore = score;
        if (topRank > 0) {
            gameOverTopRank.setText(getString(R.string.game_over_top_rank, topRank));
            gameOverTopRank.setVisibility(View.VISIBLE);
        } else {
            gameOverTopRank.setVisibility(View.GONE);
        }
        gameOverScore.setText(getString(R.string.game_over_score, score));
        gameOverHighScore.setText(getString(daily ? R.string.daily_best_score
                : R.string.game_over_high_score, highScore));
        gameOverNewRecord.setVisibility(newRecord ? View.VISIBLE : View.GONE);
        handler.removeCallbacks(enablePlayAgain);
        if (delayPlayAgain) {
            // Yeniden oluşturmada ViewModel'in arka plandaki oyun-sonu işi (rekor, kayıt silme)
            // hâlâ sürüyor olabilir; "Tekrar Oyna" kısa bir süre sonra etkinleşir.
            playAgainButton.setEnabled(false);
            handler.postDelayed(enablePlayAgain, PLAY_AGAIN_ENABLE_DELAY_MS);
        } else {
            // GAME_OVER olayı, ViewModel arka plan işini bitirdikten SONRA gelir; hemen etkin olabilir.
            playAgainButton.setEnabled(true);
        }
        gameOverOverlay.setVisibility(View.VISIBLE);
    }

    private void hideGameOverOverlay() {
        if (pendingGameOver != null) {
            handler.removeCallbacks(pendingGameOver);
            pendingGameOver = null;
        }
        newRecordShown = false;
        topRankShown = 0;
        gameOverOverlay.setVisibility(View.GONE);
    }
}
