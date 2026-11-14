package com.blastblock.game.di;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.blastblock.game.domain.engine.GameEngine;
import com.blastblock.game.domain.engine.DailyPuzzle;
import com.blastblock.game.domain.repository.HighScoreRepository;
import com.blastblock.game.domain.usecase.GameSession;
import com.blastblock.game.presentation.viewmodel.GameViewModel;
import com.blastblock.game.presentation.viewmodel.MenuViewModel;
import com.blastblock.game.presentation.viewmodel.ScoresViewModel;

import java.util.concurrent.Executor;

/**
 * ViewModel fabrikası.
 *
 * <p>Android, ViewModel'leri kendisi saklar ve ekran döndürülünce aynı nesneyi geri verir. Ama
 * ViewModel'lerimizin kurucuları parametre (repository, motor, iş parçacığı) istediği için Android
 * onları kendi başına oluşturamaz. Bu fabrika, istenen sınıfa göre doğru bağımlılıkları
 * {@link AppContainer}'dan alıp ViewModel'i kurar. {@code create} yalnızca ViewModel ilk kez
 * gerektiğinde çağrılır; döndürmeden sonra eski ViewModel kullanılır.
 */
public final class AppViewModelFactory implements ViewModelProvider.Factory {

    /**
     * Arka plan işlerinin sonuçlarını ana (UI) iş parçacığına taşıyan executor. ViewModel'ler
     * sonuçları bununla ana iş parçacığında, teslim anında oturum kontrolü yaparak yayınlar.
     */
    private static final Executor MAIN_EXECUTOR = new Handler(Looper.getMainLooper())::post;

    private final AppContainer container;
    private final long dailyDay;
    private final HighScoreRepository dailyScores;

    public AppViewModelFactory(AppContainer container) {
        this(container, 0, null);
    }

    AppViewModelFactory(AppContainer container, long dailyDay, HighScoreRepository dailyScores) {
        this.container = container;
        this.dailyDay = dailyDay;
        this.dailyScores = dailyScores;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(GameViewModel.class)) {
            GameEngine engine = dailyScores == null ? container.newGameEngine()
                    : new DailyPuzzle(dailyDay).newEngine();
            GameSession session = dailyScores != null ? GameSession.daily(engine, dailyScores,
                    container.getScoreHistoryRepository(), System::currentTimeMillis)
                    : new GameSession(
                    engine,
                    container.getGameRepository(),
                    container.getHighScoreRepository(),
                    container.getScoreHistoryRepository(),
                    System::currentTimeMillis);
            return (T) new GameViewModel(engine, session, container.getIoExecutor(), MAIN_EXECUTOR);
        }
        if (modelClass.isAssignableFrom(ScoresViewModel.class)) {
            return (T) new ScoresViewModel(
                    container.getScoreHistoryRepository(),
                    container.getIoExecutor(),
                    MAIN_EXECUTOR);
        }
        if (modelClass.isAssignableFrom(MenuViewModel.class)) {
            return (T) new MenuViewModel(
                    container.getGameRepository(),
                    container.getHighScoreRepository(),
                    container.getSettingsRepository(),
                    container.getIoExecutor(),
                    MAIN_EXECUTOR);
        }
        throw new IllegalArgumentException("Bilinmeyen ViewModel sınıfı: " + modelClass.getName());
    }
}
