package com.blastblock.game.domain.engine;

import static com.blastblock.game.domain.TestBoards.board;
import static com.blastblock.game.domain.TestBoards.piece;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.blastblock.game.domain.model.Board;
import com.blastblock.game.domain.model.Piece;

import org.junit.Test;

import java.util.Arrays;

/** PlacementResolver: doğrudan hedef, yakına kaydırma, eşitlik, kenarlar ve uygun yer olmaması. */
public class PlacementResolverTest {

    private static final float R = PlacementResolver.DEFAULT_RADIUS;

    /** Yalnızca (3,3) hücresi dolu tahta. */
    private static Board centerFilled() {
        return board(
                "........",
                "........",
                "........",
                "...1....",
                "........",
                "........",
                "........",
                "........");
    }

    @Test
    public void dogrudanGecerliHedef_kaydirilmaz() {
        PlacementTarget t = PlacementResolver.resolve(Board.empty(), piece("single"), 2.3f, 4.4f, R, null);
        assertEquals(PlacementTarget.at(2, 4, false), t);
        assertFalse(t.isSnapped());
    }

    @Test
    public void doluHedef_enYakinBosYereKayar() {
        // Yuvarlanmış hedef (3,3) dolu. Adaylar: (4,3)≈0.81, (3,4)≈0.92, (3,2)≈1.12, (2,3)≈1.20.
        PlacementTarget t = PlacementResolver.resolve(centerFilled(), piece("single"), 3.2f, 3.1f, R, null);
        assertTrue(t.isValid());
        assertTrue(t.isSnapped());
        assertEquals(4, t.getRow());
        assertEquals(3, t.getCol());
    }

    @Test
    public void yaricapDisindakiAday_gecersiz() {
        // Ortadaki 5x5 alan dolu; (3,3) çevresindeki en yakın boş yer 3 hücre uzakta.
        Board b = board(
                "........",
                ".11111..",
                ".11111..",
                ".11111..",
                ".11111..",
                ".11111..",
                "........",
                "........");
        assertFalse(PlacementResolver.resolve(b, piece("single"), 3f, 3f, R, null).isValid());
        // Yarıçap büyütülünce aday bulunur: (0,3),(3,0),(6,3) eşit (3.0) → küçük satır (0,3).
        assertEquals(PlacementTarget.at(0, 3, true),
                PlacementResolver.resolve(b, piece("single"), 3f, 3f, 3f, null));
    }

    @Test
    public void esitUzaklik_oncekiYokken_kucukSatirSonraSutun() {
        // (3,3) dolu; dört komşu da tam 1.0 uzaklıkta → en küçük satır (2,3).
        assertEquals(PlacementTarget.at(2, 3, true),
                PlacementResolver.resolve(centerFilled(), piece("single"), 3f, 3f, R, null));
        // Geçersiz önceki hedef yok sayılır.
        assertEquals(PlacementTarget.at(2, 3, true),
                PlacementResolver.resolve(centerFilled(), piece("single"), 3f, 3f, R, PlacementTarget.invalid()));
    }

    @Test
    public void esitUzaklik_oncekiAdaylardaysa_tercihEdilir() {
        assertEquals(PlacementTarget.at(4, 3, true),
                PlacementResolver.resolve(centerFilled(), piece("single"), 3f, 3f, R, PlacementTarget.at(4, 3, true)));
        // Önceki hedef kaydırılmamış (snapped=false) olsa da yeni sonuç kaydırılmış sayılır.
        assertEquals(PlacementTarget.at(3, 4, true),
                PlacementResolver.resolve(centerFilled(), piece("single"), 3f, 3f, R, PlacementTarget.at(3, 4, false)));
    }

    @Test
    public void esitUzaklik_oncekiAdayKumesindeDegilse_yokSayilir() {
        // (5,5) eşit uzaklıktaki adaylardan değil → kural: küçük satır.
        assertEquals(PlacementTarget.at(2, 3, true),
                PlacementResolver.resolve(centerFilled(), piece("single"), 3f, 3f, R, PlacementTarget.at(5, 5, true)));
        // Önceki hedef aday ama en yakın değil: (3,2) 1.12 uzaklıkta, en iyi (4,3) 0.81 → (4,3).
        assertEquals(PlacementTarget.at(4, 3, true),
                PlacementResolver.resolve(centerFilled(), piece("single"), 3.2f, 3.1f, R, PlacementTarget.at(3, 2, true)));
    }

    @Test
    public void esitUzaklik_kesirliKonumda() {
        // (3,3) ve (3,4) dolu; (3, 3.5) → yuvarlama (3,4) dolu. Dört köşe aday eşit (≈1.118).
        Board b = board(
                "........",
                "........",
                "........",
                "...11...",
                "........",
                "........",
                "........",
                "........");
        assertEquals(PlacementTarget.at(2, 3, true),
                PlacementResolver.resolve(b, piece("single"), 3f, 3.5f, R, null));
        assertEquals(PlacementTarget.at(4, 4, true),
                PlacementResolver.resolve(b, piece("single"), 3f, 3.5f, R, PlacementTarget.at(4, 4, true)));
    }

