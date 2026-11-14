package com.blastblock.game.presentation.ui;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import com.blastblock.game.R;
import com.blastblock.game.domain.engine.MoveResult;

import java.util.HashSet;
import java.util.Set;

/** Owns the short game effects for the lifetime of one screen. */
final class GameSound {
    private final SoundPool pool;
    private final Set<Integer> loaded = new HashSet<>();
    private final int placement;
    private final int clear;
    private final int combo;
    private final int loss;
    private boolean enabled;

    GameSound(Context context) {
        pool = new SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        pool.setOnLoadCompleteListener((soundPool, id, status) -> {
            if (status == 0) loaded.add(id);
        });
        placement = pool.load(context, R.raw.block_placed, 1);
        clear = pool.load(context, R.raw.line_clear, 1);
        combo = pool.load(context, R.raw.line_combo, 1);
        loss = pool.load(context, R.raw.game_lost, 1);
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) pool.autoPause();
    }

    void play(MoveResult move) {
        int lines = move.getLinesCleared();
        int id = lines == 0 ? placement : lines > 1 || move.getMultiplier() > 1 ? combo : clear;
        playSample(id);
    }

    void playLoss() {
        playSample(loss);
    }

    private void playSample(int id) {
        if (enabled && loaded.contains(id)) pool.play(id, 0.65f, 0.65f, 1, 0, 1f);
    }

    void release() {
        pool.setOnLoadCompleteListener(null);
        pool.release();
    }
}
