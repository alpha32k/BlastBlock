package com.blastblock.game.presentation.viewmodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.Observer;

import com.blastblock.game.domain.model.ScoreRecord;
import com.blastblock.game.presentation.state.ScoresUiState;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * ScoresViewModel testleri. Executor'lar işi hemen çalıştırır (Runnable::run); ana iş parçacığı
 * kuyruğunu taklit etmek gereken testte işler bir listede bekletilir.
 */
public class ScoresViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantRule = new InstantTaskExecutorRule();

    private Fakes.FakeScoreHistoryRepository repo;
    private final List<ScoresUiState> states = new ArrayList<>();

    @Before
    public void setUp() {
        repo = new Fakes.FakeScoreHistoryRepository();
    }

    private ScoresViewModel create(Executor main) {
        ScoresViewModel vm = new ScoresViewModel(repo, Runnable::run, main);
        vm.getUiState().observeForever(new Observer<ScoresUiState>() {
            @Override
            public void onChanged(ScoresUiState s) {
                states.add(s);
            }
        });
        return vm;
    }

    @Test
    public void refresh_success_publishesLoadingThenScores() {
        List<ScoreRecord> list = Arrays.asList(new ScoreRecord(900, 2L), new ScoreRecord(300, 1L));
        repo.scores = new ArrayList<>(list);
        ScoresViewModel vm = create(Runnable::run);

        vm.refresh();

        assertEquals(2, states.size());
        assertTrue(states.get(0).isLoading());
        assertTrue(states.get(0).getScores().isEmpty());
        ScoresUiState s = vm.getUiState().getValue();
        assertFalse(s.isLoading());
        assertFalse(s.hasError());
        assertEquals(list, s.getScores());
        assertEquals(1, repo.readCount);
    }

    @Test
    public void refresh_emptyList_isNotAnError() {
        ScoresViewModel vm = create(Runnable::run);
        vm.refresh();

        ScoresUiState s = vm.getUiState().getValue();
        assertFalse(s.isLoading());
        assertFalse(s.hasError());
        assertTrue(s.getScores().isEmpty());
    }

    @Test
    public void refresh_ioError_publishesErrorWithEmptyList() {
        repo.scores.add(new ScoreRecord(500, 1L));
        repo.failRead = true;
        ScoresViewModel vm = create(Runnable::run);

        vm.refresh();

        ScoresUiState s = vm.getUiState().getValue();
        assertFalse(s.isLoading());
        assertTrue(s.hasError());
        assertTrue(s.getScores().isEmpty());
    }

    @Test
    public void refresh_afterError_recovers() {
        repo.failRead = true;
        ScoresViewModel vm = create(Runnable::run);
        vm.refresh();
        assertTrue(vm.getUiState().getValue().hasError());

        repo.failRead = false;
        repo.scores.add(new ScoreRecord(42, 7L));
        vm.refresh();

        ScoresUiState s = vm.getUiState().getValue();
        assertFalse(s.hasError());
        assertEquals(1, s.getScores().size());
    }

    @Test
    public void staleRefreshResult_isDropped() {
        final List<Runnable> mainQueue = new ArrayList<>();
        ScoresViewModel vm = create(new Executor() {
            @Override
            public void execute(Runnable command) {
                mainQueue.add(command);
            }
        });

        repo.failRead = true;
        vm.refresh(); // Bu sonuç (hata) kuyrukta bekliyor.
        repo.failRead = false;
        repo.scores.add(new ScoreRecord(10, 1L));
        vm.refresh(); // Daha yeni istek.

        while (!mainQueue.isEmpty()) {
            mainQueue.remove(0).run();
        }
        ScoresUiState s = vm.getUiState().getValue();
        assertFalse(s.hasError());
        assertEquals(1, s.getScores().size());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void uiState_listIsImmutable() {
        repo.scores.add(new ScoreRecord(10, 1L));
        ScoresViewModel vm = create(Runnable::run);
        vm.refresh();
        vm.getUiState().getValue().getScores().clear();
    }

    @Test
    public void nextAndPreviousPageKeepRanksAcrossMoreThanTenScores() {
        for (int score = 65; score > 0; score--) {
            repo.scores.add(new ScoreRecord(score, 1));
        }
        ScoresViewModel vm = create(Runnable::run);
        vm.refresh();
        assertEquals(50, vm.getUiState().getValue().getScores().size());
        assertTrue(vm.getUiState().getValue().hasNextPage());

        vm.nextPage();
        ScoresUiState second = vm.getUiState().getValue();
        assertEquals(50, second.getOffset());
        assertEquals(15, second.getScores().size());
        assertEquals(15, second.getScores().get(0).getScore());
        assertFalse(second.hasNextPage());

        vm.previousPage();
        assertEquals(0, vm.getUiState().getValue().getOffset());
        assertEquals(65, vm.getUiState().getValue().getScores().get(0).getScore());
    }
}
