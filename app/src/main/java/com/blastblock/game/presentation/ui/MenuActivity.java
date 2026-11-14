package com.blastblock.game.presentation.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.blastblock.game.BlastBlockApplication;
import com.blastblock.game.R;
import com.blastblock.game.domain.engine.DailyPuzzle;
import com.blastblock.game.presentation.state.MenuUiState;
import com.blastblock.game.presentation.viewmodel.MenuViewModel;

/**
 * Ana menü ekranı (uygulamanın açılış ekranı).
 *
 * <p>MVVM gereği bu sınıf karar vermez: {@link MenuViewModel}'in yayınladığı {@link MenuUiState}'i
 * gözler ve ekrana yansıtır (rekor, klasik oyun düğmesi ve ayarlar).
 * Düğmelere basılınca ya başka ekrana geçer ya da isteği ViewModel'e iletir.
 */
public final class MenuActivity extends AppCompatActivity {

    private MenuViewModel viewModel;
    private TextView highScoreText;
    private Button classicButton;
    private Button vibrationButton;
    private Button soundButton;
    private MenuUiState currentState;
    private boolean awaitingRefresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);
        SystemBarInsets.apply(this, findViewById(R.id.menu_content));

        highScoreText = findViewById(R.id.menu_high_score);
        classicButton = findViewById(R.id.menu_classic);
        vibrationButton = findViewById(R.id.menu_vibration);
        soundButton = findViewById(R.id.menu_sound);

        // ViewModel, uygulamanın bağımlılık kabındaki fabrika ile kurulur (bkz. AppViewModelFactory).
        BlastBlockApplication app = (BlastBlockApplication) getApplication();
        viewModel = new ViewModelProvider(this, app.getContainer().getViewModelFactory())
                .get(MenuViewModel.class);

        viewModel.getUiState().observe(this, this::render);

        classicButton.setOnClickListener(v -> openClassic());
        vibrationButton.setOnClickListener(v -> viewModel.toggleVibration());
        soundButton.setOnClickListener(v -> viewModel.toggleSound());
        findViewById(R.id.menu_daily).setOnClickListener(v -> startActivity(
                new Intent(this, GameActivity.class).putExtra(GameActivity.EXTRA_DAILY_DAY,
                        DailyPuzzle.dayAt(System.currentTimeMillis()))));

        Button historyButton = findViewById(R.id.menu_history);
        historyButton.setOnClickListener(
                v -> startActivity(new Intent(this, ScoresActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        awaitingRefresh = true;
        classicButton.setEnabled(false);
        // Oyundan dönünce rekor ve kayıt durumu değişmiş olabilir; her görünüşte yeniden oku.
        viewModel.refresh();
    }

    /** Durumu ekrana yansıtır. */
    private void render(MenuUiState state) {
        if (state == null) {
            return;
        }
        awaitingRefresh = false;
        currentState = state;
        classicButton.setEnabled(true);
        highScoreText.setText(getString(R.string.menu_high_score, state.getHighScore()));
        classicButton.setText(state.hasSavedGame() || state.hasSaveReadFailed()
                ? R.string.menu_continue : R.string.menu_new_game);
        vibrationButton.setText(state.isVibrationEnabled()
                ? R.string.menu_vibration_on : R.string.menu_vibration_off);
        soundButton.setText(state.isSoundEnabled() ? R.string.menu_sound_on : R.string.menu_sound_off);
    }

    private void openClassic() {
        MenuUiState state = currentState;
        if (awaitingRefresh || state == null) return;
        if (state.hasSaveReadFailed()) {
            Toast.makeText(this, R.string.menu_save_check_failed, Toast.LENGTH_LONG).show();
            viewModel.refresh();
            return;
        }
        classicButton.setEnabled(false);
        Class<?> destination = state.hasSavedGame() || state.hasSeenTutorial()
                ? GameActivity.class : TutorialActivity.class;
        startActivity(new Intent(this, destination));
    }
}
