package com.osr.jei.crafting;

import com.osr.jei.model.RecipeInfo;
import com.osr.jei.model.RecipeIngredient;
import com.osr.jei.service.ItemDetails;
import com.osr.jei.wiki.PageContent;
import com.osr.jei.wiki.RecipeParser;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BuildExpanderTest {

    @Test
    public void multiStepTreeScalesQuantitiesAndSkipsCachedPages() {
        ItemDetails plate = details(recipe("Smithing", 99, "Anvil", 375, 1, ing("Rune bar", 5)));
        ItemDetails bar = details(recipe("Smithing", 99, "Furnace", 50, 1, ing("Runite ore", 1), ing("Coal", 8)));
        ItemDetails ore = ItemDetails.EMPTY;
        ItemDetails coal = ItemDetails.EMPTY;

        Pages pages = new Pages();
        pages.known.put("rune bar", bar);
        pages.known.put("runite ore", ore);
        pages.known.put("coal", coal);
        pages.cache.put("rune bar", bar);
        pages.cache.put("runite ore", ore);
        pages.cache.put("coal", coal);

        BuildExpander.Result tree = BuildExpander.expandAll("Rune platebody", 1127, plate, pages);
        assertEquals(0, tree.fetches);
        assertEquals(0, pages.loads);
        assertFalse(pages.loaded.contains("rune platebody"));

        BuildExpander.BuildNode bars = tree.recipes.get(0).ingredients.get(0);
        assertEquals(5, bars.quantity);
        assertEquals("Runite ore", bars.children.get(0).name);
        assertEquals(5, bars.children.get(0).quantity);
        assertEquals(40, bars.children.get(1).quantity);
        assertEquals(Long.valueOf(5), tree.recipes.get(0).baseMaterials.get("Runite ore"));
        assertEquals(Long.valueOf(40), tree.recipes.get(0).baseMaterials.get("Coal"));
        assertFalse(tree.recipes.get(0).baseMaterials.containsKey("Rune bar"));
    }

    @Test
    public void outputQuantityScalesUpToWholeCrafts() {
        ItemDetails parent = details(recipe("Fletching", 1, "", 0, 1, ing("Arrow shaft", 30)));
        ItemDetails shafts = details(recipe("Fletching", 1, "Knife", 5, 15, ing("Logs", 1)));
        Pages pages = new Pages();
        pages.known.put("arrow shaft", shafts);
        pages.known.put("logs", ItemDetails.EMPTY);

        BuildExpander.Result tree = BuildExpander.expandAll("Headless arrow", null, parent, pages);
        BuildExpander.BuildNode shaft = tree.recipes.get(0).ingredients.get(0);
        assertEquals(30, shaft.quantity);
        assertEquals(2, shaft.children.get(0).quantity);
        assertEquals(Long.valueOf(2), tree.recipes.get(0).baseMaterials.get("Logs"));
        assertEquals(Arrays.asList("arrow shaft", "logs"), pages.loaded);
    }

    @Test
    public void cyclesStopWithoutAnotherFetch() {
        ItemDetails potion = details(recipe("Herblore", 38, "", 0, 1, ing("Attack potion(3)", 1)));
        ItemDetails dose = details(recipe("Herblore", 1, "", 0, 1, ing("Attack potion(4)", 1)));
        Pages pages = new Pages();
        pages.known.put("attack potion(3)", dose);
        pages.known.put("attack potion(4)", potion);

        BuildExpander.Result tree = BuildExpander.expandAll("Attack potion(4)", 2428, potion, pages);
        BuildExpander.BuildNode child = tree.recipes.get(0).ingredients.get(0);
        assertEquals(1, child.children.size());
        assertTrue(child.children.get(0).cycle);
        assertEquals(Collections.singletonList("attack potion(3)"), pages.loaded);
        assertEquals(1, tree.fetches);
        assertFalse(tree.recipes.get(0).baseMaterials.containsKey("Attack potion(4)"));

        int loads = pages.loads;
        pages.cache.putAll(pages.known);
        BuildExpander.Result again = BuildExpander.expandAll("Attack potion(4)", 2428, potion, pages);
        assertEquals(0, again.fetches);
        assertEquals(loads, pages.loads);
        assertTrue(again.recipes.get(0).ingredients.get(0).children.get(0).cycle);
    }

    @Test
    public void sharedIdsOnThePathAreCyclesAndSharedPagesLoadOnce() {
        ItemDetails root = details(recipe("Herblore", 1, "", 0, 1, ing("Vial of water", 1), ing("Guam leaf", 1)));
        ItemDetails vial = details(recipe("Crafting", 1, "", 0, 1, ing("Noted potion", 1)));
        ItemDetails guam = details(recipe("Farming", 1, "", 0, 1, ing("Vial of water", 1)));
        Pages pages = new Pages();
        pages.ids.put("noted potion", 100);
        pages.known.put("vial of water", vial);
        pages.known.put("guam leaf", guam);
        pages.known.put("attack potion(3)", ItemDetails.EMPTY);

        BuildExpander.Result tree = BuildExpander.expandAll("Attack potion(3)", 100, root, pages);
        assertTrue(tree.recipes.get(0).ingredients.get(0).children.get(0).cycle);
        assertEquals(1, Collections.frequency(pages.loaded, "vial of water"));
        assertEquals(1, Collections.frequency(pages.loaded, "guam leaf"));
    }

    @Test
    public void depthAndNodeCapsBoundTheWalk() {
        ItemDetails link = chain("a", "b", "c", "d", "e", "f");
        Pages pages = chainPages();
        BuildExpander.Result tree = BuildExpander.expandAll("a", null, link, pages);
        assertEquals(Arrays.asList("b", "c", "d"), pages.loaded);
        BuildExpander.BuildNode deep = tree.recipes.get(0).ingredients.get(0).children.get(0).children.get(0).children.get(0);
        assertEquals("e", deep.name);
        assertTrue(deep.capped);
        assertTrue(deep.children.isEmpty());

        ItemDetails wide = details(recipe("Smithing", 1, "", 0, 1, many("ing", 25)));
        Pages leaves = new Pages();
        for (int i = 0; i < 25; i++) {
            leaves.known.put("ing" + i, ItemDetails.EMPTY);
        }
        BuildExpander.Result capped = BuildExpander.expandAll("wide", null, wide, leaves);
        assertEquals(19, leaves.loads);
        assertEquals(20, capped.uniqueNodes);
        assertTrue(capped.recipes.get(0).ingredients.get(19).capped);
        assertFalse(capped.recipes.get(0).ingredients.get(18).capped);
    }

    @Test
    public void selectedExpansionDoesNotFetchUntilAskedAndUsesTheCache() {
        ItemDetails plate = details(recipe("Smithing", 99, "Anvil", 0, 1, ing("Rune bar", 5)));
        ItemDetails bar = details(recipe("Smithing", 99, "Furnace", 0, 1, ing("Runite ore", 1)));
        Pages pages = new Pages();
        pages.known.put("rune bar", bar);
        pages.known.put("runite ore", ItemDetails.EMPTY);

        BuildExpander.Result closed = BuildExpander.expandSelected("Rune platebody", null, plate, pages, Collections.emptySet());
        assertEquals(0, closed.fetches);
        assertEquals(0, pages.loads);
        assertTrue(closed.pending.isEmpty());
        assertTrue(closed.recipes.get(0).baseMaterials.isEmpty());
        assertFalse(closed.recipes.get(0).ingredients.get(0).expanded);

        String path = closed.recipes.get(0).ingredients.get(0).path;
        BuildExpander.Result waiting = BuildExpander.expandSelected("Rune platebody", null, plate, pages, Collections.singleton(path));
        assertEquals(0, waiting.fetches);
        assertEquals(0, pages.loads);
        assertEquals(Collections.singletonList("Rune bar"), waiting.pending);
        assertTrue(waiting.recipes.get(0).ingredients.get(0).loading);

        pages.cache.put("rune bar", bar);
        pages.cache.put("runite ore", ItemDetails.EMPTY);
        BuildExpander.Result opened = BuildExpander.expandSelected("Rune platebody", null, plate, pages, Collections.singleton(path));
        assertEquals(0, opened.fetches);
        assertEquals(0, pages.loads);
        assertTrue(opened.pending.isEmpty());
        assertEquals(5, opened.recipes.get(0).ingredients.get(0).children.get(0).quantity);
        assertEquals(Long.valueOf(5), opened.recipes.get(0).baseMaterials.get("Runite ore"));
    }

    @Test
    public void parserKeepsOutputQuantity() {
        String wiki = "{{Recipe|skill1=Fletching|skill1lvl=1|skill1exp=5|outputquantity=15|mat1=Logs|mat1quantity=1}}";
        List<RecipeInfo> recipes = RecipeParser.parseAll(wiki);
        assertEquals(15, recipes.get(0).outputQuantity);
        assertEquals(1, recipes.get(0).ingredients.get(0).quantity);
        ItemDetails details = ItemDetails.from(new PageContent(wiki, "", false));
        assertEquals(15, details.recipes.get(0).outputQuantity);
    }

    private static ItemDetails chain(String... names) {
        return details(recipe("Smithing", 1, "", 0, 1, ing(names[1], 2)));
    }

    private static Pages chainPages() {
        Pages pages = new Pages();
        pages.known.put("b", details(recipe("Smithing", 1, "", 0, 1, ing("c", 3))));
        pages.known.put("c", details(recipe("Smithing", 1, "", 0, 1, ing("d", 4))));
        pages.known.put("d", details(recipe("Smithing", 1, "", 0, 1, ing("e", 1))));
        pages.known.put("e", details(recipe("Smithing", 1, "", 0, 1, ing("f", 1))));
        pages.known.put("f", ItemDetails.EMPTY);
        return pages;
    }

    private static RecipeIngredient ing(String name, int quantity) {
        return new RecipeIngredient(name, quantity);
    }

    private static RecipeIngredient[] many(String prefix, int count) {
        RecipeIngredient[] ingredients = new RecipeIngredient[count];
        for (int i = 0; i < count; i++) {
            ingredients[i] = ing(prefix + i, 1);
        }
        return ingredients;
    }

    private static RecipeInfo recipe(String skill, int level, String facility, double xp, int output, RecipeIngredient... ingredients) {
        return new RecipeInfo(skill, level, facility, xp, Arrays.asList(ingredients), output, "");
    }

    private static ItemDetails details(RecipeInfo... recipes) {
        return new ItemDetails(
            Arrays.asList(recipes),
            java.util.Optional.empty(),
            Double.NaN,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList()
        );
    }

    private static final class Pages implements RecipePages {
        final Map<String, ItemDetails> known = new HashMap<>();
        final Map<String, ItemDetails> cache = new HashMap<>();
        final Map<String, Integer> ids = new HashMap<>();
        final List<String> loaded = new java.util.ArrayList<>();
        int loads;

        @Override
        public ItemDetails cached(String itemName) {
            return cache.get(key(itemName));
        }

        @Override
        public ItemDetails load(String itemName) {
            loads++;
            String key = key(itemName);
            loaded.add(key);
            ItemDetails details = known.getOrDefault(key, ItemDetails.EMPTY);
            cache.put(key, details);
            return details;
        }

        @Override
        public Integer idFor(String itemName) {
            return ids.get(key(itemName));
        }

        private static String key(String name) {
            return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        }
    }
}
