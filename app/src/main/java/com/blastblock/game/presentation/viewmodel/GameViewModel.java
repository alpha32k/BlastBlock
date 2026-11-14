package com.blastblock.game.presentation.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.blastblock.game.domain.engine.GameEngine;
import com.blastblock.game.domain.engine.MovePreview;
import com.blastblock.game.domain.engine.MoveResult;
import com.blastblock.game.domain.engine.PlacementResolver;
import com.blastblock.game.domain.engine.PlacementTarget;
import com.blastblock.game.domain.model.GameState;
import com.blastblock.game.domain.model.Piece;
import com.blastblock.game.domain.usecase.GameSession;
import com.blastblock.game.presentation.state.Event;
import com.blastblock.game.presentation.state.GameUiState;
import com.blastblock.game.presentation.state.UiEvent;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Oyun ekranının ViewModel'i.
 *
 * <p><b>MVVM'de ViewModel'in rolü:</b> View (GameActivity ve GameView) yalnızca çizer ve dokunuşları
 * iletir; oyun kurallarını {@link GameEngine} (domain katmanı) uygular; kayıt işlerini repository'ler
 * yapar. ViewModel bu ikisinin arasında durur: kullanıcının isteğini motora iletir, sonucu ekranın
 * anlayacağı bir duruma ({@link GameUiState}) çevirir ve kaydı tetikler. ViewModel hiçbir Activity,
 * View ya da Context referansı tutmaz; bu sayede ekran döndürülüp Activity yeniden oluşturulduğunda
 * ViewModel (ve içindeki oyun) hayatta kalır ve bellek sızıntısı olmaz. Bağımlılıklar kurucudan
 * verilir (ViewModelFactory ile), bu da sınıfı sahte repository'lerle test edilebilir yapar.
 *
 * <p><b>Neden LiveData?</b> LiveData yaşam döngüsünü bilen gözlemlenebilir bir değerdir: yalnızca
 * ekran görünürken gözlemciye haber verir, Activity yok edilince gözlemciyi kendisi bırakır ve yeni
 * bağlanan gözlemciye son durumu hemen verir. Bu sınıf LiveData'yı YALNIZCA ana iş parçacığında
 * {@code setValue} ile günceller.
 *
 * <p><b>Neden iki executor?</b> Disk okuma/yazma yavaş olabilir; ana (UI) iş parçacığında yapılırsa
 * oyun takılır, uzun sürerse Android "Uygulama yanıt vermiyor" (ANR) uyarısı verir. Bu yüzden kayıt
 * işleri {@code ioExecutor}'a verilir. Üretimde bu executor TEK iş parçacıklı ve sıralıdır; böylece
 * kayıtlar verildiği sırayla yazılır. Arka plan işinin SONUCU ise {@code mainExecutor} ile ana iş
 * parçacığına geri taşınır ve orada, teslim anında "bu sonuç hâlâ geçerli oturuma mı ait?" diye
 * kontrol edilerek yayınlanır. ({@code postValue} kullanılsaydı: art arda gelen değerler birleşip
 * kaybolabilir ve oyuncu bu arada "Tekrar Oyna"ya bastıysa eski oyunun sonucu yeni oyunun üstüne
 * yazılabilirdi.) Hamlenin kendisi anında ana iş parçacığında uygulanır; oyuncu diski beklemez.
 *
 * <p><b>Tek seferlik olaylar:</b> Animasyon, titreşim, "oyun bitti" penceresi gibi şeyler durum değil
 * olaydır. {@link Event} sarmalayıcısı sayesinde ekran döndürüldükten sonra yeniden tetiklenmezler.
 *
 * <p>Tüm public metotlar ana iş parçacığından çağrılmalıdır.
 */
public final class GameViewModel extends ViewModel {

    private final GameEngine engine;
    private final GameSession gameSession;
    private final Executor ioExecutor;
    private final Executor mainExecutor;

    private final MutableLiveData<GameUiState> uiState = new MutableLiveData<>();
    private final MutableLiveData<Event<UiEvent>> events = new MutableLiveData<>();

    // Aşağıdaki alanlar yalnızca ana iş parçacığında okunur ve yazılır.

    /** open() daha önce çağrıldı mı? İkinci çağrı (ör. ekran döndürme) oturumu sıfırlamamalı. */
    private boolean opened;

    /** Kayıtlı rekor (en son bilinen değer). */
    private long storedHighScore;

    /**
     * Oturum sayacı: open() ve yeni oyunla artar. Arka plandan gelen bir sonuç, işi başlatan oturum
     * hâlâ geçerliyse yayınlanır; değilse (ör. önceki oyunun oyun-sonu sonucu) atılır.
     */
    private int session;

