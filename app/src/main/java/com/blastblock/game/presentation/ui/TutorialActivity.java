package com.blastblock.game.presentation.ui;

import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.blastblock.game.BlastBlockApplication;
import com.blastblock.game.R;
import com.blastblock.game.di.AppContainer;
import com.blastblock.game.domain.engine.MovePreview;
import com.blastblock.game.domain.engine.MoveResult;
import com.blastblock.game.domain.engine.PlacementTarget;
import com.blastblock.game.domain.engine.TutorialPuzzle;
import com.blastblock.game.domain.repository.SettingsRepository;

/** A practice board that never touches the saved classic game. */
public final class TutorialActivity extends AppCompatActivity {
    private static final String STATE_COMPLETED = "completed";
    private TutorialPuzzle tutorial;
    private SettingsRepository settings;
    private GameSound sound;
    private GameView board;
    private TextView instruction;
    private Button action;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tutorial);
        SystemBarInsets.apply(this, findViewById(R.id.tutorial_content));
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        AppContainer container = ((BlastBlockApplication) getApplication()).getContainer();
        settings = container.getSettingsRepository();
        sound = new GameSound(this);
        tutorial = new TutorialPuzzle(container.newGameEngine());
        board = findViewById(R.id.tutorial_board);
        instruction = findViewById(R.id.tutorial_instruction);
        action = findViewById(R.id.tutorial_action);
        action.setOnClickListener(v -> {
            action.setEnabled(false);
            settings.markTutorialSeen();
            startActivity(new Intent(this, GameActivity.class));
            finish();
        });

        if (savedInstanceState != null && savedInstanceState.getBoolean(STATE_COMPLETED)) {
            tutorial.place(TutorialPuzzle.SLOT, TutorialPuzzle.TARGET_ROW, TutorialPuzzle.TARGET_COL);
            showCompleted();
        } else {
            board.setTutorialTarget(TutorialPuzzle.TARGET_ROW, TutorialPuzzle.TARGET_COL);
        }
        board.setGameState(tutorial.getState());
        board.setCallbacks(new GameView.Callbacks() {
            @Override
            public MovePreview onPreview(int slot, int row, int col) {
                return tutorial.preview(slot, row, col);
            }

            @Override
            public PlacementTarget onResolve(int slot, float row, float col, PlacementTarget previous) {
                return tutorial.resolve(slot, row, col);
            }

            @Override
            public void onDrop(int slot, int row, int col) {
                MoveResult move = tutorial.place(slot, row, col);
                if (!move.isPlaced()) return;
                board.setGameState(tutorial.getState());
                board.playMoveAnimation(move);
                sound.play(move);
                settings.markTutorialSeen();
                showCompleted();
            }

            @Override
            public void onPickUp() { }
        });
    }

    private void showCompleted() {
        board.setTutorialTarget(-1, -1);
        instruction.setText(R.string.tutorial_success);
        board.setContentDescription(getString(R.string.tutorial_success));
        action.setText(R.string.tutorial_start);
    }

    @Override
    protected void onResume() {
        super.onResume();
        sound.setEnabled(settings.isSoundEnabled());
    }

    @Override
    protected void onPause() {
        sound.setEnabled(false);
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean(STATE_COMPLETED, tutorial.isCompleted());
    }

    @Override
    protected void onDestroy() {
        sound.release();
        super.onDestroy();
    }
}
