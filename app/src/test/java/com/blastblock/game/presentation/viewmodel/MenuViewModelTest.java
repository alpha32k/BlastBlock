package com.blastblock.game.presentation.viewmodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.blastblock.game.domain.engine.GameEngine;
import com.blastblock.game.domain.repository.GameLoadResult;
import com.blastblock.game.presentation.state.MenuUiState;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Random;

/** MenuViewModel testleri: rekor, kayıt var mı ve titreşim ayarı doğru yayınlanıyor mu? */
public class MenuViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantRule = new InstantTaskExecutorRule();

    private Fakes.FakeGameRepository gameRepo;
    private Fakes.FakeHighScoreRepository highRepo;
    private Fakes.FakeSettingsRepository settings;
    private MenuViewModel vm;

    @Before
    public void setUp() {
        gameRepo = new Fakes.FakeGameRepository();
        highRepo = new Fakes.FakeHighScoreRepository();
        settings = new Fakes.FakeSettingsRepository();
        vm = new MenuViewModel(gameRepo, highRepo, settings, Runnable::run, Runnable::run);
    }

    @Test
    public void refresh_withoutSave() {
        highRepo.highScore = 777;
        vm.refresh();
        MenuUiState s = vm.getUiState().getValue();
        assertEquals(777, s.getHighScore());
        assertFalse(s.hasSavedGame());
        assertTrue(s.isVibrationEnabled());
    }

    @Test
    public void refresh_withActiveSave() {
        gameRepo.loadResult = GameLoadResult.active(new GameEngine(new Random(42)).newGame());
        vm.refresh();
        assertTrue(vm.getUiState().getValue().hasSavedGame());
        assertFalse(vm.getUiState().getValue().hasSaveReadFailed());
    }

    @Test
    public void refresh_invalidOrUnreadableSaveCountsAsNoSave() {
        gameRepo.loadResult = GameLoadResult.invalid();
        vm.refresh();
        assertFalse(vm.getUiState().getValue().hasSavedGame());

        gameRepo.failLoad = true;
        vm.refresh();
        assertFalse(vm.getUiState().getValue().hasSavedGame());
        assertTrue(vm.getUiState().getValue().hasSaveReadFailed());
    }

    @Test
    public void toggleVibration_flipsSettingAndRepublishes() {
        vm.refresh();
        vm.toggleVibration();
        assertFalse(settings.vibration);
        assertFalse(vm.getUiState().getValue().isVibrationEnabled());

        vm.toggleVibration();
        assertTrue(settings.vibration);
        assertTrue(vm.getUiState().getValue().isVibrationEnabled());
    }

    @Test
    public void soundToggleIsRetainedOnRefresh() {
        vm.refresh();
        assertTrue(vm.getUiState().getValue().isSoundEnabled());
        vm.toggleSound();
        vm.refresh();
        assertFalse(vm.getUiState().getValue().isSoundEnabled());
    }
}
