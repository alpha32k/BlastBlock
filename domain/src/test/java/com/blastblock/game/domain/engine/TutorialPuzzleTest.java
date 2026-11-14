package com.blastblock.game.domain.engine;

import com.blastblock.game.domain.model.Board;
import org.junit.Test;
import java.util.Random;
import static org.junit.Assert.*;

public class TutorialPuzzleTest {
    @Test
    public void highlightedMoveIsTheOnlyAcceptedDropAndClearsTheLine() {
        TutorialPuzzle tutorial = new TutorialPuzzle(new GameEngine(new Random(4)));
        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                assertEquals(row == TutorialPuzzle.TARGET_ROW && col == TutorialPuzzle.TARGET_COL,
                        tutorial.preview(TutorialPuzzle.SLOT, row, col).isValid());
            }
        }
        MoveResult result = tutorial.place(TutorialPuzzle.SLOT, TutorialPuzzle.TARGET_ROW, TutorialPuzzle.TARGET_COL);
        assertTrue(result.isPlaced());
        assertEquals(1, result.getLinesCleared());
        assertEquals(0, result.getState().getBoard().getCell(TutorialPuzzle.TARGET_ROW, 0));
        assertFalse(tutorial.place(TutorialPuzzle.SLOT, TutorialPuzzle.TARGET_ROW, TutorialPuzzle.TARGET_COL).isPlaced());
    }
}
