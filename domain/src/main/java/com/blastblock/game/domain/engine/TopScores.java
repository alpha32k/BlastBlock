package com.blastblock.game.domain.engine;

import com.blastblock.game.domain.model.ScoreRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * En iyi 10 skor listesinin kuralları: sıralama, kırpma ve yeni kaydın yerini bulma.
 *
 * <p>Sıralama: puana göre azalan; eşit puanda bitiş anı küçük olan (daha eski) önde.
 * Aynı puan ve aynı anda bitmiş kayıtlarda yeni eklenen sona gelir; bu yüzden kararlı
 * (stable) sıralama kullanılır. Girdi listeleri hiçbir zaman değiştirilmez.
 */
public final class TopScores {

    /** Listede tutulan en fazla kayıt sayısı. */
    public static final int MAX_ENTRIES = 10;

    /** Puana göre azalan, eşit puanda eski kayıt önde. */
    private static final Comparator<ScoreRecord> ORDER = new Comparator<ScoreRecord>() {
        @Override
        public int compare(ScoreRecord a, ScoreRecord b) {
            int byScore = Long.compare(b.getScore(), a.getScore());
            if (byScore != 0) {
                return byScore;
            }
            return Long.compare(a.getFinishedAtMillis(), b.getFinishedAtMillis());
        }
    };

    private TopScores() {
    }

    /** {@link #insert} sonucunu taşır: yeni liste ve eklenen kaydın sırası. */
    public static final class Result {
        private final List<ScoreRecord> scores;
        private final int rank;

        private Result(List<ScoreRecord> scores, int rank) {
            this.scores = scores;
            this.rank = rank;
        }

        /** Değiştirilemez, sıralı, en fazla 10 kayıtlık liste. */
        public List<ScoreRecord> getScores() {
            return scores;
        }

        /** Eklenen kaydın 1 tabanlı sırası (1..10); listeye giremediyse 0. */
        public int getRank() {
            return rank;
        }
    }

    /**
     * Kaydı listeye ekler.
     *
     * @param current mevcut liste (değiştirilmez; sırasız olsa da önce sıralanır)
     * @param record  eklenecek kayıt
     * @throws IllegalArgumentException liste ya da kayıt null ise, veya listede null eleman varsa
     */
    public static Result insert(List<ScoreRecord> current, ScoreRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("Kayıt boş olamaz");
        }
        List<ScoreRecord> all = copyOf(current);
        // Önce mevcut listeyi kendi içinde sırala, sonra yeni kaydı en sona ekleyip yeniden sırala.
        // Kararlı sıralama sayesinde yeni kayıt, kendisiyle tamamen eşit olan eskilerin arkasında kalır.
        Collections.sort(all, ORDER);
        all.add(record);
        Collections.sort(all, ORDER);

        // Kaydın yerini kimlikle (==) ara: aynı değerde başka kayıtlar olabilir.
        int index = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i) == record) {
                index = i;
                break;
            }
        }
        int rank = index < MAX_ENTRIES ? index + 1 : 0;
        return new Result(trim(all), rank);
    }

    /**
     * Listeyi sıralar ve ilk 10 kaydı değiştirilemez bir liste olarak döndürür.
     * Örneğin diskten okunan, elle bozulmuş bir liste bu metottan geçirilerek düzeltilir.
     *
     * @throws IllegalArgumentException liste null ise ya da null eleman içeriyorsa
     */
    public static List<ScoreRecord> normalize(List<ScoreRecord> scores) {
        List<ScoreRecord> all = copyOf(scores);
        Collections.sort(all, ORDER);
        return trim(all);
    }

    private static List<ScoreRecord> copyOf(List<ScoreRecord> list) {
        if (list == null) {
            throw new IllegalArgumentException("Liste boş (null) olamaz");
        }
        List<ScoreRecord> copy = new ArrayList<>(list.size() + 1);
        for (ScoreRecord r : list) {
            if (r == null) {
                throw new IllegalArgumentException("Listede boş kayıt olamaz");
            }
            copy.add(r);
        }
        return copy;
    }

    private static List<ScoreRecord> trim(List<ScoreRecord> sorted) {
        int size = Math.min(sorted.size(), MAX_ENTRIES);
        return Collections.unmodifiableList(new ArrayList<>(sorted.subList(0, size)));
    }
}
