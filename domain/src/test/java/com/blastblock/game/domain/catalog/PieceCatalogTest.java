package com.blastblock.game.domain.catalog;

import com.blastblock.game.domain.model.Piece;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.*;

public class PieceCatalogTest {

    @Test
    public void idsAreUniqueAndCreatable() {
        List<String> ids = PieceCatalog.ids();
        assertTrue("Katalog en az 25 şekil içermeli", ids.size() >= 25);
        assertEquals("Kimlikler benzersiz olmalı", ids.size(), new HashSet<>(ids).size());
        for (String id : ids) {
            Piece p = PieceCatalog.create(id, 3);
            assertEquals(id, p.getShapeId());
            assertEquals(3, p.getColorId());
            assertTrue(p.getWidth() <= 5 && p.getHeight() <= 5);
        }
    }

    @Test
    public void idsOrderIsStable() {
        assertEquals("single", PieceCatalog.ids().get(0));
        assertEquals(PieceCatalog.ids(), PieceCatalog.ids());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void idsListIsUnmodifiable() {
        PieceCatalog.ids().add("hile");
    }

    @Test
    public void knownShapesHaveExpectedCells() {
        assertEquals(1, PieceCatalog.create("single", 1).getCellCount());
        Piece bar5v = PieceCatalog.create("bar5_v", 1);
        assertEquals(1, bar5v.getWidth());
        assertEquals(5, bar5v.getHeight());
        assertEquals(9, PieceCatalog.create("square3", 1).getCellCount());
        assertArrayEquals(new int[][]{{0, 0}, {1, 0}, {1, 1}},
                PieceCatalog.create("corner3_a", 1).getCells());
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownIdThrows() {
        PieceCatalog.create("yok_boyle", 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void badColorThrows() {
        PieceCatalog.create("single", PieceCatalog.COLOR_COUNT + 1);
    }
}