    /**
     * @param gameSession            kayıt açma, kaydetme ve oyun sonu iş akışı
     * @param ioExecutor             disk işleri için (üretimde tek iş parçacıklı, sıralı)
     * @param mainExecutor           sonuçları ana iş parçacığına taşır (üretimde ana Looper'a post
     *                               eden Handler)
     */
    public GameViewModel(GameEngine engine,
                         GameSession gameSession,
                         Executor ioExecutor,
                         Executor mainExecutor) {
        this.engine = Objects.requireNonNull(engine);
        this.gameSession = Objects.requireNonNull(gameSession);
        this.ioExecutor = Objects.requireNonNull(ioExecutor);
        this.mainExecutor = Objects.requireNonNull(mainExecutor);
    }

    /** Ekranın çizeceği durum. Yükleme bitene kadar {@code isLoading()==true}. */
    public LiveData<GameUiState> getUiState() {
        return uiState;
    }

    /** Tek seferlik olaylar; gözlemci {@link Event#getContentIfNotHandled()} ile okumalı. */
    public LiveData<Event<UiEvent>> getEvents() {
        return events;
    }

    /**
     * Oyun oturumunu açar. Yalnızca İLK çağrıda iş yapar; Activity yeniden oluşturulup tekrar
     * çağırdığında mevcut oyun olduğu gibi kalır.
     *
     * <p>Kayıt yükleme kuralları (startNew=false): ACTIVE → geri yüklenir; EMPTY → yeni oyun;
     * INVALID ya da tutarsız kayıt (restore IllegalArgumentException) → kayıt silinir, yeni oyun
     * başlar, {@code INVALID_SAVE} olayı gelir; okuma hatası (IOException) → kayıt korunur, oyun
     * başlatılmaz (durum: loading=false, game=null) ve {@code LOAD_FAILED} olayı gelir.
     *
     * @param startNew true → yeni oyun başlat ve hemen kaydet; false → kayıtlı oyunu yüklemeyi dene
     */
    public void open(final boolean startNew) {
        if (opened) {
            return;
        }
        opened = true;
        final int mySession = ++session;
        uiState.setValue(GameUiState.loading());

        ioExecutor.execute(() -> {
            GameSession.OpenResult result = gameSession.open(startNew);
            UiEvent event = result.loadFailed ? UiEvent.loadFailed()
                    : result.invalidSave ? UiEvent.invalidSave() : null;
            deliverLoaded(mySession, result.highScore, result.game, event, result.saveFailed);
        });
    }

    /**
     * Sürükleme sırasında önizleme: parça buraya bırakılırsa geçerli mi, hangi çizgiler dolar?
     * Senkron çalışır, durumu değiştirmez. Oyun yoksa geçersiz döner.
     */
    public MovePreview preview(int slot, int row, int col) {
        GameState game = currentGame();
        if (game == null) {
            return MovePreview.invalid();
        }
        return engine.preview(game, slot, row, col);
    }

    /**
     * Sürükleme sırasında parçanın bırakılacağı hücreyi bulur. Parmak tam bir hücrenin üstünde
     * olmasa da, yakında ({@link PlacementResolver#DEFAULT_RADIUS} hücre içinde) parçanın sığdığı
     * bir yer varsa oraya "yapıştırır" (snapped=true). Böylece oyuncu milimetrik nişan almak zorunda
     * kalmaz. Ekran dönen hedefin satır/sütununu {@link #preview} ve {@link #place}'e verir.
     *
     * <p>Ana iş parçacığında, senkron çalışır; durumu değiştirmez (her karede çağrılabilir).
     *
     * @param slot     tepsideki yuva (0..2)
     * @param fracRow  parçanın sol-üst hücresinin kesirli satırı
     * @param fracCol  parçanın sol-üst hücresinin kesirli sütunu
     * @param previous bir önceki karede bulunan hedef (eşitlikte titremeyi önler); null olabilir
     * @return hedef; oyun yoksa, yükleniyorsa, bittiyse ya da yuva boş/geçersizse
     *         {@link PlacementTarget#invalid()}
     */
    public PlacementTarget resolveTarget(int slot, float fracRow, float fracCol,
                                         PlacementTarget previous) {
        GameState game = currentGame();
        if (game == null || game.isGameOver() || slot < 0 || slot >= GameState.TRAY_SIZE) {
            return PlacementTarget.invalid();
        }
        Piece piece = game.getTrayPiece(slot);
        if (piece == null) {
            return PlacementTarget.invalid();
        }
        return PlacementResolver.resolve(game.getBoard(), piece, fracRow, fracCol,
                PlacementResolver.DEFAULT_RADIUS, previous);
    }

