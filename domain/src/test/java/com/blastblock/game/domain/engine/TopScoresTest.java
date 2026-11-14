package com.blastblock.game.domain.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import com.blastblock.game.domain.model.ScoreRecord;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** TopScores: ekleme sırası, 10'a kırpma, eşit puan kuralı, normalize ve değiştirilemezlik. */
public class TopScoresTest {

    private static ScoreRecord rec(long score, long time) {
        return new ScoreRecord(score, time);
    }

    /** Puanları 1000, 900, ..., 100 olan 10 kayıt (sıralı). */
    private static List<ScoreRecord> fullList() {
        List<ScoreRecord> list = new ArrayList<>();
        for (int i = 10; i >= 1; i--) {
            list.add(rec(i * 100L, i));
        }
        return list;
    }

    @Test
    public void bosListeyeEkleme_sira1() {
        ScoreRecord r = rec(250, 1);
        TopScores.Result result = TopScores.insert(Collections.<ScoreRecord>emptyList(), r);
        assertEquals(1, result.getRank());
        assertEquals(Collections.singletonList(r), result.getScores());
    }

    @Test
    public void onbirinciEnDusukKayit_listeyeGiremez() {
        List<ScoreRecord> current = fullList();
        TopScores.Result result = TopScores.insert(current, rec(50, 99));
        assertEquals(0, result.getRank());
        assertEquals(TopScores.MAX_ENTRIES, result.getScores().size());
        assertEquals(current, result.getScores());
    }

    @Test
    public void ortayaGirenKayit_enDusukElenir() {
        ScoreRecord r = rec(550, 99);
        TopScores.Result result = TopScores.insert(fullList(), r);
        assertEquals(6, result.getRank());
        assertEquals(10, result.getScores().size());
        assertSame(r, result.getScores().get(5));
        assertEquals(200, result.getScores().get(9).getScore());
    }

    @Test
    public void listeDolukenEnDusukPuanaEsitYeniKayit_giremez() {
        // 100 puanlı eski kayıt önde kalır; yeni 100 puanlı kayıt 11. olur.
        assertEquals(0, TopScores.insert(fullList(), rec(100, 99)).getRank());
    }

    @Test
    public void esitPuan_eskiKayitOnde_yeniKayitSonra() {
        List<ScoreRecord> current = Arrays.asList(rec(500, 10), rec(500, 20));
        // Daha yeni anda biten kayıt eşit puanlıların sonuna gelir.
        ScoreRecord newer = rec(500, 30);
        TopScores.Result r1 = TopScores.insert(current, newer);
        assertEquals(3, r1.getRank());
        assertEquals(Arrays.asList(rec(500, 10), rec(500, 20), newer), r1.getScores());
        // Aynı puan ve aynı anda biten kayıt da eşitlerin arkasına gelir.
        assertEquals(2, TopScores.insert(current, rec(500, 10)).getRank());
        // Daha eski anda bitmiş kayıt (saat geri alınmış gibi) öne geçer.
        assertEquals(1, TopScores.insert(current, rec(500, 5)).getRank());
        // Daha yüksek puan her zaman önde.
        assertEquals(1, TopScores.insert(current, rec(501, 99)).getRank());
    }

    @Test
    public void girdiListesiDegismez() {
        List<ScoreRecord> current = new ArrayList<>(Arrays.asList(rec(100, 1), rec(300, 2)));
        List<ScoreRecord> snapshot = new ArrayList<>(current);
        TopScores.insert(current, rec(200, 3));
        TopScores.normalize(current);
        assertEquals(snapshot, current);
    }

    @Test
    public void sirasizGirdi_eklemedenOnceSiralanir() {
        List<ScoreRecord> current = Arrays.asList(rec(100, 1), rec(300, 2), rec(200, 3));
        TopScores.Result result = TopScores.insert(current, rec(250, 4));
        assertEquals(2, result.getRank());
        assertEquals(Arrays.asList(rec(300, 2), rec(250, 4), rec(200, 3), rec(100, 1)), result.getScores());
    }

    @Test
    public void normalize_siralarVeKirpar() {
        List<ScoreRecord> messy = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            messy.add(rec(i * 10L, 100 - i));
        }
        messy.add(rec(120, 1)); // 120 puanlı daha eski kayıt
        Collections.shuffle(messy, new java.util.Random(7));

        List<ScoreRecord> result = TopScores.normalize(messy);
        assertEquals(10, result.size());
        assertEquals(rec(120, 1), result.get(0));   // eşit puanda eski önde
        assertEquals(rec(120, 88), result.get(1));
        assertEquals(rec(110, 89), result.get(2));
        assertEquals(40, result.get(9).getScore());
        for (int i = 1; i < result.size(); i++) {
            if (result.get(i - 1).getScore() < result.get(i).getScore()) {
                fail("Liste azalan sırada değil");
            }
        }
        assertEquals(Collections.<ScoreRecord>emptyList(), TopScores.normalize(new ArrayList<ScoreRecord>()));
    }

    @Test
    public void sonucListeleriDegistirilemez() {
        assertUnmodifiable(TopScores.insert(fullList(), rec(5000, 1)).getScores());
        assertUnmodifiable(TopScores.insert(new ArrayList<ScoreRecord>(), rec(1, 1)).getScores());
        assertUnmodifiable(TopScores.normalize(fullList()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullKayit_reddedilir() {
        TopScores.insert(new ArrayList<ScoreRecord>(), null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void negatifPuan_reddedilir() {
        new ScoreRecord(-1, 0);
    }

    @Test
    public void skorKaydiEsitligi() {
        assertEquals(rec(10, 20), rec(10, 20));
        assertEquals(rec(10, 20).hashCode(), rec(10, 20).hashCode());
        assertNotEquals(rec(10, 20), rec(10, 21));
        assertNotEquals(rec(10, 20), rec(11, 20));
    }

    private static void assertUnmodifiable(List<ScoreRecord> list) {
        try {
            list.add(rec(1, 1));
            fail("Liste değiştirilebilir olmamalı");
        } catch (UnsupportedOperationException expected) {
            // beklenen
        }
    }
}