    @Test
    public void solUstKenar_negatifKesirliKonum() {
        // (-0.4, -0.3) yuvarlanınca (0,0): doğrudan geçerli.
        assertEquals(PlacementTarget.at(0, 0, false),
                PlacementResolver.resolve(Board.empty(), piece("single"), -0.4f, -0.3f, R, null));
        // (-0.8, -0.6) yuvarlanınca (-1,-1): tahta dışı; (0,0) tam 1.0 uzakta → kaydırılır.
        assertEquals(PlacementTarget.at(0, 0, true),
                PlacementResolver.resolve(Board.empty(), piece("single"), -0.8f, -0.6f, R, null));
        // Çok uzakta: aday yok.
        assertFalse(PlacementResolver.resolve(Board.empty(), piece("single"), -3f, -3f, R, null).isValid());
    }

    @Test
    public void sagVeAltKenar_tasmaGeriCekilir() {
        // Yatay 5'lik çubuk (2,4)'e konursa sağdan taşar; (2,3) 1.4 uzakta.
        assertEquals(PlacementTarget.at(2, 3, true),
                PlacementResolver.resolve(Board.empty(), piece("bar5_h"), 2f, 4.4f, R, null));
        // Dikey 5'lik çubuk (4,1)'e konursa alttan taşar; (3,1) 1.3 uzakta.
        assertEquals(PlacementTarget.at(3, 1, true),
                PlacementResolver.resolve(Board.empty(), piece("bar5_v"), 4.3f, 1f, R, null));
        // Tahtanın çok dışında: aday yok.
        assertFalse(PlacementResolver.resolve(Board.empty(), piece("single"), 10f, 10f, R, null).isValid());
    }

    @Test
    public void besliCubuk_doluHucreninYanindanKayar() {
        // Satır 2'de (2,2) dolu. Yatay çubuk (2,0)'a konamaz; (1,0) ve (3,0) 1.0 uzakta → küçük satır.
        Board b = board(
                "........",
                "........",
                "..1.....",
                "........",
                "........",
                "........",
                "........",
                "........");
        assertEquals(PlacementTarget.at(1, 0, true),
                PlacementResolver.resolve(b, piece("bar5_h"), 2f, 0f, R, null));
        assertEquals(PlacementTarget.at(3, 0, true),
                PlacementResolver.resolve(b, piece("bar5_h"), 2f, 0f, R, PlacementTarget.at(3, 0, false)));
    }

    @Test
    public void ucUcKare_doluHucreyiAtlar() {
        // (2,2) dolu. 3x3 kare (2,1)'e konursa (2,2)'yi kapsar; (3,1) 0.6 uzakta ve sığar.
        Board b = board(
                "........",
                "........",
                "..1.....",
                "........",
                "........",
                "........",
                "........",
                "........");
        assertEquals(PlacementTarget.at(3, 1, true),
                PlacementResolver.resolve(b, piece("square3"), 2.4f, 1f, R, null));
        // Boş tahtada sağ alt köşeye taşan kare (5,5)'e çekilir: (5.6,5.6) → yuvarlama (6,6) taşar.
        assertEquals(PlacementTarget.at(5, 5, true),
                PlacementResolver.resolve(Board.empty(), piece("square3"), 5.6f, 5.6f, R, null));
    }

    @Test
    public void tamamenDoluTahta_gecersiz() {
        int[] cells = new int[Board.SIZE * Board.SIZE];
        Arrays.fill(cells, 2);
        Board full = new Board(cells);
        assertFalse(PlacementResolver.resolve(full, piece("single"), 3f, 3f, R, null).isValid());
        assertFalse(PlacementResolver.resolve(full, piece("single"), 3f, 3f, 20f, PlacementTarget.at(3, 3, true)).isValid());
    }

    @Test
    public void tanimsizKonumVeNegatifYaricap() {
        assertFalse(PlacementResolver.resolve(Board.empty(), piece("single"), Float.NaN, 1f, R, null).isValid());
        // Negatif yarıçap: yalnızca doğrudan hedef denenir.
        assertEquals(PlacementTarget.at(1, 1, false),
                PlacementResolver.resolve(Board.empty(), piece("single"), 1f, 1f, -1f, null));
        assertFalse(PlacementResolver.resolve(centerFilled(), piece("single"), 3f, 3f, -1f, null).isValid());
    }

    @Test
    public void tahtaVeParcaDegismez() {
        Board b = centerFilled();
        int[] before = b.getCells();
        Piece p = piece("square3");
        Piece pCopy = piece("square3");
        PlacementResolver.resolve(b, p, 3f, 3f, R, null);
        PlacementResolver.resolve(b, p, 2.6f, 4.2f, 3f, PlacementTarget.at(0, 0, true));
        assertTrue(Arrays.equals(before, b.getCells()));
        assertEquals(pCopy, p);
    }

    @Test
    public void hedefEsitligi() {
        assertEquals(PlacementTarget.invalid(), PlacementTarget.invalid());
        assertEquals(PlacementTarget.at(1, 2, true), PlacementTarget.at(1, 2, true));
        assertEquals(PlacementTarget.at(1, 2, true).hashCode(), PlacementTarget.at(1, 2, true).hashCode());
        assertNotEquals(PlacementTarget.at(1, 2, true), PlacementTarget.at(1, 2, false));
        assertNotEquals(PlacementTarget.at(0, 0, false), PlacementTarget.invalid());
    }
}
