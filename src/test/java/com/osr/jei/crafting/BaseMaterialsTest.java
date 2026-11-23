package com.osr.jei.crafting;

import com.osr.jei.model.DropEntry;
import com.osr.jei.model.RecipeInfo;
import com.osr.jei.model.RecipeIngredient;
import com.osr.jei.model.ShopEntry;
import com.osr.jei.service.ItemDetails;
import org.junit.Test;

import java.util.Collections;
import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BaseMaterialsTest {

    @Test
    public void essenceOreAndLogsAreBaseEvenWithAnOptionalRecipe() {
        ItemDetails pure = details(new RecipeInfo(
            "Mining", 64, "Essence orifice", 0,
            Collections.singletonList(new RecipeIngredient("Tainted essence chunk", 1))));
        ItemDetails guardian = details(new RecipeInfo(
            "Crafting", 1, "", 0,
            Collections.singletonList(new RecipeIngredient("Guardian fragments", 1))));
        assertTrue(BaseMaterials.isBase("Pure essence", pure));
        assertTrue(BaseMaterials.isBase("Rune essence", null));
        assertTrue(BaseMaterials.isBase("Guardian essence", guardian));
        assertTrue(BaseMaterials.isBase("Runite ore", null));
        assertTrue(BaseMaterials.isBase("Oak logs", null));
        assertFalse(arrow("Pure essence", pure));
        assertFalse(arrow("Rune essence", ItemDetails.EMPTY));
        assertFalse(arrow("Guardian essence", guardian));
        assertFalse(arrow("Runite ore", null));
        assertFalse(arrow("Oak logs", null));
    }

    @Test
    public void aCraftedIntermediateKeepsItsArrow() {
        ItemDetails staff = details(new RecipeInfo(
            "Crafting", 66, "Air orb", 137.5,
            Collections.singletonList(new RecipeIngredient("Air orb", 1))));
        ItemDetails orb = details(new RecipeInfo(
            "Crafting", 66, "Glassblowing pipe", 52.5,
            Collections.singletonList(new RecipeIngredient("Molten glass", 1))));
        assertFalse(BaseMaterials.isBase("Battlestaff", staff));
        assertFalse(BaseMaterials.isBase("Air orb", orb));
        assertTrue(arrow("Battlestaff", staff));
        assertTrue(arrow("Air orb", orb));
        assertTrue(arrow("Battlestaff", null));
    }

    @Test
    public void aShopOrDropAlsoCountsAsABaseMaterial() {
        ItemDetails sold = new ItemDetails(
            Collections.emptyList(), Optional.empty(), 0,
            Collections.singletonList(new ShopEntry("General store", 1, 10, "gp", true)),
            Collections.emptyList(), Collections.emptyList());
        DropEntry drop = new DropEntry();
        drop.setSource("Goblin");
        ItemDetails dropped = new ItemDetails(
            Collections.emptyList(), Optional.empty(), 0,
            Collections.emptyList(), Collections.singletonList(drop), Collections.emptyList());
        assertTrue(BaseMaterials.isBase("Beer", sold));
        assertTrue(BaseMaterials.isBase("Bones", dropped));
        assertFalse(arrow("Beer", sold));
        assertFalse(arrow("Bones", dropped));
    }

    private static boolean arrow(String name, ItemDetails cached) {
        ItemDetails root = details(new RecipeInfo(
            "Crafting", 1, "", 0,
            Collections.singletonList(new RecipeIngredient(name, 1))));
        BuildExpander.Result tree = BuildExpander.expandSelected("Product", null, root, new RecipePages() {
            @Override
            public ItemDetails cached(String itemName) {
                return name.equals(itemName) ? cached : null;
            }

            @Override
            public ItemDetails load(String itemName) {
                throw new AssertionError("open tree must not fetch");
            }
        }, Collections.emptySet());
        return tree.recipes.get(0).ingredients.get(0).showExpander;
    }

    private static ItemDetails details(RecipeInfo recipe) {
        return new ItemDetails(
            Collections.singletonList(recipe),
            Optional.empty(),
            0,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList());
    }
}
