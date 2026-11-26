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
        assertEquals("2, 7", cave.getQuantity());
        assertEquals("20/128, 15/128", DropMerger.tooltipRate(cave));
        assertEquals("1/6.4, 1/8.53", DropMerger.rateLabel(cave.getRate(), cave.getQuantity()));
        assertEquals("28", merged.get(1).getLevel());
        assertEquals("1/64", DropMerger.rateLabel(merged.get(1).getRate(), merged.get(1).getQuantity()));
        assertSame(goblin, merged.get(2));
    }

    @Test
    public void oneLineKeepsTheOriginalRateAndTooltip() {
        DropEntry only = drop("Cave goblin miner", "11", "7", "20/128");
        List<DropEntry> merged = DropMerger.merge(Collections.singletonList(only));
        assertEquals(1, merged.size());
        assertSame(only, merged.get(0));
        assertEquals("1/6.4", DropMerger.rateLabel(only.getRate(), only.getQuantity()));
        assertEquals("20/128", DropMerger.tooltipRate(only));
        assertEquals("Dropped by (1)", SectionSummaries.drops(merged));
    }

    @Test
    public void namesMatchIgnoringCaseAndExtraSpace() {
        DropEntry lower = drop("cave  goblin miner", " 11 ", "4", "1/32");
        DropEntry upper = drop("Cave goblin miner", "11", "1", "1/100");
        List<DropEntry> merged = DropMerger.merge(Arrays.asList(lower, upper));
        assertEquals(1, merged.size());
        assertEquals("1, 4", merged.get(0).getQuantity());
        assertEquals("1/32, 1/100", DropMerger.rateLabel(merged.get(0).getRate(), merged.get(0).getQuantity()));
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
