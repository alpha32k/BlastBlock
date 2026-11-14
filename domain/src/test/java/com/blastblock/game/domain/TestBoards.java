package com.blastblock.game.domain;

import com.blastblock.game.domain.catalog.PieceCatalog;
import com.blastblock.game.domain.model.Board;
import com.blastblock.game.domain.model.GameState;
import com.blastblock.game.domain.model.Piece;

/** Testlerde belirli tahta ve durumları kolayca kurmak için yardımcılar. */
public final class TestBoards {

    private TestBoards() {
    }

    /** Metin çiziminden tahta kurar: 8 satır, her satır 8 karakter; '.' boş, rakam renk. */
    public static Board board(String... rows) {
        if (rows.length != Board.SIZE) {
            throw new IllegalArgumentException("8 satır gerekli");
        }
        int[] cells = new int[Board.SIZE * Board.SIZE];
        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                char ch = rows[r].charAt(c);
                cells[r * Board.SIZE + c] = ch == '.' ? 0 : ch - '0';
            }
        }
        return new Board(cells);
    }

    /** Kimliklerden tepsi kurar; null kullanılmış yuva demektir. Renk hep 1. */
    public static Piece[] tray(String a, String b, String c) {
        return new Piece[]{piece(a), piece(b), piece(c)};
    }

    public static Piece piece(String id) {
        return id == null ? null : PieceCatalog.create(id, 1);
    }

    public static GameState state(Board board, Piece[] tray, long score, int streak) {
        return new GameState(board, tray, score, streak, false);
    }
}
