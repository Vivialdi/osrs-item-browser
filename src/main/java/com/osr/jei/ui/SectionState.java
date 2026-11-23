package com.osr.jei.ui;

import java.util.HashMap;
import java.util.Map;

/**
 * Collapsed or expanded choice for the detail sections of the open item.
 * Every section starts collapsed. Switching items clears the choices.
 */
public final class SectionState {

    public static final String EQUIPMENT = "equipment";
    public static final String SHOPS = "shops";
    public static final String USED_IN = "usedIn";
    public static final String DROPS = "drops";
    public static final String CRAFTING = "crafting";
    public static final String LOCATIONS = "locations";
    public static final String TRADING = "trading";

    private final Map<String, Boolean> expanded = new HashMap<>();

    public boolean expanded(String key) {
        Boolean value = expanded.get(key);
        return value != null && value.booleanValue();
    }

    /** Back to collapsed for every section. Used when the open item changes. */
    public void reset() {
        expanded.clear();
    }

    public void setExpanded(String key, boolean value) {
        if (key == null) {
            return;
        }
        expanded.put(key, Boolean.valueOf(value));
    }

    /** @return the state after the flip */
    public boolean toggle(String key) {
        boolean next = !expanded(key);
        setExpanded(key, next);
        return next;
    }
}
