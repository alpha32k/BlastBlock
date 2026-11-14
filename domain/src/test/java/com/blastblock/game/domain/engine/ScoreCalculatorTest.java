package com.blastblock.game.domain.engine;

import org.junit.Test;

import static org.junit.Assert.*;

public class ScoreCalculatorTest {

    @Test
    public void placementIsTenPerCell() {
        assertEquals(40, ScoreCalculator.placementPoints(4));
        assertEquals(0, ScoreCalculator.placementPoints(0));
    }

    @Test
    public void clearPointsFormula() {
        assertEquals(0, ScoreCalculator.clearPoints(0, 3));
        assertEquals(100, ScoreCalculator.clearPoints(1, 1));
        assertEquals(250, ScoreCalculator.clearPoints(2, 1));
        assertEquals(500, ScoreCalculator.clearPoints(2, 2));
        assertEquals(400 * 5, ScoreCalculator.clearPoints(3, 5));
    }

    @Test
    public void streakGrowsAndCapsAtFive() {
        assertEquals(1, ScoreCalculator.nextStreak(0, 1));
        assertEquals(3, ScoreCalculator.nextStreak(2, 4));
        assertEquals(5, ScoreCalculator.nextStreak(5, 1));
        assertEquals(0, ScoreCalculator.nextStreak(4, 0));
    }
}
