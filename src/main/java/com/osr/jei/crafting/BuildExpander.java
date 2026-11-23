package com.osr.jei.crafting;

import com.osr.jei.model.RecipeInfo;
import com.osr.jei.model.RecipeIngredient;
import com.osr.jei.service.ItemDetails;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Expandable crafting tree. The root page is passed in and is never fetched.
 * Sub-pages are read from {@link RecipePages#cached} or, while expanding,
 * {@link RecipePages#load}. Cycles along the active path stop without another
 * request. Depth and the unique-item cap bound how far a walk can go.
 */
public final class BuildExpander {

    public static final int MAX_DEPTH = 4;
    public static final int MAX_NODES = 20;
    public static final String SEP = "\u001f";

    public static final class BuildNode {
        public final String name;
        public final long quantity;
        public final int depth;
        public final String path;
        public final boolean cycle;
        public final boolean capped;
        public final boolean loading;
        public final boolean expanded;
        /** First recipe used for this node, when it was expanded and one exists. */
        public final RecipeInfo recipe;
        /** Further recipes on the same page, not folded into the rollup. */
        public final int otherRecipes;
        public final List<BuildNode> children;
        /** False for a leaf. Unknown pages stay true until a load proves otherwise. */
        public final boolean showExpander;

        BuildNode(
            String name,
            long quantity,
            int depth,
            String path,
            boolean cycle,
            boolean capped,
            boolean loading,
            boolean expanded,
            RecipeInfo recipe,
            int otherRecipes,
            List<BuildNode> children,
            boolean showExpander
        ) {
            this.name = name;
            this.quantity = quantity;
            this.depth = depth;
            this.path = path;
            this.cycle = cycle;
            this.capped = capped;
            this.loading = loading;
            this.expanded = expanded;
            this.recipe = recipe;
            this.otherRecipes = otherRecipes;
            this.children = children;
            this.showExpander = showExpander;
        }

        public boolean expandable() {
            return !cycle && !capped && !loading && depth < MAX_DEPTH;
        }
    }

    public static final class RecipeBranch {
        public final RecipeInfo recipe;
        public final List<BuildNode> ingredients;
        /** Empty until at least one ingredient on this branch is expanded. */
        public final Map<String, Long> baseMaterials;

        RecipeBranch(RecipeInfo recipe, List<BuildNode> ingredients, Map<String, Long> baseMaterials) {
            this.recipe = recipe;
            this.ingredients = ingredients;
            this.baseMaterials = baseMaterials;
        }
    }

    public static final class Result {
        public final List<RecipeBranch> recipes;
        public final int fetches;
        public final int uniqueNodes;
        public final List<String> expandedPaths;
        /** Names whose pages still need a fetch. Empty when {@code fetchMissing} ran. */
        public final List<String> pending;

        Result(
            List<RecipeBranch> recipes,
            int fetches,
            int uniqueNodes,
            List<String> expandedPaths,
            List<String> pending
        ) {
            this.recipes = recipes;
            this.fetches = fetches;
            this.uniqueNodes = uniqueNodes;
            this.expandedPaths = expandedPaths;
            this.pending = pending;
        }
    }

    private BuildExpander() {
    }

    /** Open every eligible node, fetching pages that are not cached yet. */
    public static Result expandAll(String rootName, Integer rootId, ItemDetails root, RecipePages pages) {
        return walk(rootName, rootId, root, pages, Collections.emptySet(), true);
    }

    /**
     * Open only {@code expandedPaths}. Missing pages are listed as pending
     * and are not fetched here, so this is safe on the EDT.
     */
    public static Result expandSelected(
        String rootName,
        Integer rootId,
        ItemDetails root,
        RecipePages pages,
        Set<String> expandedPaths
    ) {
        return walk(rootName, rootId, root, pages, expandedPaths == null ? Collections.emptySet() : expandedPaths, false);
    }

    private static Result walk(
        String rootName,
        Integer rootId,
        ItemDetails root,
        RecipePages pages,
        Set<String> forcedPaths,
        boolean fetchMissing
    ) {
        Set<String> ancestors = new HashSet<>();
        ancestors.add(nameKey(rootName));
        if (rootId != null) {
            ancestors.add(idKey(rootId));
        }
        Set<String> unique = new LinkedHashSet<>();
        unique.add(norm(rootName));
        Map<String, ItemDetails> local = new HashMap<>();
        List<String> opened = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        int[] fetches = new int[1];

        List<RecipeInfo> recipes = root == null || root.recipes == null
            ? Collections.emptyList()
            : root.recipes;
        List<RecipeBranch> branches = new ArrayList<>();
        for (int i = 0; i < recipes.size(); i++) {
            RecipeInfo recipe = recipes.get(i);
            long batch = Math.max(1, recipe.outputQuantity);
            List<BuildNode> ingredients = new ArrayList<>();
            if (recipe.ingredients != null) {
                for (RecipeIngredient ingredient : recipe.ingredients) {
                    long quantity = RecipeText.scale(batch, recipe.outputQuantity, ingredient.quantity);
                    String path = i + SEP + ingredient.name;
                    ingredients.add(node(
                        ingredient.name,
                        quantity,
                        1,
                        path,
                        ancestors,
                        unique,
                        local,
                        pages,
                        forcedPaths,
                        fetchMissing,
                        fetches,
                        opened,
                        pending
                    ));
                }
            }
            branches.add(new RecipeBranch(recipe, ingredients, rollup(ingredients)));
        }
        return new Result(branches, fetches[0], unique.size(), opened, pending);
    }

    private static BuildNode node(
        String name,
        long quantity,
        int depth,
        String path,
        Set<String> ancestors,
        Set<String> unique,
        Map<String, ItemDetails> local,
        RecipePages pages,
        Set<String> forcedPaths,
        boolean fetchMissing,
        int[] fetches,
        List<String> opened,
        List<String> pending
    ) {
        String key = norm(name);
        Integer id = pages == null ? null : pages.idFor(name);
        boolean cycle = ancestors.contains(nameKey(name)) || (id != null && ancestors.contains(idKey(id)));
        if (cycle) {
            return new BuildNode(name, quantity, depth, path, true, false, false, false, null, 0, Collections.emptyList(), false);
        }
        if (depth >= MAX_DEPTH) {
            return new BuildNode(name, quantity, depth, path, false, true, false, false, null, 0, Collections.emptyList(), false);
        }

        boolean known = unique.contains(key);
        if (!known && unique.size() >= MAX_NODES) {
            return new BuildNode(name, quantity, depth, path, false, true, false, false, null, 0, Collections.emptyList(), false);
        }

        boolean want = fetchMissing || forcedPaths.contains(path);
        if (!want) {
            ItemDetails cached = pages == null ? null : pages.cached(name);
            boolean expander = cached == null ? true : hasExpandableRecipe(cached);
            return new BuildNode(name, quantity, depth, path, false, false, false, false, null, 0, Collections.emptyList(), expander);
        }

        ItemDetails details = lookup(key, name, local, pages, fetchMissing, fetches);
        if (details == null) {
            if (!pending.contains(name)) {
                pending.add(name);
            }
            return new BuildNode(name, quantity, depth, path, false, false, true, true, null, 0, Collections.emptyList(), true);
        }
        if (!known) {
            unique.add(key);
        }

        RecipeInfo chosen = firstRecipe(details);
        int others = details.recipes == null ? 0 : Math.max(0, details.recipes.size() - (chosen == null ? 0 : 1));
        opened.add(path);
        if (chosen == null || chosen.ingredients == null || chosen.ingredients.isEmpty()) {
            return new BuildNode(name, quantity, depth, path, false, false, false, true, chosen, others, Collections.emptyList(), false);
        }

        Set<String> nextAncestors = new HashSet<>(ancestors);
        nextAncestors.add(nameKey(name));
        if (id != null) {
            nextAncestors.add(idKey(id));
        }
        List<BuildNode> children = new ArrayList<>();
        for (RecipeIngredient ingredient : chosen.ingredients) {
            long childQty = RecipeText.scale(quantity, chosen.outputQuantity, ingredient.quantity);
            String childPath = path + SEP + ingredient.name;
            children.add(node(
                ingredient.name,
                childQty,
                depth + 1,
                childPath,
                nextAncestors,
                unique,
                local,
                pages,
                forcedPaths,
                fetchMissing,
                fetches,
                opened,
                pending
            ));
        }
        return new BuildNode(name, quantity, depth, path, false, false, false, true, chosen, others, children, true);
    }

    private static boolean hasExpandableRecipe(ItemDetails details) {
        if (details == null || details.recipes == null) {
            return false;
        }
        for (RecipeInfo recipe : details.recipes) {
            if (recipe != null && recipe.ingredients != null && !recipe.ingredients.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static ItemDetails lookup(
        String key,
        String name,
        Map<String, ItemDetails> local,
        RecipePages pages,
        boolean fetchMissing,
        int[] fetches
    ) {
        ItemDetails details = local.get(key);
        if (details != null) {
            return details;
        }
        if (pages != null) {
            details = pages.cached(name);
        }
        if (details != null) {
            local.put(key, details);
            return details;
        }
        if (!fetchMissing || pages == null) {
            return null;
        }
        details = pages.load(name);
        fetches[0]++;
        if (details == null) {
            details = ItemDetails.EMPTY;
        }
        local.put(key, details);
        return details;
    }

    private static RecipeInfo firstRecipe(ItemDetails details) {
        if (details == null || details.recipes == null) {
            return null;
        }
        for (RecipeInfo recipe : details.recipes) {
            if (recipe != null && recipe.ingredients != null && !recipe.ingredients.isEmpty()) {
                return recipe;
            }
        }
        return details.recipes.isEmpty() ? null : details.recipes.get(0);
    }

    static Map<String, Long> rollup(List<BuildNode> ingredients) {
        boolean anyExpanded = false;
        for (BuildNode ingredient : ingredients) {
            if (ingredient.expanded || ingredient.loading) {
                anyExpanded = true;
                break;
            }
        }
        if (!anyExpanded) {
            return Collections.emptyMap();
        }
        Map<String, Long> totals = new LinkedHashMap<>();
        for (BuildNode ingredient : ingredients) {
            collect(ingredient, totals);
        }
        return totals;
    }

    private static void collect(BuildNode node, Map<String, Long> totals) {
        if (node.children.isEmpty()) {
            totals.merge(node.name, node.quantity, Long::sum);
            return;
        }
        for (BuildNode child : node.children) {
            collect(child, totals);
        }
    }

    private static String norm(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private static String nameKey(String name) {
        return "n:" + norm(name);
    }

    private static String idKey(int id) {
        return "i:" + id;
    }
}
