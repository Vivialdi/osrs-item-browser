package com.osr.jei.model;

import java.util.List;

/**
 * Location data for one monster, parsed from the OSRS Wiki
 * {@code {{Infobox Monster}}} wikitext template.
 */
public class MonsterInfo {
    /** Page / display name of the monster. */
    public final String       name;
    /** Combat level, or 0 if unavailable. */
    public final int          combatLevel;
    /** Where this monster is found (e.g. "Lumbridge Swamp", "Varrock Sewers"). */
    public final List<String> locations;
    /** True when the same page already contains world-map coordinates. */
    public final boolean mapped;

    public MonsterInfo(String name, int combatLevel, List<String> locations) {
        this(name, combatLevel, locations, false);
    }

    public MonsterInfo(String name, int combatLevel, List<String> locations, boolean mapped) {
        this.name        = name;
        this.combatLevel = combatLevel;
        this.locations   = locations;
        this.mapped      = mapped;
    }
}
