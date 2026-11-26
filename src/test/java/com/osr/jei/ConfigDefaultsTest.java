package com.osr.jei;

import com.osr.jei.drops.DropSort;
import com.osr.jei.ironman.IronmanMode;
import com.osr.jei.map.PinSize;
import com.osr.jei.search.SearchCriteria;
import com.osr.jei.ui.FontFamilyChoice;
import com.osr.jei.ui.FontStyleChoice;
import com.osr.jei.ui.SectionState;
import org.junit.Test;

import java.awt.Color;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConfigDefaultsTest {

    @Test
    public void newSettingsUseTheRequestedDefaultsAndOldOnesStay() {
        JEIConfig config = new Bare();
        assertEquals(SearchCriteria.Trade.ANY, config.filterTradeable());
        assertTrue(config.showPrices());
        assertEquals(IronmanMode.AUTO, config.ironmanMode());
        assertTrue(config.showRequirementChecks());
        assertTrue(config.showOwned());
        assertTrue(config.showDropSources());
        assertEquals(DropSort.RATE, config.dropSort());
        assertTrue(config.showEquipmentCompare());
        assertEquals(12, config.fontSize());
        assertEquals(FontStyleChoice.REGULAR, config.fontStyle());
        assertEquals(FontFamilyChoice.DEFAULT, config.fontFamily());
        assertEquals(45, config.detailRatio());

        assertTrue(config.rightClickLookup());
        assertEquals(PinSize.MEDIUM, config.pinSize());
        assertEquals(16, config.pinSize().pixels());
        assertTrue(config.clearPinsOnSwitch());
        assertFalse(config.sectionsOpenByDefault());
        assertTrue(config.showHighAlch());
        assertEquals(new Color(255, 105, 180), config.mapPinColor());
    }

    @Test
    public void openByDefaultExpandsMainSectionsAndLeavesLocationsClosed() {
        SectionState state = new SectionState();
        state.reset(true);
        assertTrue(state.expanded(SectionState.EQUIPMENT));
        assertTrue(state.expanded(SectionState.SHOPS));
        assertTrue(state.expanded(SectionState.USED_IN));
        assertTrue(state.expanded(SectionState.DROPS));
        assertTrue(state.expanded(SectionState.CRAFTING));
        assertTrue(state.expanded(SectionState.TRADING));
        assertFalse(state.expanded(SectionState.LOCATIONS));

        state.reset(false);
        assertFalse(state.expanded(SectionState.SHOPS));
        assertFalse(state.expanded(SectionState.DROPS));
        assertFalse(state.expanded(SectionState.LOCATIONS));
    }

    private static final class Bare implements JEIConfig {
    }
}
