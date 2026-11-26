package com.osr.jei.ui;

import com.osr.jei.JEIConfig;

/**
 * Which detail sections start open for a new item. This only flips visibility.
 * It does not request a wiki page. Monster rows still load when they are clicked.
 */
public final class DefaultOpen {

    private DefaultOpen() {
    }

    public static void apply(SectionState state, JEIConfig config) {
        if (state == null) {
            return;
        }
        state.reset();
        if (config == null) {
            return;
        }
        state.setExpanded(SectionState.TRADING, config.openTradingByDefault());
        state.setExpanded(SectionState.EQUIPMENT, config.openEquipmentByDefault());
        state.setExpanded(SectionState.SHOPS, config.openShopsByDefault());
        state.setExpanded(SectionState.USED_IN, config.openUsedInByDefault());
        state.setExpanded(SectionState.DROPS, config.openDropsByDefault());
        state.setExpanded(SectionState.CRAFTING, config.openCraftingByDefault());
        state.setExpanded(SectionState.LOCATIONS, config.openLocationsByDefault());
    }

    /** Config key to section id, or null when the key is not one of these settings. */
    public static String sectionKey(String configKey) {
        if (configKey == null) {
            return null;
        }
        switch (configKey) {
            case "openTradingByDefault":
                return SectionState.TRADING;
            case "openEquipmentByDefault":
                return SectionState.EQUIPMENT;
            case "openShopsByDefault":
                return SectionState.SHOPS;
            case "openUsedInByDefault":
                return SectionState.USED_IN;
            case "openDropsByDefault":
                return SectionState.DROPS;
            case "openCraftingByDefault":
                return SectionState.CRAFTING;
            case "openLocationsByDefault":
                return SectionState.LOCATIONS;
            default:
                return null;
        }
    }

    public static boolean enabled(JEIConfig config, String configKey) {
        if (config == null) {
            return false;
        }
        switch (configKey) {
            case "openTradingByDefault":
                return config.openTradingByDefault();
            case "openEquipmentByDefault":
                return config.openEquipmentByDefault();
            case "openShopsByDefault":
                return config.openShopsByDefault();
            case "openUsedInByDefault":
                return config.openUsedInByDefault();
            case "openDropsByDefault":
                return config.openDropsByDefault();
            case "openCraftingByDefault":
                return config.openCraftingByDefault();
            case "openLocationsByDefault":
                return config.openLocationsByDefault();
            default:
                return false;
        }
    }
}
