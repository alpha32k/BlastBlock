package com.blastblock.game.domain.engine;

import com.blastblock.game.domain.model.Board;
import com.blastblock.game.domain.model.Piece;

/**
 * Sürüklenen parçanın bırakılacağı hedefi bulur ("yakına yapışma" / snap).
 *
 * <p>Parmak tam hücre sınırına denk gelmediği için parçanın sol-üst hücresinin konumu kesirli bir
 * tahta koordinatıdır ({@code fracRow, fracCol}). Kurallar:
 * <ol>
 *   <li>Konum en yakın tam sayıya yuvarlanır; parça oraya sığıyorsa o hedef döner ({@code snapped=false}).</li>
 *   <li>Sığmıyorsa, sol-üst başlangıç noktası {@code (r, c)} için Öklid uzaklığı
 *       {@code sqrt((r-fracRow)² + (c-fracCol)²) ≤ radius} olan ve parçanın tamamen sığdığı adaylar
 *       arasından en yakını seçilir ({@code snapped=true}).</li>
 *   <li>Eşit uzaklıkta: {@code previous} geçerli ve eşit uzaklıktaki adaylardan biriyse o seçilir
 *       (hedef parmak titredikçe iki yer arasında zıplamasın diye); yoksa küçük satır, sonra küçük sütun.</li>
 *   <li>Hiç aday yoksa {@link PlacementTarget#invalid()} döner.</li>
 * </ol>
 *
 * <p>Bu metot sürükleme sırasında her karede çağrılabilir; bu yüzden tahta ve parça değiştirilmez,
 * parçanın hücre dizisi yalnızca bir kez alınır ve tarama sırasında yeni dizi üretilmez.
 */
public final class PlacementResolver {

    /** Varsayılan yapışma yarıçapı (hücre cinsinden). */
    public static final float DEFAULT_RADIUS = 1.5f;

    /** Kayan noktalı uzaklıkların "eşit" sayılması için tolerans. */
    private static final double EPSILON = 1e-4;

    private static final int N = Board.SIZE;

    private PlacementResolver() {
    }

    /**
     * @param board    mevcut tahta (değiştirilmez)
     * @param piece    sürüklenen parça (değiştirilmez)
     * @param fracRow  parçanın sol-üst hücresinin kesirli satırı (negatif ya da tahta dışı olabilir)
     * @param fracCol  parçanın sol-üst hücresinin kesirli sütunu
     * @param radius   yakına kaydırma için en büyük uzaklık (hücre cinsinden)
     * @param previous bir önceki karede bulunan hedef; yoksa null ya da geçersiz olabilir
     * @return bulunan hedef ya da {@link PlacementTarget#invalid()}
     */
    public static PlacementTarget resolve(Board board, Piece piece, float fracRow, float fracCol,
                                          float radius, PlacementTarget previous) {
        if (board == null || piece == null) {
            throw new IllegalArgumentException("Tahta ve parça boş olamaz");
        }
        if (!isFinite(fracRow) || !isFinite(fracCol)) {
            return PlacementTarget.invalid();
        }

        // Parça hücrelerini tek sefer al (getCells her çağrıda kopya üretir).
        int[][] cells = piece.getCells();

        // (1) Doğrudan hedef: kesirli konumu en yakın hücreye yuvarla.
        int roundRow = Math.round(fracRow);
        int roundCol = Math.round(fracCol);
        if (fits(board, cells, roundRow, roundCol)) {
            return PlacementTarget.at(roundRow, roundCol, false);
        }
        if (!(radius >= 0f) || Float.isInfinite(radius)) {
            // Negatif/NaN yarıçapta kaydırma yapılmaz; sonsuz yarıçap da kabul edilmez.
            return PlacementTarget.invalid();
        }

        // (2) Taranacak aralık: yarıçap çemberini içine alan kare, parçanın tahtaya taşmayacağı
        // başlangıç noktalarıyla sınırlanır. Böylece en fazla birkaç düzine nokta denenir.
        int minRow = Math.max(0, (int) Math.floor(fracRow - radius));
        int maxRow = Math.min(N - piece.getHeight(), (int) Math.ceil(fracRow + radius));
        int minCol = Math.max(0, (int) Math.floor(fracCol - radius));
        int maxCol = Math.min(N - piece.getWidth(), (int) Math.ceil(fracCol + radius));

        boolean prevUsable = previous != null && previous.isValid();
        boolean found = false;
        double bestDist = Double.MAX_VALUE;
        int bestRow = 0;
        int bestCol = 0;
        boolean previousAmongBest = false;

        // Satır, sonra sütun artan sırada taranır: eşit uzaklıkta ilk bulunan (küçük satır/sütun) kalır.
        for (int r = minRow; r <= maxRow; r++) {
            double dr = r - (double) fracRow;
            for (int c = minCol; c <= maxCol; c++) {
                double dc = c - (double) fracCol;
                double dist = Math.sqrt(dr * dr + dc * dc);
                if (dist > radius + EPSILON) {
                    continue;
                }
                // Uzaklık şimdiki en iyiden açıkça büyükse sığma kontrolüne hiç girme.
                if (found && dist > bestDist + EPSILON) {
                    continue;
                }
                if (!fits(board, cells, r, c)) {
                    continue;
                }
                boolean isPrevious = prevUsable && previous.getRow() == r && previous.getCol() == c;
                if (!found || dist < bestDist - EPSILON) {
                    // Açıkça daha yakın yeni aday.
                    found = true;
                    bestDist = dist;
                    bestRow = r;
                    bestCol = c;
                    previousAmongBest = isPrevious;
                } else if (isPrevious) {
                    // En iyiyle eşit uzaklıkta ve önceki hedef: onu tercih etmek için işaretle.
                    previousAmongBest = true;
                }
            }
        }

        if (!found) {
            return PlacementTarget.invalid();
        }
        if (previousAmongBest) {
            return PlacementTarget.at(previous.getRow(), previous.getCol(), true);
        }
        return PlacementTarget.at(bestRow, bestCol, true);
    }

    /**
     * {@link BoardRules#canPlace} ile aynı kural; ancak hücre dizisini dışarıdan alır ki her
     * denemede parça hücreleri yeniden kopyalanmasın.
     */
    private static boolean fits(Board board, int[][] cells, int row, int col) {
        for (int[] cell : cells) {
            int r = row + cell[0];
            int c = col + cell[1];
            if (!Board.isInside(r, c) || !board.isEmpty(r, c)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
