package com.osr.jei.model;

/**
 * One ingredient line in a crafting recipe — e.g. "Iron bar × 5".
 */
public class RecipeIngredient {
    public final String name;
    public final int    quantity;

    public RecipeIngredient(String name, int quantity) {
        this.name     = name;
        this.quantity = quantity;
    }
}
