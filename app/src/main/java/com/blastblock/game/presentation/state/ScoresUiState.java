package com.blastblock.game.presentation.state;

import com.blastblock.game.domain.model.ScoreRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The current history page and its loading status.
 *
 * <p>Liste kurucuda kopyalanır ve değiştirilemez hâle getirilir; böylece ekran ya da başka bir kod
 * yayınlanmış bir durumu sonradan bozamaz (LiveData'daki değer her zaman tutarlı kalır).
 */
public final class ScoresUiState {

    private final List<ScoreRecord> scores;
    private final boolean loading;
    private final boolean error;
    private final long offset;
    private final boolean hasNextPage;

    /**
     * @param scores  puana göre azalan skor listesi (null ise boş liste kabul edilir)
     * @param loading liste diskten okunuyor mu
     * @param error   liste okunamadı mı (geçici disk hatası)
     */
    public ScoresUiState(List<ScoreRecord> scores, boolean loading, boolean error,
                         long offset, boolean hasNextPage) {
        this.scores = scores == null
                ? Collections.<ScoreRecord>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(scores));
        this.loading = loading;
        this.error = error;
        this.offset = offset;
        this.hasNextPage = hasNextPage;
    }

    /** Yükleniyor durumu: liste boş, hata yok. */
    public static ScoresUiState loading(long offset) {
        return new ScoresUiState(null, true, false, offset, false);
    }

    /** Scores on this page, highest first. */
    public List<ScoreRecord> getScores() {
        return scores;
    }

    public boolean isLoading() {
        return loading;
    }

    /** Liste okunamadıysa true; ekran "liste yüklenemedi" mesajı gösterir. */
    public boolean hasError() {
        return error;
    }

    public long getOffset() {
        return offset;
    }

    public boolean hasNextPage() {
        return hasNextPage;
    }
}
