package com.osr.jei.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.osr.jei.model.RecipeInfo;
import com.osr.jei.model.RecipeIngredient;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Singleton;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches crafting recipe data from the OSRS Wiki by requesting the raw wikitext
 * for an item's page and parsing the {{Infobox Recipe}} template block.
 *
 * <p>Wikitext is cached separately from the recipe result so that other services
 * (EquipmentService, ShopService) can call {@link #getWikitext} and get the
 * already-fetched wikitext without making a second network request.
 *
 * <p>Network errors are NOT cached — the next call will retry.
 */
@Slf4j
@Singleton
public class RecipeService {

    private static final String API_BASE =
        "https://oldschool.runescape.wiki/api.php"
        + "?action=parse&prop=wikitext&format=json&redirects=true&page=";

    /**
     * Raw wikitext cache — populated by both {@link #getRecipe} and
     * {@link #getWikitext}.  Absent = never fetched; empty-string = fetched
     * but the page returned no wikitext (missing page / API error).
     */
    private final ConcurrentHashMap<String, String> wikitextCache =
        new ConcurrentHashMap<>();

    /**
     * Recipe result cache — absent = never fetched; empty Optional = fetched,
     * confirmed no recipe; Optional.of(x) = recipe found.
     */
    private final ConcurrentHashMap<String, Optional<RecipeInfo>> recipeCache =
        new ConcurrentHashMap<>();

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Returns the crafting recipe for {@code itemName}, or empty if the item has
     * no crafting recipe on the OSRS Wiki or the page couldn't be found.
     * Network errors are swallowed and return empty (not cached).
     */
    public Optional<RecipeInfo> getRecipe(String itemName) {
        String key = itemName.toLowerCase(Locale.ROOT);
        Optional<RecipeInfo> cached = recipeCache.get(key);
        if (cached != null) return cached;

        try {
            String wikitext = ensureWikitext(itemName, key);
            Optional<RecipeInfo> result = wikitext.isEmpty()
                ? Optional.empty()
                : parseWikitext(wikitext);
            recipeCache.put(key, result);
            return result;
        } catch (Exception e) {
            log.warn("[JEI] Recipe fetch failed for '{}': {}", itemName, e.getMessage());
            return Optional.empty(); // NOT cached — will retry next time
        }
    }

    /**
     * Returns the raw wikitext for {@code itemName}'s OSRS Wiki page.
     * Returns an empty string if the page doesn't exist or a network error occurs.
     *
     * <p>If {@link #getRecipe} was already called for this item the wikitext is
     * returned from cache with zero network overhead.  Otherwise it is fetched
     * now and cached for both this service and any subsequent recipe call.
     */
    public String getWikitext(String itemName) {
        String key = itemName.toLowerCase(Locale.ROOT);
        String cached = wikitextCache.get(key);
        if (cached != null) return cached;

        try {
            return ensureWikitext(itemName, key);
        } catch (Exception e) {
            log.warn("[JEI] Wikitext fetch failed for '{}': {}", itemName, e.getMessage());
            return "";
        }
    }

    // ── Internal helpers ───────────────────────────────────────────────────────

    /**
     * Returns cached wikitext if present, otherwise fetches it, stores it in
     * {@link #wikitextCache}, and returns it.  Guaranteed to populate the cache
     * entry on success (even if the wikitext is empty).
     */
    private String ensureWikitext(String itemName, String key) throws Exception {
        String cached = wikitextCache.get(key);
        if (cached != null) return cached;

        String wikitext = fetchWikitext(itemName);
        wikitextCache.put(key, wikitext); // always cache, even empty-string
        return wikitext;
    }

    // ── Network ────────────────────────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    private String fetchWikitext(String itemName) throws Exception {
        String encoded = URLEncoder.encode(itemName.replace(" ", "_"), "UTF-8");
        URL url = new URL(API_BASE + encoded);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(6_000);
        conn.setReadTimeout(12_000);
        conn.setRequestProperty("User-Agent", "osrs-jei-plugin");

        try (InputStreamReader reader = new InputStreamReader(
                conn.getInputStream(), StandardCharsets.UTF_8)) {

            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            if (!root.has("parse")) return ""; // missing page / API error
            return root.getAsJsonObject("parse")
                .getAsJsonObject("wikitext")
                .get("*").getAsString();
        } finally {
            conn.disconnect();
        }
    }

    // ── Wikitext parsing ───────────────────────────────────────────────────────

    private Optional<RecipeInfo> parseWikitext(String wikitext) {
        // The OSRS Wiki uses {{Recipe|...}} — case-insensitive search
        String lower = wikitext.toLowerCase(Locale.ROOT);
        int startIdx = lower.indexOf("{{recipe\n");
        if (startIdx == -1) startIdx = lower.indexOf("{{recipe|");
        if (startIdx == -1) startIdx = lower.indexOf("{{recipe ");
        if (startIdx == -1) {
            log.info("[JEI] No {{Recipe}} template found in wikitext");
            return Optional.empty();
        }

        String block = extractTemplateBlock(wikitext, startIdx);
        if (block == null) {
            log.warn("[JEI] Found Recipe template start but could not extract block");
            return Optional.empty();
        }

        Map<String, String> p = parseParams(block);
        log.debug("[JEI] Recipe params: {}", p.keySet());

        // ── Skill (skill1 is the main field; skill2 exists for dual-skill recipes) ──
        String skill = clean(coalesce(p, "skill1", "skill2", "skill"));

        // ── Level ──────────────────────────────────────────────────────────────
        int level = firstInt(clean(coalesce(p, "skill1lvl", "skill2lvl", "level", "lvl")));

        // ── Facility — combine tools + facilities when both are present ─────────
        String tools      = clean(coalesce(p, "tools", "tool"));
        String facilities = clean(coalesce(p, "facilities", "facility", "location"));
        for (String bad : new String[]{"none", "n/a", "inventory", "no"}) {
            if (tools.equalsIgnoreCase(bad))      tools      = "";
            if (facilities.equalsIgnoreCase(bad)) facilities = "";
        }
        String facility;
        if (!tools.isEmpty() && !facilities.isEmpty()) {
            facility = tools + " + " + facilities;
        } else if (!tools.isEmpty()) {
            facility = tools;
        } else {
            facility = facilities;
        }

        // ── XP ─────────────────────────────────────────────────────────────────
        double xp = firstDouble(clean(coalesce(p, "skill1exp", "skill2exp", "experience", "xp")));

        // ── Ingredients — mat1/mat1quantity … mat10/mat10quantity ─────────────
        List<RecipeIngredient> ingredients = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            String mat = clean(p.getOrDefault("mat" + i, "")).trim();
            if (mat.isEmpty() || mat.equalsIgnoreCase("none")
                    || mat.equalsIgnoreCase("n/a")) break;

            int qty = 1;
            String qtyRaw = clean(p.getOrDefault("mat" + i + "quantity", "1"))
                .replace(",", "").replaceAll("[^0-9].*", "").trim();
            if (!qtyRaw.isEmpty()) {
                try { qty = Integer.parseInt(qtyRaw); } catch (NumberFormatException ignored) {}
            }
            ingredients.add(new RecipeIngredient(mat, qty));
        }

        if (skill.isEmpty() && ingredients.isEmpty()) {
            log.info("[JEI] Recipe template found but no skill/ingredients extracted");
            return Optional.empty();
        }

        log.info("[JEI] Recipe parsed: skill={} lv={} facility='{}' ingredients={}",
            skill, level, facility, ingredients.size());
        return Optional.of(new RecipeInfo(skill, level, facility, xp, ingredients));
    }

    // ── Wikitext template utilities ────────────────────────────────────────────

    private String extractTemplateBlock(String text, int startIdx) {
        int depth = 0;
        int i = startIdx;
        while (i < text.length() - 1) {
            char c    = text.charAt(i);
            char next = text.charAt(i + 1);
            if      (c == '{' && next == '{') { depth++; i += 2; }
            else if (c == '}' && next == '}') {
                depth--;
                if (depth == 0) return text.substring(startIdx, i + 2);
                i += 2;
            } else {
                i++;
            }
        }
        return null;
    }

    private List<String> splitTopLevel(String text) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (i + 1 < text.length()) {
                char nx = text.charAt(i + 1);
                if ((c == '{' && nx == '{') || (c == '[' && nx == '[')) { depth++; i++; continue; }
                if ((c == '}' && nx == '}') || (c == ']' && nx == ']')) { depth--; i++; continue; }
            }
            if (c == '|' && depth == 0) {
                parts.add(text.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(text.substring(start));
        return parts;
    }

    private Map<String, String> parseParams(String block) {
        String inner = block;
        if (inner.startsWith("{{")) inner = inner.substring(2);
        if (inner.endsWith("}}"))   inner = inner.substring(0, inner.length() - 2);

        Map<String, String> params = new LinkedHashMap<>();
        List<String> parts = splitTopLevel(inner);
        for (int i = 1; i < parts.size(); i++) {
            String part = parts.get(i).replace("\n", " ").trim();
            if (part.isEmpty()) continue;

            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            String key = part.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String val = part.substring(eq + 1).trim();
            if (!key.isEmpty()) params.put(key, val);
        }
        return params;
    }

    private String coalesce(Map<String, String> params, String... keys) {
        for (String key : keys) {
            String v = params.get(key);
            if (v != null && !v.trim().isEmpty()) return v;
        }
        return "";
    }

    // ── Wiki markup stripping ──────────────────────────────────────────────────

    private static final Pattern WIKILINK  = Pattern.compile("\\[\\[(?:[^|\\]]*\\|)?([^\\]]+)\\]\\]");
    private static final Pattern TEMPLATE1 = Pattern.compile("\\{\\{[^{}|]*\\|([^{}|]*)\\}\\}");
    private static final Pattern TEMPLATE2 = Pattern.compile("\\{\\{[^{}]*\\}\\}");
    private static final Pattern EXTLINK   = Pattern.compile("\\[https?://\\S+\\s([^\\]]+)\\]");
    private static final Pattern HTML_TAG  = Pattern.compile("<[^>]+>");

    private String clean(String text) {
        if (text == null || text.trim().isEmpty()) return "";
        text = WIKILINK.matcher(text).replaceAll("$1");
        text = TEMPLATE1.matcher(text).replaceAll("$1");
        text = TEMPLATE2.matcher(text).replaceAll("");
        text = EXTLINK.matcher(text).replaceAll("$1");
        text = text.replaceAll("'{2,}", "");
        text = text.replace("&amp;", "&").replace("&lt;", "<")
                   .replace("&gt;", ">").replace("&nbsp;", " ").replace("&#160;", " ");
        text = HTML_TAG.matcher(text).replaceAll("");
        return text.replaceAll("\\s+", " ").trim();
    }

    // ── Number helpers ─────────────────────────────────────────────────────────

    private static final Pattern DIGITS  = Pattern.compile("\\d+");
    private static final Pattern DECIMAL = Pattern.compile("[0-9]+(?:\\.[0-9]+)?");

    private int firstInt(String text) {
        if (text == null || text.isEmpty()) return 0;
        Matcher m = DIGITS.matcher(text.replace(",", ""));
        return m.find() ? Integer.parseInt(m.group()) : 0;
    }

    private double firstDouble(String text) {
        if (text == null || text.isEmpty()) return 0;
        Matcher m = DECIMAL.matcher(text.replace(",", ""));
        return m.find() ? Double.parseDouble(m.group()) : 0;
    }
}
