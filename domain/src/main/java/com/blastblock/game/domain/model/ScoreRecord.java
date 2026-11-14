package com.blastblock.game.domain.model;

/**
 * Biten bir oyunun skor listesindeki kaydı: puan ve oyunun bittiği an.
 *
 * <p>Sınıf değişmezdir. Bitiş anı, 1970'ten bu yana geçen milisaniye olarak saklanır; ekranda
 * tarihe çevrilmesi sunum katmanının işidir.
 */
public final class ScoreRecord {

    private final long score;
    private final long finishedAtMillis;
    private final boolean daily;

    /**
     * @param score            0 veya daha büyük puan
     * @param finishedAtMillis oyunun bittiği an (epoch milisaniye)
     * @throws IllegalArgumentException puan negatifse
     */
    public ScoreRecord(long score, long finishedAtMillis) {
        this(score, finishedAtMillis, false);
    }

    public ScoreRecord(long score, long finishedAtMillis, boolean daily) {
        if (score < 0) {
            throw new IllegalArgumentException("Puan negatif olamaz: " + score);
        }
        this.score = score;
        this.finishedAtMillis = finishedAtMillis;
        this.daily = daily;
    }

    public long getScore() {
        return score;
    }

    public long getFinishedAtMillis() {
        return finishedAtMillis;
    }

    public boolean isDaily() {
        return daily;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScoreRecord)) return false;
        ScoreRecord other = (ScoreRecord) o;
        return score == other.score && finishedAtMillis == other.finishedAtMillis
                && daily == other.daily;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * Long.hashCode(score) + Long.hashCode(finishedAtMillis))
                + Boolean.hashCode(daily);
    }

    @Override
    public String toString() {
        return "ScoreRecord{puan=" + score + ", bitiş=" + finishedAtMillis + "}";
    }
}
