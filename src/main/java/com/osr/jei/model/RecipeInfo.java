package com.osr.jei.model;

import java.util.ArrayList;
import java.util.Collections;
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
    /** First named product. Empty when the template does not name one. */
    public final String                  outputName;
    /** Every product named on the recipe, in template order. */
    public final List<String>            outputs;
    /** Second skill on the recipe template, empty when the recipe has one skill. */
    public final String                  skill2;
    public final int                     level2;

    public RecipeInfo(String skill, int level, String facility,
                      double experience, List<RecipeIngredient> ingredients) {
        this(skill, level, facility, experience, ingredients, 1, "");
    }

    public RecipeInfo(String skill, int level, String facility,
                      double experience, List<RecipeIngredient> ingredients,
                      int outputQuantity, String outputName) {
        this(skill, level, facility, experience, ingredients, outputQuantity, outputName, "", 0);
    }

    public RecipeInfo(String skill, int level, String facility,
                      double experience, List<RecipeIngredient> ingredients,
                      int outputQuantity, String outputName,
                      String skill2, int level2) {
        this(skill, level, facility, experience, ingredients, outputQuantity, outputName, skill2, level2, null);
    }

    public RecipeInfo(String skill, int level, String facility,
                      double experience, List<RecipeIngredient> ingredients,
                      int outputQuantity, String outputName,
                      String skill2, int level2, List<String> outputs) {
        this.skill       = skill;
        this.level       = level;
        this.facility    = facility;
        this.experience  = experience;
        this.ingredients = ingredients;
        this.outputQuantity = outputQuantity <= 0 ? 1 : outputQuantity;
        this.skill2 = skill2 == null ? "" : skill2;
        this.level2 = level2;
        List<String> named = new ArrayList<>();
        if (outputs != null) {
            for (String output : outputs) {
                if (output == null || output.trim().isEmpty()) {
                    continue;
                }
                named.add(output.trim());
            }
        }
        if (named.isEmpty() && outputName != null && !outputName.trim().isEmpty()) {
            named.add(outputName.trim());
        }
        this.outputs = Collections.unmodifiableList(named);
        this.outputName = named.isEmpty() ? "" : named.get(0);
    }
}
