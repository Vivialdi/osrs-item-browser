package com.osr.jei.crafting;

import com.osr.jei.model.DropEntry;
import com.osr.jei.model.RecipeInfo;
import com.osr.jei.model.ShopEntry;
import com.osr.jei.service.ItemDetails;

import java.util.Locale;

/**
 * An item is a base material when it can be obtained without crafting it
 * from other items: a gathering recipe, a shop, a monster drop, or the
 * {@link RawMaterials} fallback when the page has not been read.
 * An optional recipe does not make it a crafting step.
 */
public final class BaseMaterials {

    private BaseMaterials() {
    }

    public static boolean isBase(String name, ItemDetails details) {
        if (RawMaterials.isBase(name)) {
            return true;
        }
        if (details == null || details.missing) {
            return false;
        }
        return soldInShop(details) || droppedByMonster(details) || gathered(details);
    }

    public static boolean gathered(ItemDetails details) {
        if (details == null || details.recipes == null) {
            return false;
        }
        for (RecipeInfo recipe : details.recipes) {
            if (recipe == null) {
                continue;
            }
            if (isGathering(recipe.skill) || isGathering(recipe.skill2)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isGathering(String skill) {
        if (skill == null) {
            return false;
        }
        String text = skill.trim().toLowerCase(Locale.ROOT);
        return "mining".equals(text)
            || "woodcutting".equals(text)
            || "fishing".equals(text)
            || "hunter".equals(text)
            || "farming".equals(text);
    }

    private static boolean soldInShop(ItemDetails details) {
        if (details.shops == null) {
            return false;
        }
        for (ShopEntry shop : details.shops) {
            if (shop != null && shop.sells) {
                return true;
            }
        }
        return false;
    }

    private static boolean droppedByMonster(ItemDetails details) {
        if (details.drops == null) {
            return false;
        }
        for (DropEntry drop : details.drops) {
            if (drop != null && drop.getSource() != null && !drop.getSource().isEmpty()) {
                return true;
            }
        }
        return false;
    }

}
