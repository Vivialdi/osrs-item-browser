package com.osr.jei.wiki;

import com.osr.jei.model.RecipeInfo;
import com.osr.jei.model.RecipeIngredient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A page can list recipes for several items. Crafting keeps the recipes whose
 * product is the item being viewed. A recipe whose product is something else
 * is a use of this item when this item is one of its materials.
 */
public final class RecipeUses {

    public static final class Split {
        public final List<RecipeInfo> crafting;
        public final List<String> otherProducts;

        Split(List<RecipeInfo> crafting, List<String> otherProducts) {
            this.crafting = crafting;
            this.otherProducts = otherProducts;
        }
    }

    private RecipeUses() {
    }

    /** Infobox name, or {@code name1} on a versioned item page. */
    public static String pageSubject(String wikitext) {
        if (wikitext == null || wikitext.isEmpty()) {
            return "";
        }
        List<Integer> hits = WikiText.findTemplates(wikitext, "infobox item");
        if (hits.isEmpty()) {
            return "";
        }
        String block = WikiText.extractTemplateBlock(wikitext, hits.get(0));
        Map<String, String> params = WikiText.parseParams(block);
        return WikiText.clean(WikiText.coalesce(params, "name", "name1"));
    }

    /**
     * @param viewedName item the player opened. Empty keeps every recipe.
     * @param pageSubject infobox name. Empty means the viewed name is the page item.
     */
    public static Split split(List<RecipeInfo> recipes, String viewedName, String pageSubject) {
        List<RecipeInfo> all = recipes == null ? Collections.emptyList() : recipes;
        String viewed = key(viewedName);
        if (viewed.isEmpty()) {
            return new Split(new ArrayList<>(all), Collections.emptyList());
        }
        String subject = key(pageSubject);
        boolean viewedIsSubject = subject.isEmpty() || subject.equals(viewed);
        boolean pageNamesViewed = false;
        for (RecipeInfo recipe : all) {
            if (names(recipe, viewed)) {
                pageNamesViewed = true;
                break;
            }
        }
        // A version such as Iron dagger(p) shares the base page and is named as some recipe's product.
        boolean variant = pageNamesViewed && !viewedIsSubject;
        List<RecipeInfo> crafting = new ArrayList<>();
        List<String> uses = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (RecipeInfo recipe : all) {
            if (recipe == null) {
                continue;
            }
            if (produces(recipe, viewed, subject, variant)) {
                crafting.add(recipe);
            }
            if (!usesMaterial(recipe, viewed)) {
                continue;
            }
            for (String output : named(recipe)) {
                String id = key(output);
                if (id.isEmpty() || id.equals(viewed) || !seen.add(id)) {
                    continue;
                }
                uses.add(output.trim());
            }
        }
        return new Split(crafting, uses);
    }

    /** HTML products first, then recipe products that are not already listed. */
    public static List<String> mergeProducts(List<String> existing, List<String> extra) {
        List<String> merged = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        add(merged, seen, existing);
        add(merged, seen, extra);
        return merged;
    }

    private static void add(List<String> merged, Set<String> seen, List<String> names) {
        if (names == null) {
            return;
        }
        for (String name : names) {
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            if (seen.add(key(name))) {
                merged.add(name.trim());
            }
        }
    }

    private static boolean produces(RecipeInfo recipe, String viewed, String subject, boolean variant) {
        for (String output : named(recipe)) {
            if (key(output).equals(viewed)) {
                return true;
            }
        }
        if (variant) {
            return false;
        }
        List<String> outputs = named(recipe);
        if (outputs.isEmpty()) {
            return true;
        }
        if (subject.isEmpty()) {
            return false;
        }
        for (String output : outputs) {
            if (key(output).equals(subject)) {
                return true;
            }
        }
        return false;
    }

    private static boolean names(RecipeInfo recipe, String viewed) {
        if (recipe == null || viewed.isEmpty()) {
            return false;
        }
        for (String output : named(recipe)) {
            if (key(output).equals(viewed)) {
                return true;
            }
        }
        return false;
    }

    private static boolean usesMaterial(RecipeInfo recipe, String viewed) {
        if (recipe.ingredients == null || viewed.isEmpty()) {
            return false;
        }
        for (RecipeIngredient ingredient : recipe.ingredients) {
            if (ingredient != null && key(ingredient.name).equals(viewed)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> named(RecipeInfo recipe) {
        if (recipe.outputs != null && !recipe.outputs.isEmpty()) {
            return recipe.outputs;
        }
        if (recipe.outputName != null && !recipe.outputName.isEmpty()) {
            return Collections.singletonList(recipe.outputName);
        }
        return Collections.emptyList();
    }

    static String key(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        String cleaned = WikiText.clean(name).replace('_', ' ').trim().toLowerCase(Locale.ROOT);
        int hash = cleaned.indexOf('#');
        if (hash >= 0) {
            cleaned = cleaned.substring(0, hash).trim();
        }
        return cleaned.replaceAll("\\s+", " ");
    }
}
