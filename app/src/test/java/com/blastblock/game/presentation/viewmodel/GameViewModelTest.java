package com.blastblock.game.presentation.viewmodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.Observer;

import com.blastblock.game.domain.catalog.PieceCatalog;
import com.blastblock.game.domain.engine.GameEngine;
import com.blastblock.game.domain.engine.PlacementTarget;
import com.blastblock.game.domain.model.Board;
import com.blastblock.game.domain.model.GameState;
import com.blastblock.game.domain.model.Piece;
import com.blastblock.game.domain.model.ScoreRecord;
import com.blastblock.game.domain.repository.GameLoadResult;
import com.blastblock.game.domain.usecase.GameSession;
import com.blastblock.game.presentation.state.Event;
import com.blastblock.game.presentation.state.GameUiState;
import com.blastblock.game.presentation.state.UiEvent;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executor;

/**
 * GameViewModel testleri. InstantTaskExecutorRule, LiveData'nın setValue/postValue işlerini test
 * iş parçacığında anında yapmasını sağlar; ioExecutor olarak da işi hemen çalıştıran
 * {@code Runnable::run} verilir. Böylece testler senkron ve tekrarlanabilir olur.
 */
public class GameViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantRule = new InstantTaskExecutorRule();

    private Fakes.FakeGameRepository gameRepo;
    private Fakes.FakeHighScoreRepository highRepo;
    private Fakes.FakeScoreHistoryRepository historyRepo;
    /** Sabit saat: oyun sonu kaydının zamanı testte bilinir. */
    private static final long NOW = 1_700_000_000_000L;
    private GameViewModel vm;
    private final List<GameUiState> states = new ArrayList<>();
    private final List<Event<UiEvent>> events = new ArrayList<>();

    @Before
    public void setUp() {
        gameRepo = new Fakes.FakeGameRepository();
        highRepo = new Fakes.FakeHighScoreRepository();
        historyRepo = new Fakes.FakeScoreHistoryRepository();
        vm = newViewModel(Runnable::run);
        vm.getUiState().observeForever(new Observer<GameUiState>() {
            @Override
            public void onChanged(GameUiState s) {
                states.add(s);
            }
        });
        vm.getEvents().observeForever(new Observer<Event<UiEvent>>() {
            @Override
            public void onChanged(Event<UiEvent> e) {
                events.add(e);
            }
        });
    }

    private GameUiState current() {
        return vm.getUiState().getValue();
    }

    private GameViewModel newViewModel(Executor mainExecutor) {
        GameEngine engine = new GameEngine(new Random(42));
        GameSession session = new GameSession(engine, gameRepo, highRepo, historyRepo, () -> NOW);
        return new GameViewModel(engine, session, Runnable::run, mainExecutor);
    }

    private List<UiEvent.Type> eventTypes() {
        List<UiEvent.Type> types = new ArrayList<>();
        for (Event<UiEvent> e : events) {
            types.add(e.peekContent().getType());
        }
        return types;
    }

    /**
     * Tek hamlelik, neredeyse dolu bir durum: yalnızca köşegen hücreler (r,r) boş, tepside bir
     * "single" ve bir "square3". single (0,0)'a konunca yalnızca satır 0 ve sütun 0 dolar ve silinir
     * (diğer satır/sütunlarda köşegen deliği kalır); hiçbir yerde 3x3 boşluk oluşmadığı için
     * square3 sığmaz → oyun biter.
     */
    private static GameState oneMoveLeftState() {
        int[] cells = new int[Board.SIZE * Board.SIZE];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = 1 + (i % PieceCatalog.COLOR_COUNT);
        }
        for (int r = 0; r < Board.SIZE; r++) {
            cells[r * Board.SIZE + r] = 0;
        }
        Piece[] tray = {PieceCatalog.create("single", 2), PieceCatalog.create("square3", 3), null};
        return new GameState(new Board(cells), tray, 500, 0, false);
    }

    @Test
    public void openNew_publishesLoadingThenNewGameAndSaves() {
        highRepo.highScore = 1234;
        vm.open(true);

        assertTrue(states.get(0).isLoading());
        GameUiState s = current();
        assertFalse(s.isLoading());
        assertNotNull(s.getGame());
        assertEquals(0, s.getGame().getScore());
        assertEquals(1234, s.getHighScore());
        assertEquals(1, gameRepo.saveCount);
        assertEquals(s.getGame(), gameRepo.lastSaved);
        assertTrue(events.isEmpty());
    }

    @Test
    public void openTwice_doesNotResetSession() {
        vm.open(true);
        GameState first = current().getGame();
        vm.place(0, 0, 0);
        GameState afterMove = current().getGame();
        int stateCount = states.size();

        vm.open(true);
        vm.open(false);

        assertSame(afterMove, current().getGame());
        assertTrue(afterMove.getScore() > first.getScore());
        assertEquals(stateCount, states.size());
    }

    @Test
    public void openExisting_restoresActiveSave() {
        GameState saved = oneMoveLeftState();
        gameRepo.loadResult = GameLoadResult.active(saved);
        vm.open(false);

        assertEquals(saved, current().getGame());
        assertEquals(500, current().getGame().getScore());
        assertEquals(500, current().getHighScore()); // max(kayıtlı rekor 0, skor 500)
        assertEquals(0, gameRepo.saveCount);
        assertEquals(0, gameRepo.clearCount);
        assertTrue(events.isEmpty());
    }

    @Test
    public void openExisting_emptySaveStartsNewGame() {
        vm.open(false);
        assertEquals(0, current().getGame().getScore());
        assertEquals(1, gameRepo.saveCount);
        assertTrue(events.isEmpty());
    }

    @Test
    public void openExisting_invalidSaveClearsAndStartsNewGameWithEvent() {
        gameRepo.loadResult = GameLoadResult.invalid();
        vm.open(false);

        assertEquals(1, gameRepo.clearCount);
        assertNotNull(current().getGame());
        assertEquals(0, current().getGame().getScore());
        assertEquals(1, gameRepo.saveCount);
        assertEquals(1, events.size());
        assertEquals(UiEvent.Type.INVALID_SAVE, events.get(0).peekContent().getType());
    }

    @Test
    public void openExisting_ioErrorPublishesLoadFailedAndKeepsSave() {
        gameRepo.failLoad = true;
        vm.open(false);

        // Sağlam olabilecek kayıt korunur: ne silinir ne de üstüne yeni oyun yazılır.
        assertEquals(0, gameRepo.clearCount);
        assertEquals(0, gameRepo.saveCount);
        assertFalse(current().isLoading());
        assertNull(current().getGame());
        assertEquals(1, events.size());
        assertEquals(UiEvent.Type.LOAD_FAILED, events.get(0).peekContent().getType());

        // Oyun yokken hamle ve kayıt yok sayılır.
        vm.place(0, 0, 0);
        vm.saveNow();
        assertEquals(1, events.size());
        assertEquals(0, gameRepo.saveCount);
    }

    @Test
    public void openExisting_inconsistentStateBehavesLikeInvalid() {
        // Tepsi boş ama oyun bitmemiş → restore IllegalArgumentException atar.
        GameState broken = new GameState(Board.empty(), new Piece[3], 10, 0, false);
        gameRepo.loadResult = GameLoadResult.active(broken);
        vm.open(false);

        assertEquals(1, gameRepo.clearCount);
        assertEquals(0, current().getGame().getScore());
        assertEquals(UiEvent.Type.INVALID_SAVE, events.get(0).peekContent().getType());
    }

    @Test
    public void openExisting_saveWithNoPlayablePiecesRecordsTheFinishedScore() throws IOException {
        int[] cells = new int[Board.SIZE * Board.SIZE];
        Arrays.fill(cells, 1);
        GameState snapshot = new GameState(new Board(cells),
                new Piece[] {PieceCatalog.create("single", 1), null, null}, 740, 0, false);
        gameRepo.loadResult = GameLoadResult.active(snapshot);

        vm.open(false);

        assertEquals(740, highRepo.getHighScore());
        assertEquals(Collections.singletonList(new ScoreRecord(740, NOW)), historyRepo.getScores(0, 10));
        assertEquals(0, current().getGame().getScore());
    }

    @Test
    public void validPlace_increasesScorePublishesEventAndSaves() {
        vm.open(true);
        GameState before = current().getGame();
        int savesBefore = gameRepo.saveCount;
        int cellCount = before.getTrayPiece(0).getCellCount();

        vm.place(0, 0, 0); // Boş tahtada her parça (0,0)'a sığar.

        GameState after = current().getGame();
        assertEquals(before.getScore() + 10L * cellCount, after.getScore());
        assertNull(after.getTrayPiece(0));
        assertEquals(1, events.size());
        UiEvent e = events.get(0).peekContent();
        assertEquals(UiEvent.Type.MOVE_COMMITTED, e.getType());
        assertTrue(e.getMoveResult().isPlaced());
        assertEquals(savesBefore + 1, gameRepo.saveCount);
        assertEquals(after, gameRepo.lastSaved);
    }

    @Test
    public void invalidPlace_changesNothing() {
        vm.open(true);
        GameState before = current().getGame();
        int stateCount = states.size();
        int savesBefore = gameRepo.saveCount;

        vm.place(0, -1, 0); // Tahta dışı.
        vm.place(5, 0, 0); // Geçersiz yuva.

        assertSame(before, current().getGame());
        assertEquals(stateCount, states.size());
        assertTrue(events.isEmpty());
        assertEquals(savesBefore, gameRepo.saveCount);
    }

    @Test
    public void placeBeforeOpen_isIgnored() {
        vm.place(0, 0, 0);
        assertNull(current());
        assertTrue(events.isEmpty());
        assertFalse(vm.preview(0, 0, 0).isValid());
    }

    @Test
    public void preview_usesCurrentState() {
        vm.open(true);
        assertTrue(vm.preview(0, 0, 0).isValid());
        assertFalse(vm.preview(0, -1, 0).isValid());
    }

    @Test
    public void saveFailure_publishesSaveFailed() {
        vm.open(true);
        gameRepo.failSave = true;
        vm.place(0, 0, 0);

        assertEquals(2, events.size());
        assertEquals(UiEvent.Type.MOVE_COMMITTED, events.get(0).peekContent().getType());
        assertEquals(UiEvent.Type.SAVE_FAILED, events.get(1).peekContent().getType());
    }

    @Test
    public void event_isConsumedOnlyOnce() {
        vm.open(true);
        vm.place(0, 0, 0);
        Event<UiEvent> event = vm.getEvents().getValue();

        assertNotNull(event.getContentIfNotHandled());
        assertNull(event.getContentIfNotHandled());

        // Yeni gözlemci (ör. döndürmeden sonra) aynı Event'i alır ama içerik artık null döner.
        final List<UiEvent> seen = new ArrayList<>();
        vm.getEvents().observeForever(new Observer<Event<UiEvent>>() {
            @Override
            public void onChanged(Event<UiEvent> e) {
                UiEvent c = e.getContentIfNotHandled();
                if (c != null) {
                    seen.add(c);
                }
            }
        });
        assertTrue(seen.isEmpty());
        assertNotNull(event.peekContent());
    }

    @Test
    public void gameOver_submitsScoreClearsSaveAndPublishesGameOver() {
        highRepo.highScore = 100;
        gameRepo.loadResult = GameLoadResult.active(oneMoveLeftState());
        vm.open(false);
        assertFalse(current().getGame().isGameOver());

        vm.place(0, 0, 0);

        GameState end = current().getGame();
        assertTrue(end.isGameOver());
        assertEquals(1, highRepo.submitCount);
        assertEquals(end.getScore(), highRepo.lastSubmitted);
        // Önce bitmiş durum (gameOver=true) kaydedilir, sonra kayıt silinir.
        assertEquals(1, gameRepo.saveCount);
        assertEquals(1, gameRepo.clearCount);
        assertNull(gameRepo.lastSaved);
        assertEquals(end.getScore(), current().getHighScore());

        assertEquals(2, events.size());
        assertEquals(UiEvent.Type.MOVE_COMMITTED, events.get(0).peekContent().getType());
        UiEvent over = events.get(1).peekContent();
        assertEquals(UiEvent.Type.GAME_OVER, over.getType());
        assertTrue(over.isNewHighScore());
        assertNotNull(over.getMoveResult());
        assertEquals(2, over.getMoveResult().getLinesCleared());

        // Oyun bittikten sonra hamle yok sayılır; saveNow da kaydetmez.
        vm.place(1, 0, 0);
        vm.saveNow();
        assertEquals(2, events.size());
        assertEquals(1, gameRepo.saveCount);
    }

    @Test
    public void gameOver_withoutNewHighScore() {
        highRepo.highScore = 1_000_000;
        gameRepo.loadResult = GameLoadResult.active(oneMoveLeftState());
        vm.open(false);
        vm.place(0, 0, 0);

        UiEvent over = events.get(events.size() - 1).peekContent();
        assertEquals(UiEvent.Type.GAME_OVER, over.getType());
        assertFalse(over.isNewHighScore());
        assertEquals(1_000_000, current().getHighScore());
    }

    @Test
    public void newGame_resetsAndSaves() {
        gameRepo.loadResult = GameLoadResult.active(oneMoveLeftState());
        vm.open(false);
        vm.place(0, 0, 0);
        assertTrue(current().getGame().isGameOver());

        int savesBefore = gameRepo.saveCount;
        vm.newGame();

        assertFalse(current().getGame().isGameOver());
        assertEquals(0, current().getGame().getScore());
        assertEquals(savesBefore + 1, gameRepo.saveCount);
        assertEquals(current().getGame(), gameRepo.lastSaved);
    }

    @Test
    public void saveNow_savesCurrentGame() {
        vm.open(true);
        int before = gameRepo.saveCount;
        vm.saveNow();
        assertEquals(before + 1, gameRepo.saveCount);
        assertEquals(current().getGame(), gameRepo.lastSaved);
    }

    @Test
    public void gameOver_clearFailureLeavesFinishedStateOnDisk() {
        gameRepo.loadResult = GameLoadResult.active(oneMoveLeftState());
        vm.open(false);
        gameRepo.failClear = true;
        vm.place(0, 0, 0);

        // Silme başarısız olsa da diskteki son kayıt bitmiş oyundur; "Devam Et" ile açılmaz.
        assertNotNull(gameRepo.lastSaved);
        assertTrue(gameRepo.lastSaved.isGameOver());
        assertEquals(UiEvent.Type.GAME_OVER, events.get(events.size() - 1).peekContent().getType());
        assertFalse(eventTypes().contains(UiEvent.Type.SAVE_FAILED));
    }

    @Test
    public void gameOver_saveAndClearBothFail_sendsSaveFailedThenGameOver() {
        gameRepo.loadResult = GameLoadResult.active(oneMoveLeftState());
        vm.open(false);
        gameRepo.failClear = true;
        gameRepo.failSave = true;
        vm.place(0, 0, 0);

        List<UiEvent.Type> types = eventTypes();
        assertEquals(3, types.size());
        assertEquals(UiEvent.Type.MOVE_COMMITTED, types.get(0));
        assertEquals(UiEvent.Type.SAVE_FAILED, types.get(1));
        assertEquals(UiEvent.Type.GAME_OVER, types.get(2));
    }

    @Test
    public void staleGameOverResult_doesNotOverwriteNewGame() {
        // Ana iş parçacığı kuyruğunu taklit eden executor: işler drain() çağrılana dek bekler.
        final List<Runnable> mainQueue = new ArrayList<>();
        GameViewModel queued = newViewModel(mainQueue::add);
        final List<UiEvent.Type> seen = new ArrayList<>();
        queued.getEvents().observeForever(new Observer<Event<UiEvent>>() {
            @Override
            public void onChanged(Event<UiEvent> e) {
                seen.add(e.peekContent().getType());
            }
        });

        gameRepo.loadResult = GameLoadResult.active(oneMoveLeftState());
        queued.open(false);
        drain(mainQueue);
        assertFalse(queued.getUiState().getValue().getGame().isGameOver());

        queued.place(0, 0, 0); // Oyun biter; oyun-sonu sonucu ana kuyrukta bekliyor.
        assertEquals(1, mainQueue.size());
        queued.newGame(); // Oyuncu bu arada yeni oyuna geçti.
        drain(mainQueue);

        GameUiState s = queued.getUiState().getValue();
        assertFalse(s.getGame().isGameOver());
        assertEquals(0, s.getGame().getScore());
        assertFalse(seen.contains(UiEvent.Type.GAME_OVER));
        // Rekor yine de güncellenmiş olmalı.
        assertEquals(1, highRepo.submitCount);
    }

    // ------------------------------------------------------------ v1.1: En İyi 10 listesi

    @Test
    public void gameOver_addsScoreToTopListWithClockAndCarriesRank() {
        historyRepo.scores.add(new ScoreRecord(1_000_000, 1L)); // Bizden yüksek bir skor var.
        gameRepo.loadResult = GameLoadResult.active(oneMoveLeftState());
        vm.open(false);
        vm.place(0, 0, 0);

        GameState end = current().getGame();
        assertTrue(end.isGameOver());
        assertEquals(1, historyRepo.addCount);
        assertEquals(new ScoreRecord(end.getScore(), NOW), historyRepo.lastAdded);

        UiEvent over = events.get(events.size() - 1).peekContent();
        assertEquals(UiEvent.Type.GAME_OVER, over.getType());
        assertEquals(2, over.getTopRank());
        // Kayıt yine önce bitmiş durumla yazılıp sonra silinir.
        assertEquals(1, gameRepo.clearCount);
        assertNull(gameRepo.lastSaved);
    }

    @Test
    public void gameOver_historyWriteFailureIsReportedAndGameStillEnds() {
        historyRepo.failAdd = true;
        gameRepo.loadResult = GameLoadResult.active(oneMoveLeftState());
        vm.open(false);
        vm.place(0, 0, 0);

        assertEquals(1, historyRepo.addCount);
        UiEvent over = events.get(events.size() - 1).peekContent();
        assertEquals(UiEvent.Type.GAME_OVER, over.getType());
        assertEquals(0, over.getTopRank());
        assertEquals(1, highRepo.submitCount);
        assertEquals(1, gameRepo.clearCount);
        assertNull(gameRepo.lastSaved);
        assertTrue(eventTypes().contains(UiEvent.Type.SAVE_FAILED));
    }

    @Test
    public void gameOver_topListRuntimeFailure_rankZeroAndSaveCleared() {
        historyRepo.throwRuntimeOnAdd = true;
        gameRepo.loadResult = GameLoadResult.active(oneMoveLeftState());
        vm.open(false);
        vm.place(0, 0, 0);

        UiEvent over = events.get(events.size() - 1).peekContent();
        assertEquals(UiEvent.Type.GAME_OVER, over.getType());
        assertEquals(0, over.getTopRank());
        assertEquals(1, gameRepo.clearCount);
    }

    @Test
    public void nonGameOverEvents_haveRankZeroAndDoNotTouchTopList() {
        vm.open(true);
        vm.place(0, 0, 0);
        assertEquals(UiEvent.Type.MOVE_COMMITTED, events.get(0).peekContent().getType());
        assertEquals(0, events.get(0).peekContent().getTopRank());
        assertEquals(0, historyRepo.addCount);
    }

    /** Boş tahta, yalnızca (3,3) dolu; tepside tek bir "single". */
    private static GameState singleWithBlockedCenter() {
        int[] cells = new int[Board.SIZE * Board.SIZE];
        cells[3 * Board.SIZE + 3] = 1;
        Piece[] tray = {PieceCatalog.create("single", 2), null, null};
        return new GameState(new Board(cells), tray, 0, 0, false);
    }

    @Test
    public void resolveTarget_directHitIsNotSnapped() {
        gameRepo.loadResult = GameLoadResult.active(singleWithBlockedCenter());
        vm.open(false);

        assertEquals(PlacementTarget.at(0, 1, false), vm.resolveTarget(0, 0.2f, 0.9f, null));
    }

    @Test
    public void resolveTarget_occupiedCellSnapsToNearestFreeCell() {
        gameRepo.loadResult = GameLoadResult.active(singleWithBlockedCenter());
        vm.open(false);

        // (3,3) dolu; en yakın boş hücre (3,4) (uzaklık 0.6): yakına kaydırılmış olarak döner.
        PlacementTarget t = vm.resolveTarget(0, 3.0f, 3.4f, null);
        assertTrue(t.isValid());
        assertTrue(t.isSnapped());
        assertEquals(3, t.getRow());
        assertEquals(4, t.getCol());
        // Çözülen hedef gerçekten geçerli bir hamle.
        assertTrue(vm.preview(0, t.getRow(), t.getCol()).isValid());
    }

    @Test
    public void resolveTarget_invalidWithoutGameEmptySlotOrBadSlot() {
        assertFalse(vm.resolveTarget(0, 0f, 0f, null).isValid()); // open() yok.

        gameRepo.loadResult = GameLoadResult.active(singleWithBlockedCenter());
        vm.open(false);
        assertFalse(vm.resolveTarget(1, 0f, 0f, null).isValid()); // Boş yuva.
        assertFalse(vm.resolveTarget(7, 0f, 0f, null).isValid()); // Geçersiz yuva.
    }

    @Test
    public void resolveTarget_invalidAfterGameOver() {
        gameRepo.loadResult = GameLoadResult.active(oneMoveLeftState());
        vm.open(false);
        assertTrue(vm.resolveTarget(0, 0f, 0f, null).isValid());

        vm.place(0, 0, 0);
        assertTrue(current().getGame().isGameOver());
        assertEquals(PlacementTarget.invalid(), vm.resolveTarget(1, 0f, 0f, null));
    }

    private static void drain(List<Runnable> queue) {
        while (!queue.isEmpty()) {
            queue.remove(0).run();
        }
    }
}
