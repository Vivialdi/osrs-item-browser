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

    public RecipeInfo(String skill, int level, String facility,
                      double experience, List<RecipeIngredient> ingredients) {
        this.skill       = skill;
        this.level       = level;
        this.facility    = facility;
        this.experience  = experience;
        this.ingredients = ingredients;
    }
}
