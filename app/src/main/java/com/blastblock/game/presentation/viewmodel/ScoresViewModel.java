package com.blastblock.game.presentation.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.blastblock.game.domain.model.ScoreRecord;
import com.blastblock.game.domain.repository.ScoreHistoryRepository;
import com.blastblock.game.presentation.state.ScoresUiState;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * "En İyi Skorlar" ekranının ViewModel'i.
 *
 * <p>Liste diskten okunduğu için okuma {@code ioExecutor} üzerinde yapılır (ana iş parçacığı
 * takılmasın). Sonuç {@code mainExecutor} ile ana iş parçacığına taşınır ve orada {@code setValue}
 * ile yayınlanır. Activity ya da Context referansı tutulmaz; ekran döndürülünce ViewModel ve son
 * liste korunur.
 *
 * <p>Tüm public metotlar ana iş parçacığından çağrılmalıdır.
 */
public final class ScoresViewModel extends ViewModel {

    public static final int PAGE_SIZE = 50;
    private final ScoreHistoryRepository repository;
    private final Executor ioExecutor;
    private final Executor mainExecutor;

    private final MutableLiveData<ScoresUiState> uiState = new MutableLiveData<>();

    /**
     * Yenileme sayacı (yalnızca ana iş parçacığında). Art arda iki refresh() çağrılırsa yalnızca
     * EN SON isteğin sonucu yayınlanır; eski bir okuma yeni sonucun üstüne yazamaz.
     */
    private int request;

    /**
     * @param ioExecutor   disk okuması için
     * @param mainExecutor sonucu ana iş parçacığına taşır
     */
    public ScoresViewModel(ScoreHistoryRepository repository,
                           Executor ioExecutor,
                           Executor mainExecutor) {
        if (repository == null || ioExecutor == null || mainExecutor == null) {
            throw new IllegalArgumentException("Bağımlılıklar null olamaz");
        }
        this.repository = repository;
        this.ioExecutor = ioExecutor;
        this.mainExecutor = mainExecutor;
    }

    /** Ekranın çizeceği durum; ilk refresh()'ten önce null olabilir. */
    public LiveData<ScoresUiState> getUiState() {
        return uiState;
    }

    /**
     * Listeyi yeniden okur: önce "yükleniyor" yayınlanır, okuma arka planda yapılır, sonuç ana iş
     * parçacığında yayınlanır. Okuma hatasında (IOException) boş liste ve {@code hasError()==true}.
     */
    public void refresh() {
        ScoresUiState current = uiState.getValue();
        load(current == null ? 0 : current.getOffset());
    }

    public void nextPage() {
        ScoresUiState current = uiState.getValue();
        if (current != null && current.hasNextPage()) {
            load(current.getOffset() + PAGE_SIZE);
        }
    }

    public void previousPage() {
        ScoresUiState current = uiState.getValue();
        if (current != null && !current.isLoading() && current.getOffset() > 0) {
            load(current.getOffset() - PAGE_SIZE);
        }
    }

    private void load(long offset) {
        final int myRequest = ++request;
        uiState.setValue(ScoresUiState.loading(offset));
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                List<ScoreRecord> scores;
                boolean error;
                try {
                    scores = repository.getScores(offset, PAGE_SIZE + 1);
                    error = false;
                } catch (IOException e) {
                    scores = Collections.emptyList();
                    error = true;
                }
                boolean hasNext = scores.size() > PAGE_SIZE;
                if (hasNext) scores = scores.subList(0, PAGE_SIZE);
                final ScoresUiState result = new ScoresUiState(scores, false, error, offset, hasNext);
                mainExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (myRequest == request) {
                            uiState.setValue(result);
                        }
                    }
                });
            }
        });
    }
}
