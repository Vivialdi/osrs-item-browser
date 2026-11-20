package com.osr.jei.model;

import java.util.List;

/**
 * All the data we surface for one crafting recipe.
 */
public class RecipeInfo {
    public final String                  skill;       // e.g. "Smithing"
    public final int                     level;       // required level, 0 if unknown
    public final String                  facility;    // e.g. "Anvil", "" if none
    public final double                  experience;  // XP per craft, 0 if unknown
    public final List<RecipeIngredient>  ingredients;
    /** How many of the output one craft produces. At least 1. */
    public final int                     outputQuantity;
    /** Wiki output name when the template names it. Empty when it is this page's item. */
    public final String                  outputName;

    public RecipeInfo(String skill, int level, String facility,
                      double experience, List<RecipeIngredient> ingredients) {
        this(skill, level, facility, experience, ingredients, 1, "");
    }

    public RecipeInfo(String skill, int level, String facility,
                      double experience, List<RecipeIngredient> ingredients,
                      int outputQuantity, String outputName) {
        this.skill       = skill;
        this.level       = level;
        this.facility    = facility;
        this.experience  = experience;
        this.ingredients = ingredients;
        this.outputQuantity = outputQuantity <= 0 ? 1 : outputQuantity;
        this.outputName = outputName == null ? "" : outputName;
    }
}
