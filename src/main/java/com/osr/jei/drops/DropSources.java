package com.osr.jei.drops;

import com.osr.jei.model.DropEntry;

import java.util.Locale;

/**
 * Drop sources that are not monsters. The wiki marks the level cell with
 * {@code drops-combat}, {@code drops-sailing}, {@code drops-hunter},
 * {@code drops-thieving}, or {@code drops-reward}.
 */
public final class DropSources {

    public static final String MONSTER_EMPTY = "No locations are listed for this monster.";
    public static final String OTHER_EMPTY = "No locations are listed.";

    private DropSources() {
    }

    public static boolean monster(DropEntry drop) {
        String kind = kind(drop);
        return kind.isEmpty() || "combat".equals(kind);
    }

    /** Show on map needs coordinates and at least one place. A source with neither stays a text row. */
    public static boolean showMap(DropEntry drop, boolean mapped, int places) {
        return mapped && places > 0;
    }

    public static String emptyLocations(DropEntry drop) {
        return monster(drop) ? MONSTER_EMPTY : OTHER_EMPTY;
    }

    private static String kind(DropEntry drop) {
        if (drop == null || drop.getLevelKind() == null) {
            return "";
        }
        return drop.getLevelKind().trim().toLowerCase(Locale.ROOT);
    }
}
