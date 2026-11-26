package com.osr.jei;

import com.osr.jei.drops.DropSort;
import com.osr.jei.ironman.IronmanMode;
import com.osr.jei.map.PinSize;
import com.osr.jei.search.SearchCriteria;
import com.osr.jei.ui.DefaultOpen;
import com.osr.jei.ui.FontFamilyChoice;
import com.osr.jei.ui.FontStyleChoice;
import com.osr.jei.ui.RecentLimit;
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
        assertTrue(config.wrapText());
        assertEquals(RecentLimit.TWELVE, config.maxRecentItems());
        assertEquals(12, config.maxRecentItems().count());
        assertTrue(config.showRecentItems());
        assertFalse(config.showAnimationItems());
        assertFalse(config.showTaskCarryItems());

        assertTrue(config.rightClickLookup());
        assertEquals(PinSize.MEDIUM, config.pinSize());
        assertEquals(16, config.pinSize().pixels());
        assertTrue(config.clearPinsOnSwitch());
        assertTrue(config.showHighAlch());
        assertEquals(new Color(255, 105, 180), config.mapPinColor());
        assertFalse(config.openTradingByDefault());
        assertFalse(config.openEquipmentByDefault());
        assertFalse(config.openShopsByDefault());
        assertFalse(config.openUsedInByDefault());
        assertFalse(config.openDropsByDefault());
        assertFalse(config.openCraftingByDefault());
        assertFalse(config.openLocationsByDefault());
    }

    @Test
    public void eachDefaultOpenSettingStartsOnlyThatSection() {
        SectionState state = new SectionState();
        DefaultOpen.apply(state, new JEIConfig() {
            @Override
            public boolean openShopsByDefault() {
                return true;
            }

            @Override
            public boolean openCraftingByDefault() {
                return true;
            }
        });
        assertTrue(state.expanded(SectionState.SHOPS));
        assertTrue(state.expanded(SectionState.CRAFTING));
        assertFalse(state.expanded(SectionState.TRADING));
        assertFalse(state.expanded(SectionState.EQUIPMENT));
        assertFalse(state.expanded(SectionState.USED_IN));
        assertFalse(state.expanded(SectionState.DROPS));
        assertFalse(state.expanded(SectionState.LOCATIONS));

        state.setExpanded(SectionState.DROPS, true);
        state.setExpanded(SectionState.SHOPS, DefaultOpen.enabled(new JEIConfig() {
            @Override
            public boolean openShopsByDefault() {
                return false;
            }
        }, "openShopsByDefault"));
        assertFalse(state.expanded(SectionState.SHOPS));
        assertTrue(state.expanded(SectionState.DROPS));
        assertEquals(SectionState.DROPS, DefaultOpen.sectionKey("openDropsByDefault"));
        assertEquals(null, DefaultOpen.sectionKey("showHighAlch"));
    }

    private static final class Bare implements JEIConfig {
    }
}