    /**
     * Hamle yapar. Geçersizse hiçbir şey yayınlanmaz. Geçerliyse yeni durum HEMEN ekrana yayınlanır,
     * {@code MOVE_COMMITTED} olayı gönderilir ve kayıt arka planda yapılır. Oyun bittiyse ayrıca
     * rekor gönderilir, kayıt kapatılır ve {@code GAME_OVER} olayı gelir.
     */
    public void place(int slot, int row, int col) {
        final GameState game = currentGame();
        if (game == null || game.isGameOver()) {
            return;
        }
        final MoveResult result = engine.place(game, slot, row, col);
        if (!result.isPlaced()) {
            return;
        }
        final GameState next = result.getState();
        uiState.setValue(stateFor(next));
        events.setValue(new Event<>(UiEvent.moveCommitted(result)));

        if (next.isGameOver()) {
            finishGameInBackground(result);
        } else {
            saveInBackground(next);
        }
    }

    /** Yeni oyun başlatır ve kaydeder ("Tekrar Oyna"). Yükleme sürerken yok sayılır. */
    public void newGame() {
        GameUiState current = uiState.getValue();
        if (current != null && current.isLoading()) {
            return;
        }
        opened = true;
        session++; // Eski oyunun henüz teslim edilmemiş sonuçları artık geçersiz.
        GameState game = engine.newGame();
        uiState.setValue(stateFor(game));
        saveInBackground(game);
    }

    /** Mevcut oyunu (bitmemişse) arka planda kaydeder; Activity'nin onPause'unda çağrılır. */
    public void saveNow() {
        GameState game = currentGame();
        if (game == null || game.isGameOver()) {
            return;
        }
        saveInBackground(game);
    }

    // ---------------------------------------------------------------- yardımcılar

    /** Ekranda gösterilen (yüklemesi bitmiş) oyun; yoksa null. Yalnızca ana iş parçacığında. */
    private GameState currentGame() {
        GameUiState state = uiState.getValue();
        if (state == null || state.isLoading()) {
            return null;
        }
        return state.getGame();
    }

    /** Gösterilecek rekor: kayıtlı rekor ile mevcut skorun büyüğü (rekor kırılırken canlı artar). */
    private GameUiState stateFor(GameState game) {
        if (game == null) {
            return new GameUiState(null, storedHighScore, false);
        }
        return new GameUiState(game, Math.max(storedHighScore, game.getScore()), false);
    }

    /**
     * open() sonucunu ana iş parçacığında yayınlar (arka plandan çağrılır).
     *
     * @param event      gönderilecek olay ya da null
     * @param saveFailed ilk kayıt başarısız olduysa (ve başka olay yoksa) SAVE_FAILED gönderilir
     */
    private void deliverLoaded(final int mySession, final long high, final GameState game,
                               final UiEvent event, final boolean saveFailed) {
        mainExecutor.execute(new Runnable() {
            @Override
            public void run() {
                storedHighScore = high;
                if (mySession != session) {
                    return;
                }
                uiState.setValue(stateFor(game));
                if (event != null) {
                    events.setValue(new Event<>(event));
                } else if (saveFailed) {
                    events.setValue(new Event<>(UiEvent.saveFailed()));
                }
            }
        });
    }

    /**
     * Records the completed game and clears the classic save on the I/O executor. A history
     * write error produces a warning and the game-over screen still opens.
     * Bitmiş durumun önce kaydedilmesinin nedeni: silme başarısız olsa bile diskte "Devam Et" ile
     * açılabilecek eski bir ACTIVE kayıt kalmaz (bitmiş oyun kaydı yüklemede EMPTY sayılır). İkisi de başarısızsa SAVE_FAILED.
     */
    private void finishGameInBackground(final MoveResult result) {
        final int mySession = session;
        final GameState end = result.getState();
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                GameSession.FinishResult finished = gameSession.finish(end);
                mainExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        storedHighScore = Math.max(storedHighScore, finished.highScore);
                        if (mySession != session) {
                            return; // Bu arada yeni oyun başladı: eski oyunun sonucunu gösterme.
                        }
                        // Önce durum (güncel rekorla), sonra olaylar; GAME_OVER en son gelir.
                        uiState.setValue(stateFor(end));
                        if (finished.saveFailed) {
                            events.setValue(new Event<>(UiEvent.saveFailed()));
                        }
                        events.setValue(new Event<>(UiEvent.gameOver(result, finished.newHighScore, finished.topRank)));
                    }
                });
            }
        });
    }

    private void saveInBackground(final GameState game) {
        final int mySession = session;
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (gameSession.save(game)) {
                    return;
                }
                mainExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (mySession == session) {
                            events.setValue(new Event<>(UiEvent.saveFailed()));
                        }
                    }
                });
            }
        });
    }

}
