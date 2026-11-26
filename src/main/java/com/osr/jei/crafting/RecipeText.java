package com.osr.jei.crafting;

import com.osr.jei.model.RecipeInfo;

import java.util.List;

/** One-line skill, level, facility, and XP summary. */
public final class RecipeText {

    private RecipeText() {
    }

    public static String summary(RecipeInfo recipe) {
        if (recipe == null) {
            return "";
        }
        StringBuilder info = new StringBuilder();
        if (recipe.skill != null && !recipe.skill.isEmpty()) {
            info.append(recipe.skill);
            if (recipe.level > 0) {
                info.append(" (Lv. ").append(recipe.level).append(")");
            }
        }
        if (recipe.skill2 != null && !recipe.skill2.isEmpty()) {
            if (info.length() > 0) {
                info.append(" \u00b7 ");
            }
            info.append(recipe.skill2);
            if (recipe.level2 > 0) {
                info.append(" (Lv. ").append(recipe.level2).append(")");
            }
        }
        if (recipe.facility != null && !recipe.facility.isEmpty()) {
            if (info.length() > 0) {
                info.append(" \u00b7 ");
            }
            info.append(recipe.facility);
        }
        if (recipe.experience > 0) {
            if (info.length() > 0) {
                info.append(" \u00b7 ");
            }
            if (recipe.experience == Math.floor(recipe.experience)) {
                info.append((int) recipe.experience);
            } else {
                info.append(recipe.experience);
            }
            info.append(" XP");
        }
        if (recipe.outputQuantity > 1) {
            if (info.length() > 0) {
                info.append(" \u00b7 ");
            }
            info.append("Makes ").append(recipe.outputQuantity);
        }
        return info.toString();
    }

    /**
     * Puts ingredients that loop back to an ancestor on the recipe line.
     * Names are joined with {@code +}. An existing skill summary stays in front.
     */
    public static String withLoopedIngredients(String summary, List<String> loopedNames) {
        String names = joinNames(loopedNames);
        if (names.isEmpty()) {
            return summary == null ? "" : summary;
        }
        if (summary == null || summary.isEmpty()) {
            return names;
        }
        return summary + " \u00b7 " + names;
    }

    private static String joinNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return "";
        }
        StringBuilder joined = new StringBuilder();
        for (String name : names) {
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            if (joined.length() > 0) {
                joined.append(" + ");
            }
            joined.append(name.trim());
        }
        return joined.toString();
    }

    /**
     * Materials for {@code needed} outputs. {@code outputQuantity} is how many
     * one craft produces; the result is rounded up to a whole craft.
     */
    public static long scale(long needed, int outputQuantity, int materialQuantity) {
        if (needed <= 0 || materialQuantity <= 0) {
            return 0;
        }
        int produced = Math.max(1, outputQuantity);
        long crafts = (needed + produced - 1) / produced;
        if (crafts > Long.MAX_VALUE / materialQuantity) {
            return Long.MAX_VALUE;
        }
        return crafts * materialQuantity;
    }
}
