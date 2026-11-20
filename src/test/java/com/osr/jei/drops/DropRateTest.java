package com.osr.jei.drops;

import com.osr.jei.model.DropEntry;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DropRateTest {

    @Test
    public void normalizesFractionsWordsRangesAndQuantities() {
        DropRate plain = DropRate.parse("1/128", "1");
        assertEquals("1/128", plain.display);
        assertEquals("1/128", plain.original);
        assertEquals(1.0 / 128.0, plain.probability, 1e-12);

        DropRate scaled = DropRate.parse("2 \u00d7 1/128", "1");
        assertEquals("1/64", scaled.display);
        assertEquals(2.0 / 128.0, scaled.probability, 1e-12);

        DropRate reduced = DropRate.parse("3/128", "1");
        assertEquals("1/42.67", reduced.display);

        DropRate always = DropRate.parse("Always", "1");
        assertEquals("1/1", always.display);
        assertEquals(1.0, always.probability, 0);

        DropRate rare = DropRate.parse("Rare", "1");
        assertEquals("Rare", rare.display);
        assertNull(rare.probability);
        assertEquals("Rare", rare.original);

        DropRate range = DropRate.parse("1/100 \u2013 1/200", "1");
        assertEquals("1/100\u20131/200", range.display);
        assertEquals((0.01 + 0.005) / 2.0, range.probability, 1e-12);

        DropRate approx = DropRate.parse("~1/5,000", "1");
        assertEquals("1/5000", approx.display);

        DropRate percent = DropRate.parse("5%", "1");
        assertEquals("1/20", percent.display);

        DropRate quantity = DropRate.parse("1-3", "1");
        assertEquals("1-3", quantity.display);
        assertNull(quantity.probability);

        DropRate withQuantity = DropRate.parse("1/128", "1-3");
        assertEquals("1/128", withQuantity.display);
        assertEquals(1.0 / 128.0, withQuantity.probability, 1e-12);

        DropRate prefixed = DropRate.parse("1\u20133 \u00d7 1/42", "1");
        assertEquals("1/42", prefixed.display);
    }

    @Test
    public void sortsByRateCombatAndName() {
        DropEntry rare = drop("Zulrah", "725", "1", "1/512");
        DropEntry always = drop("Goblin", "2", "1", "Always");
        DropEntry word = drop("Mystery", "", "1", "Rare");
        DropEntry ranged = drop("Imp", "2-13", "1-3", "1/100");
        DropEntry missing = drop("Shade", "?", "1", "1/8");

        List<DropEntry> byRate = DropSorter.ordered(Arrays.asList(rare, word, always, missing), DropSort.RATE);
        assertEquals("Goblin", byRate.get(0).getSource());
        assertEquals("Shade", byRate.get(1).getSource());
        assertEquals("Zulrah", byRate.get(2).getSource());
        assertEquals("Mystery", byRate.get(3).getSource());

        List<DropEntry> byCombat = DropSorter.ordered(Arrays.asList(rare, ranged, missing, always), DropSort.COMBAT);
        assertEquals("Goblin", byCombat.get(0).getSource());
        assertEquals("Imp", byCombat.get(1).getSource());
        assertEquals("Zulrah", byCombat.get(2).getSource());
        assertEquals("Shade", byCombat.get(3).getSource());
        assertTrue(DropSorter.combatRank("?") == Integer.MAX_VALUE);
        assertTrue(DropSorter.combatRank("") == Integer.MAX_VALUE);
        assertEquals(2, DropSorter.combatRank("2-13"));

        List<DropEntry> byName = DropSorter.ordered(Arrays.asList(rare, always, ranged), DropSort.NAME);
        assertEquals("Goblin", byName.get(0).getSource());
        assertEquals("Imp", byName.get(1).getSource());
        assertEquals("Zulrah", byName.get(2).getSource());
    }

    private static DropEntry drop(String source, String level, String quantity, String rate) {
        DropEntry entry = new DropEntry();
        entry.setSource(source);
        entry.setLevel(level);
        entry.setQuantity(quantity);
        entry.setRate(rate);
        return entry;
    }
}
