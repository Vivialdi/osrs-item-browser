package com.osr.jei.ironman;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Order of the blocks under the item name. Ironman mode leads with
 * self-sufficient sources and leaves the Grand Exchange line last.
 */
public final class DetailLayout {

    public static final String GE = "ge";
    public static final String ALCH = "alch";
    public static final String EQUIPMENT = "equipment";
    public static final String SHOPS = "shops";
    public static final String USED_IN = "usedIn";
    public static final String DROPS = "drops";
    public static final String CRAFTING = "crafting";

    private static final List<String> STANDARD = Collections.unmodifiableList(Arrays.asList(
        GE, ALCH, EQUIPMENT, SHOPS, USED_IN, DROPS, CRAFTING
    ));

    private static final List<String> IRONMAN = Collections.unmodifiableList(Arrays.asList(
        ALCH, SHOPS, DROPS, CRAFTING, EQUIPMENT, USED_IN, GE
    ));

    private DetailLayout() {
    }

    public static List<String> order(boolean ironman) {
        return ironman ? IRONMAN : STANDARD;
    }
}
