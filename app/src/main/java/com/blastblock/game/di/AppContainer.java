package com.blastblock.game.di;

import android.content.Context;

import com.blastblock.game.data.FileGameRepository;
import com.blastblock.game.data.SqliteScoreHistoryRepository;
import com.blastblock.game.data.PrefsHighScoreRepository;
import com.blastblock.game.data.PrefsSettingsRepository;
import com.blastblock.game.domain.engine.GameEngine;
import com.blastblock.game.domain.repository.GameRepository;
import com.blastblock.game.domain.repository.HighScoreRepository;
import com.blastblock.game.domain.repository.ScoreHistoryRepository;
import com.blastblock.game.domain.repository.SettingsRepository;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Bağımlılık kabı (elle yapılan "dependency injection").
 *
 * <p><b>Katmanlar burada birbirine bağlanır.</b> Domain katmanı yalnızca arayüzleri
 * ({@link GameRepository}, {@link HighScoreRepository}, {@link SettingsRepository}) tanır; bu
 * arayüzlerin dosya ve SharedPreferences ile çalışan gerçek hâlleri data katmanındadır. Sunum
 * katmanındaki ViewModel'ler de yalnızca arayüzleri ister. "Hangi arayüzün yerine hangi gerçek sınıf
 * kullanılacak?" kararı uygulamada YALNIZCA bu sınıfta verilir. Böylece:
 * <ul>
 *   <li>domain ve ViewModel'ler Android'e/diske bağımlı olmaz, testlerde sahte sınıflar verilebilir;</li>
 *   <li>kayıt yöntemi değişirse (ör. veritabanı) yalnızca burası değişir.</li>
 * </ul>
 *
 * <p>Repository'ler ve iş parçacığı uygulama boyunca TEK örnektir: iki ekran aynı dosyaya aynı anda
 * farklı nesnelerle yazmaya çalışmaz. {@link GameEngine} ise her oyun ekranı için yeni oluşturulur.
 */
public final class AppContainer {

    private final Context appContext;
    private final GameRepository gameRepository;
    private final HighScoreRepository highScoreRepository;
    private final SettingsRepository settingsRepository;
    /** Completed classic and daily games. */
    private final ScoreHistoryRepository scoreHistoryRepository;

    /**
     * Disk işleri için tek iş parçacıklı sıralı yürütücü. Tek iş parçacığı olduğu için kayıtlar
     * verildikleri sırayla yazılır; eski bir oyun durumu yeni durumun üstüne yazılamaz.
     */
    private final ExecutorService ioExecutor;

    public AppContainer(Context context) {
        appContext = context.getApplicationContext();
        gameRepository = new FileGameRepository(appContext);
        highScoreRepository = new PrefsHighScoreRepository(appContext);
        settingsRepository = new PrefsSettingsRepository(appContext);
        scoreHistoryRepository = new SqliteScoreHistoryRepository(appContext);
        ioExecutor = Executors.newSingleThreadExecutor();
    }

    public GameRepository getGameRepository() {
        return gameRepository;
    }

    public HighScoreRepository getHighScoreRepository() {
        return highScoreRepository;
    }

    public SettingsRepository getSettingsRepository() {
        return settingsRepository;
    }

    public ScoreHistoryRepository getScoreHistoryRepository() {
        return scoreHistoryRepository;
    }

    public ExecutorService getIoExecutor() {
        return ioExecutor;
    }

    /** Yeni bir oyun motoru: kurallar domain katmanında, rastgelelik java.util.Random ile. */
    public GameEngine newGameEngine() {
        return new GameEngine(new Random());
    }

    /** Ekranların ViewModel'lerini bu kaptaki nesnelerle kuran fabrika. */
    public AppViewModelFactory getViewModelFactory() {
        return new AppViewModelFactory(this);
    }

    public AppViewModelFactory getDailyViewModelFactory(long day) {
        return new AppViewModelFactory(this, day, PrefsHighScoreRepository.daily(appContext, day));
    }
}
