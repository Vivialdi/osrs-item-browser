package com.osr.jei.wiki;

import com.osr.jei.model.RecipeInfo;
import com.osr.jei.model.RecipeIngredient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Pulls every {@code {{Recipe}}} block off an item page, not only the first. */
public final class RecipeParser {

    private RecipeParser() {
    }

    public static List<RecipeInfo> parseAll(String wikitext) {
        if (wikitext == null || wikitext.isEmpty()) {
            return Collections.emptyList();
        }
        List<RecipeInfo> recipes = new ArrayList<>();
        for (int start : WikiText.findTemplates(wikitext, "recipe")) {
            String block = WikiText.extractTemplateBlock(wikitext, start);
            if (block == null) {
                continue;
            }
            RecipeInfo info = parseBlock(block);
            if (info != null) {
                recipes.add(info);
            }
        }
        return recipes;
    }

    private static RecipeInfo parseBlock(String block) {
        Map<String, String> p = WikiText.parseParams(block);
        String skill = WikiText.clean(WikiText.coalesce(p, "skill1", "skill2", "skill"));
        int level = WikiText.firstInt(WikiText.clean(WikiText.coalesce(p, "skill1lvl", "skill2lvl", "level", "lvl")));

        String tools = WikiText.clean(WikiText.coalesce(p, "tools", "tool"));
        String facilities = WikiText.clean(WikiText.coalesce(p, "facilities", "facility", "location"));
        for (String bad : new String[]{"none", "n/a", "inventory", "no"}) {
            if (tools.equalsIgnoreCase(bad)) {
                tools = "";
            }
            if (facilities.equalsIgnoreCase(bad)) {
                facilities = "";
            }
        }
        String facility;
        if (!tools.isEmpty() && !facilities.isEmpty()) {
            facility = tools + " + " + facilities;
        } else if (!tools.isEmpty()) {
            facility = tools;
        } else {
            facility = facilities;
        }

        double xp = WikiText.firstDouble(WikiText.clean(WikiText.coalesce(p, "skill1exp", "skill2exp", "experience", "xp")));
        int outputQuantity = WikiText.firstInt(WikiText.clean(WikiText.coalesce(p, "outputquantity", "outqty")));
        if (outputQuantity <= 0) {
            outputQuantity = 1;
        }
        String outputName = WikiText.clean(WikiText.coalesce(p, "outputname", "output"));
        String skill2 = WikiText.clean(p.getOrDefault("skill2", ""));
        int level2 = WikiText.firstInt(WikiText.clean(p.getOrDefault("skill2lvl", "")));
        if (skill2.equalsIgnoreCase(skill)) {
            if (level2 > level) {
                level = level2;
            }
            skill2 = "";
            level2 = 0;
        }

        List<RecipeIngredient> ingredients = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            String mat = WikiText.clean(p.getOrDefault("mat" + i, "")).trim();
            if (mat.isEmpty() || mat.equalsIgnoreCase("none") || mat.equalsIgnoreCase("n/a")) {
                if (ingredients.isEmpty()) {
                    continue;
                }
                break;
            }
            int qty = 1;
            String qtyRaw = WikiText.clean(p.getOrDefault("mat" + i + "quantity", "1"))
                .replace(",", "")
                .replaceAll("[^0-9].*", "")
                .trim();
            if (!qtyRaw.isEmpty()) {
                try {
                    qty = Integer.parseInt(qtyRaw);
                } catch (NumberFormatException ignored) {
                    qty = 1;
                }
            }
            ingredients.add(new RecipeIngredient(mat, qty));
        }

        if (skill.isEmpty() && ingredients.isEmpty()) {
            return null;
        }
        return new RecipeInfo(skill, level, facility, xp, ingredients, outputQuantity, outputName, skill2, level2);
    }
}
