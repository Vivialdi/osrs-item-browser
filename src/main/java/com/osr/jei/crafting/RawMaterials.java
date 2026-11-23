package com.osr.jei.crafting;

import java.util.Locale;

/**
 * Items that are gathered, not crafted. The crafting tree already treats them
 * as base materials, so they do not get an expand arrow and do not need a
 * wiki fetch to prove they have no recipe.
 */
public final class RawMaterials {

    private RawMaterials() {
    }

    public static boolean isBase(String name) {
        if (name == null) {
            return false;
        }
        String text = name.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return false;
        }
        if (text.endsWith(" essence") || text.equals("essence")) {
            return true;
        }
        if (text.endsWith(" ore") || text.equals("coal") || text.equals("clay") || text.equals("soft clay")) {
            return true;
        }
        if (text.equals("logs") || text.equals("log") || text.endsWith(" logs")) {
            return true;
        }
        if (text.startsWith("raw ")) {
            return true;
        }
        if (text.endsWith(" herb") || text.startsWith("grimy ")) {
            return true;
        }
        return false;
    }
}
