package com.blastblock.game.presentation.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.blastblock.game.domain.repository.GameLoadResult;
import com.blastblock.game.domain.repository.GameRepository;
import com.blastblock.game.domain.repository.HighScoreRepository;
import com.blastblock.game.domain.repository.SettingsRepository;
import com.blastblock.game.presentation.state.MenuUiState;

import java.io.IOException;
import java.util.concurrent.Executor;

/**
 * Ana menünün ViewModel'i: rekoru, devam edilebilecek bir kayıt olup olmadığını ve titreşim
 * ayarını okuyup {@link MenuUiState} olarak yayınlar.
 *
 * <p>Okumalar disk erişimi içerdiği için {@code ioExecutor} üzerinde yapılır; sonuç
 * {@code mainExecutor} ile ana iş parçacığına taşınıp {@code setValue} ile yayınlanır. Activity referansı tutulmaz.
 */
public final class MenuViewModel extends ViewModel {

    private final GameRepository gameRepository;
    private final HighScoreRepository highScoreRepository;
    private final SettingsRepository settingsRepository;
    private final Executor ioExecutor;
    private final Executor mainExecutor;

    private final MutableLiveData<MenuUiState> uiState = new MutableLiveData<>();

    public MenuViewModel(GameRepository gameRepository,
                         HighScoreRepository highScoreRepository,
                         SettingsRepository settingsRepository,
                         Executor ioExecutor,
                         Executor mainExecutor) {
        if (gameRepository == null || highScoreRepository == null
                || settingsRepository == null || ioExecutor == null || mainExecutor == null) {
            throw new IllegalArgumentException("Bağımlılıklar null olamaz");
        }
        this.gameRepository = gameRepository;
        this.highScoreRepository = highScoreRepository;
        this.settingsRepository = settingsRepository;
        this.ioExecutor = ioExecutor;
        this.mainExecutor = mainExecutor;
    }

    public LiveData<MenuUiState> getUiState() {
        return uiState;
    }

    /** Menü durumunu yeniden okur; menü her göründüğünde (onResume) çağrılmalı. */
    public void refresh() {
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                publish();
            }
        });
    }

    /** Titreşim ayarını tersine çevirir, kaydeder ve yeni durumu yayınlar. */
    public void toggleVibration() {
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                settingsRepository.setVibrationEnabled(!settingsRepository.isVibrationEnabled());
                publish();
            }
        });
    }

    public void toggleSound() {
        ioExecutor.execute(() -> {
            settingsRepository.setSoundEnabled(!settingsRepository.isSoundEnabled());
            publish();
        });
    }

    /** Arka plan iş parçacığında çalışır. */
    private void publish() {
        boolean hasSaved = false;
        boolean saveReadFailed = false;
        try {
            hasSaved = gameRepository.load().getStatus() == GameLoadResult.Status.ACTIVE;
        } catch (IOException e) {
            saveReadFailed = true;
        }
        final MenuUiState state = new MenuUiState(
                highScoreRepository.getHighScore(),
                hasSaved,
                settingsRepository.isVibrationEnabled(),
                settingsRepository.isSoundEnabled(),
                settingsRepository.hasSeenTutorial(),
                saveReadFailed);
        // Sonuç ana iş parçacığına taşınır ve orada setValue ile yayınlanır.
        mainExecutor.execute(new Runnable() {
            @Override
            public void run() {
                uiState.setValue(state);
            }
        });
    }
}
