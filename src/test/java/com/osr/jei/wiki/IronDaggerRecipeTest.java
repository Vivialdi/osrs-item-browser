package com.osr.jei.wiki;

import com.osr.jei.crafting.BuildExpander;
import com.osr.jei.model.RecipeInfo;
import com.osr.jei.service.ItemDetails;
import com.osr.jei.ui.SectionSummaries;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Iron dagger's page lists smithing and the three poison recipes. */
public class IronDaggerRecipeTest {

    private static final String WIKI = ""
        + "{{Infobox Item\n"
        + "|name1 = Iron dagger\n"
        + "|name2 = Iron dagger(p)\n"
        + "|name3 = Iron dagger(p+)\n"
        + "|name4 = Iron dagger(p++)\n"
        + "}}\n"
        + "{{Recipe\n"
        + "|skill1 = Smithing\n"
        + "|skill1lvl = 15\n"
        + "|skill1exp = 25\n"
        + "|members = No\n"
        + "|tools = Hammer\n"
        + "|facilities = Anvil\n"
        + "|mat1 = Iron bar\n"
        + "|output1 = Iron dagger\n"
        + "}}\n"
        + "{{Recipe\n"
        + "|members = Yes\n"
        + "|mat1 = Iron dagger\n"
        + "|mat1quantity = 1\n"
        + "|mat2 = Weapon poison\n"
        + "|mat2quantity = 1\n"
        + "|output1 = Iron dagger(p)\n"
        + "|output1quantity = 1\n"
        + "}}\n"
        + "{{Recipe\n"
        + "|members = Yes\n"
        + "|mat1 = Iron dagger\n"
        + "|mat1quantity = 1\n"
        + "|mat2 = Weapon poison(+)\n"
        + "|mat2quantity = 1\n"
        + "|output1 = Iron dagger(p+)\n"
        + "|output1quantity = 1\n"
        + "}}\n"
        + "{{Recipe\n"
        + "|members = Yes\n"
        + "|mat1 = Iron dagger\n"
        + "|mat1quantity = 1\n"
        + "|mat2 = Weapon poison(++)\n"
        + "|mat2quantity = 1\n"
        + "|output1 = Iron dagger(p++)\n"
        + "|output1quantity = 1\n"
        + "}}\n";

    private static final String ALREADY_LISTED = ""
        + "<h2 id=\"Products\">Products</h2>"
        + "<table class=\"wikitable\"><tr><td><a href=\"/w/Iron_dagger(p)\">Iron dagger(p)</a></td></tr></table>";

    @Test
    public void ironDaggerCraftingIsSmithingAndPoisonsAreUses() {
        ItemDetails details = ItemDetails.from(new PageContent(WIKI, "", false), "Iron dagger");
        assertEquals(1, details.recipes.size());
        RecipeInfo smithing = details.recipes.get(0);
        assertEquals("Smithing", smithing.skill);
        assertEquals(15, smithing.level);
        assertEquals("Iron dagger", smithing.outputName);
        assertEquals(1, smithing.ingredients.size());
        assertEquals("Iron bar", smithing.ingredients.get(0).name);
        assertEquals(1, smithing.ingredients.get(0).quantity);

        assertEquals(Arrays.asList("Iron dagger(p)", "Iron dagger(p+)", "Iron dagger(p++)"), details.products);
        assertEquals("Crafting (1) \u00b7 Smithing (Lv. 15)", SectionSummaries.crafting(details.recipes));
        assertEquals("Used to craft (3)", SectionSummaries.uses(details.products));
    }

    @Test
    public void poisonedDaggerKeepsItsOwnRecipe() {
        ItemDetails details = ItemDetails.from(new PageContent(WIKI, "", false), "Iron dagger(p)");
        assertEquals(1, details.recipes.size());
        RecipeInfo poison = details.recipes.get(0);
        assertEquals("Iron dagger(p)", poison.outputName);
        assertEquals(2, poison.ingredients.size());
        assertEquals("Iron dagger", poison.ingredients.get(0).name);
        assertEquals("Weapon poison", poison.ingredients.get(1).name);
        assertTrue(details.products.isEmpty());

        BuildExpander.Result tree = BuildExpander.expandSelected(
            "Iron dagger(p)", null, details, null, Collections.emptySet());
        assertEquals(1, tree.recipes.size());
        assertEquals(2, tree.recipes.get(0).ingredients.size());
        assertEquals("Iron dagger", tree.recipes.get(0).ingredients.get(0).name);
        assertFalse(tree.recipes.get(0).ingredients.get(0).cycle);
        assertEquals("Weapon poison", tree.recipes.get(0).ingredients.get(1).name);
        assertFalse(tree.recipes.get(0).ingredients.get(1).cycle);
        assertEquals("Crafting (1)", SectionSummaries.crafting(details.recipes));
    }

    @Test
    public void listedUseIsNotRepeated() {
        ItemDetails details = ItemDetails.from(new PageContent(WIKI, ALREADY_LISTED, false), "Iron dagger");
        assertEquals(Arrays.asList("Iron dagger(p)", "Iron dagger(p+)", "Iron dagger(p++)"), details.products);
    }

    @Test
    public void everyNamedProductOfAUseIsKept() {
        String wiki = "{{Recipe|mat1=Iron dagger|mat2=Weapon poison"
            + "|output1=Iron dagger(p)|output2=Iron dagger(p+)}}\n";
        ItemDetails details = ItemDetails.from(new PageContent(wiki, "", false), "Iron dagger");
        assertTrue(details.recipes.isEmpty());
        assertEquals(Arrays.asList("Iron dagger(p)", "Iron dagger(p+)"), details.products);
    }
}
