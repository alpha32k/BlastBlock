package com.blastblock.game.domain.engine;

import com.blastblock.game.domain.catalog.PieceCatalog;
import com.blastblock.game.domain.model.Board;
import com.blastblock.game.domain.model.GameState;
import com.blastblock.game.domain.model.Piece;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Tepsiye gelecek yeni üç parçayı "adil" biçimde üretir.
 *
 * <p>Tamamen rastgele üretimde, tahta kalabalıkken oyuncuya hiçbirinin sığmadığı üç parça gelebilir
 * ve oyun haksız biçimde biter. Bunu azaltmak için:
 * <ol>
 *   <li>Katalogdaki her şekil için "tahtada en az bir yere sığıyor mu?" kontrol edilir.</li>
 *   <li>Birinci parça bu sığan şekiller arasından rastgele seçilir (hiçbiri sığmıyorsa tüm katalogdan).</li>
 *   <li>Diğer iki parça tüm katalogdan rastgele seçilir; böylece oyun çok kolaylaşmaz.</li>
 *   <li>Üç parça yuvalara karıştırılarak yerleştirilir; sığan parça hep aynı yuvada durmaz.</li>
 *   <li>Her parçanın rengi 1..{@link PieceCatalog#COLOR_COUNT} arasından rastgele seçilir.</li>
 * </ol>
 * Rastgele sayı üreteci dışarıdan verilir (bağımlılık enjeksiyonu): testlerde sabit tohumlu
 * {@link Random} vererek her seferinde aynı sonucu elde edebiliriz.
 */
public final class FairPieceGenerator {

    private final Random random;

    public FairPieceGenerator(Random random) {
        if (random == null) {
            throw new IllegalArgumentException("Random null olamaz");
        }
        this.random = random;
    }

    /** Verilen tahtaya göre yeni üç parçalık tepsi üretir. */
    public Piece[] nextTray(Board board) {
        if (board == null) {
            throw new IllegalArgumentException("Tahta null olamaz");
        }
        List<String> allIds = PieceCatalog.ids();

        // 1. adım: şu an tahtaya sığan şekilleri topla (renk sığmayı etkilemez, 1 kullanılır).
        List<String> fitting = new ArrayList<>();
        for (String id : allIds) {
            if (BoardRules.fitsAnywhere(board, PieceCatalog.create(id, 1))) {
                fitting.add(id);
            }
        }

        // 2. ve 3. adım: bir sığan + iki genel parça.
        List<Piece> pieces = new ArrayList<>(GameState.TRAY_SIZE);
        List<String> firstPool = fitting.isEmpty() ? allIds : fitting;
        pieces.add(randomPiece(firstPool));
        for (int i = 1; i < GameState.TRAY_SIZE; i++) {
            pieces.add(randomPiece(allIds));
        }

        // 4. adım: yuvaları karıştır.
        Collections.shuffle(pieces, random);
        return pieces.toArray(new Piece[0]);
    }

    private Piece randomPiece(List<String> pool) {
        String id = pool.get(random.nextInt(pool.size()));
        int color = 1 + random.nextInt(PieceCatalog.COLOR_COUNT);
        return PieceCatalog.create(id, color);
    }
}
