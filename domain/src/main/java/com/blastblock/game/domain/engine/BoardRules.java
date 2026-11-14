package com.blastblock.game.domain.engine;

import com.blastblock.game.domain.model.Board;
import com.blastblock.game.domain.model.Piece;

import java.util.ArrayList;
import java.util.List;

/**
 * Tahta üzerindeki temel kurallar: sığma kontrolü, yerleştirme, dolu çizgi bulma ve silme.
 *
 * <p>Motor ({@link GameEngine}) ve adil üretici ({@link FairPieceGenerator}) aynı kuralları
 * kullansın diye ortak bir yardımcı sınıfta toplanmıştır. Paket içi (package-private) bir
 * sınıftır; dış katmanlar yalnızca {@link GameEngine} üzerinden çalışır.
 */
final class BoardRules {

    private static final int N = Board.SIZE;

    private BoardRules() {
    }

    /**
     * Parçanın (0,0) göreli hücresi tahtadaki (row, col) noktasına konursa sığar mı?
     * Parçanın her hücresi hem tahta içinde hem de boş bir hücreye düşmelidir.
     */
    static boolean canPlace(Board board, Piece piece, int row, int col) {
        for (int[] cell : piece.getCells()) {
            int r = row + cell[0];
            int c = col + cell[1];
            if (!Board.isInside(r, c) || !board.isEmpty(r, c)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Parça tahtada herhangi bir yere sığıyor mu? Tüm başlangıç noktaları denenir.
     * Parça tahtadan taşmasın diye yalnızca (SIZE − yükseklik) × (SIZE − genişlik) aralığı taranır.
     */
    static boolean fitsAnywhere(Board board, Piece piece) {
        for (int row = 0; row <= N - piece.getHeight(); row++) {
            for (int col = 0; col <= N - piece.getWidth(); col++) {
                if (canPlace(board, piece, row, col)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Parçayı tahtaya koyar ve yeni tahtayı döndürür. Önce {@link #canPlace} ile kontrol edilmelidir. */
    static Board place(Board board, Piece piece, int row, int col) {
        int[] cells = board.getCells();
        for (int[] cell : piece.getCells()) {
            cells[(row + cell[0]) * N + (col + cell[1])] = piece.getColorId();
        }
        return new Board(cells);
    }

    /** Tamamen dolu satırların numaraları (artan sırada). */
    static int[] fullRows(Board board) {
        List<Integer> rows = new ArrayList<>();
        for (int r = 0; r < N; r++) {
            boolean full = true;
            for (int c = 0; c < N && full; c++) {
                full = !board.isEmpty(r, c);
            }
            if (full) {
                rows.add(r);
            }
        }
        return toArray(rows);
    }

    /** Tamamen dolu sütunların numaraları (artan sırada). */
    static int[] fullColumns(Board board) {
        List<Integer> cols = new ArrayList<>();
        for (int c = 0; c < N; c++) {
            boolean full = true;
            for (int r = 0; r < N && full; r++) {
                full = !board.isEmpty(r, c);
            }
            if (full) {
                cols.add(c);
            }
        }
        return toArray(cols);
    }

    /**
     * Verilen satır ve sütunları birlikte siler.
     *
     * <p>Satırlar ve sütunlar aynı tahtadan önceden bulunmuş olmalıdır. Silme tek geçişte yapılır:
     * bir hücre silinecek bir satırda VEYA silinecek bir sütunda ise boşaltılır. Bu yüzden satır ile
     * sütunun kesiştiği hücre yalnızca bir kez silinir, ama satır ve sütun ayrı çizgi olarak sayılır.
     * Önce satırları silip sonra sütunları yeniden aramak yanlış olurdu: kesişim hücresi boşalınca
     * sütun artık dolu görünmezdi.
     */
    static Board clearLines(Board board, int[] rows, int[] columns) {
        int[] cells = board.getCells();
        for (int r : rows) {
            for (int c = 0; c < N; c++) {
                cells[r * N + c] = 0;
            }
        }
        for (int c : columns) {
            for (int r = 0; r < N; r++) {
                cells[r * N + c] = 0;
            }
        }
        return new Board(cells);
    }

    private static int[] toArray(List<Integer> list) {
        int[] result = new int[list.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = list.get(i);
        }
        return result;
    }
}
