package com.osr.jei.requirements;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Canonical skill names, matching {@code Skill.getName()} in the client.
 * Unknown names stay unknown so a requirement is never marked met by a guess.
 */
public final class SkillNames {

    private static final Map<String, String> CANONICAL;

    static {
        Map<String, String> map = new HashMap<>();
        String[] names = {
            "Attack", "Strength", "Defence", "Hitpoints", "Ranged", "Prayer", "Magic",
            "Cooking", "Woodcutting", "Fletching", "Fishing", "Firemaking", "Crafting",
            "Smithing", "Mining", "Herblore", "Agility", "Thieving", "Slayer", "Farming",
            "Runecraft", "Hunter", "Construction", "Sailing"
        };
        for (String name : names) {
            map.put(normalize(name), name);
        }
        map.put(normalize("Defense"), "Defence");
        map.put(normalize("Range"), "Ranged");
        map.put(normalize("Constitution"), "Hitpoints");
        map.put(normalize("Runecrafting"), "Runecraft");
        map.put(normalize("HP"), "Hitpoints");
        CANONICAL = Collections.unmodifiableMap(map);
    }

    private SkillNames() {
    }

    /** Display name such as {@code Attack}, or null when {@code raw} is not a skill. */
    public static String canonical(String raw) {
        if (raw == null) {
            return null;
        }
        return CANONICAL.get(normalize(raw));
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }
}
