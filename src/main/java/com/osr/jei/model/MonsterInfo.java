package com.osr.jei.model;

import java.util.List;

/**
 * Location data for one monster, parsed from the OSRS Wiki
 * {@code {{Infobox Monster}}} wikitext template and the rendered section-0 HTML
 * (which contains the Kartographer map link with tile coordinates).
 */
public class MonsterInfo {
    /** Page / display name of the monster. */
    public final String       name;
    /** Combat level, or 0 if unavailable. */
    public final int          combatLevel;
    /** Where this monster is found (e.g. "Lumbridge Swamp", "Varrock Sewers"). */
    public final List<String> locations;

    /**
     * Primary OSRS tile X coordinate from the wiki's Kartographer map link
     * ({@code data-lon} attribute).  {@code -1} if the page has no map link.
     */
    public final int mapX;

    /**
     * Primary OSRS tile Y coordinate from the wiki's Kartographer map link
     * ({@code data-lat} attribute).  {@code -1} if the page has no map link.
     */
    public final int mapY;

    /**
     * OSRS game plane (0 = surface/most dungeons, 1–3 = upper floors).
     * {@code -1} if the page has no map link.
     */
    public final int mapPlane;

    public MonsterInfo(String name, int combatLevel, List<String> locations,
                       int mapX, int mapY, int mapPlane) {
        this.name        = name;
        this.combatLevel = combatLevel;
        this.locations   = locations;
        this.mapX        = mapX;
        this.mapY        = mapY;
        this.mapPlane    = mapPlane;
    }
}
