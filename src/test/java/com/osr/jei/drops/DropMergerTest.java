package com.osr.jei.drops;

import com.osr.jei.model.DropEntry;
import com.osr.jei.ui.SectionSummaries;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class DropMergerTest {

    @Test
    public void sameMonsterAndLevelBecomeOneRow() {
        DropEntry seven = drop("Cave goblin miner", "11", "7", "20/128");
        DropEntry two = drop("Cave goblin miner", "11", "2", "15/128");
        DropEntry otherLevel = drop("Cave goblin miner", "28", "1", "1/64");
        DropEntry goblin = drop("Goblin", "2", "1", "1/16");

        List<DropEntry> merged = DropMerger.merge(Arrays.asList(seven, two, otherLevel, goblin));

        assertEquals(3, merged.size());
        assertEquals("Dropped by (3)", SectionSummaries.drops(merged));
        DropEntry cave = merged.get(0);
        assertEquals("Cave goblin miner", cave.getSource());
        assertEquals("11", cave.getLevel());
        assertEquals("Lv. 11\n7 at 1/6.4\n2 at 1/8.53", DropMerger.detail(cave));
        assertEquals("20/128, 15/128", DropMerger.tooltipRate(cave));
        assertEquals("Lv. 28 \u00b7 1 at 1/64", DropMerger.detail(merged.get(1)));
        assertEquals("Lv. 2 \u00b7 1 at 1/16", DropMerger.detail(goblin));
        assertSame(goblin, merged.get(2));
    }

    @Test
    public void oneLineKeepsTheOriginalRateAndTooltip() {
        DropEntry only = drop("Cave goblin miner", "11", "7", "20/128");
        List<DropEntry> merged = DropMerger.merge(Collections.singletonList(only));
        assertEquals(1, merged.size());
        assertSame(only, merged.get(0));
        assertEquals("Lv. 11 \u00b7 7 at 1/6.4", DropMerger.detail(only));
        assertEquals("20/128", DropMerger.tooltipRate(only));
        assertEquals("Dropped by (1)", SectionSummaries.drops(merged));
    }

    @Test
    public void namesMatchIgnoringCaseAndExtraSpace() {
        DropEntry lower = drop("cave  goblin miner", " 11 ", "4", "1/32");
        DropEntry upper = drop("Cave goblin miner", "11", "1", "1/100");
        List<DropEntry> merged = DropMerger.merge(Arrays.asList(lower, upper));
        assertEquals(1, merged.size());
        assertEquals("Lv. 11\n4 at 1/32\n1 at 1/100", DropMerger.detail(merged.get(0)));
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
