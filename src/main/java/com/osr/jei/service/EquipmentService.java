package com.osr.jei.service;

import com.osr.jei.model.EquipmentStats;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses equipment bonuses ({@code {{Infobox Bonuses}}}) and item weight
 * ({@code {{Infobox Item|weight=}}}) from wikitext that {@link RecipeService}
 * already fetches and caches.
 *
 * <p><b>Zero extra network calls.</b>  By the time the panel calls
 * {@code getStats(itemName)}, {@code RecipeService.getRecipe(itemName)} will
 * already have populated the shared wikitext cache — this service just parses
 * the cached string.
 *
 * <p>Network errors and items with no {@code {{Infobox Bonuses}}} both return
 * {@code Optional.empty()}.  Results are cached per item name.
 */
@Slf4j
@Singleton
public class EquipmentService {

    @Inject private RecipeService recipeService;

    /** absent = never checked; empty Optional = no Infobox Bonuses on page. */
    private final ConcurrentHashMap<String, Optional<EquipmentStats>> statsCache =
        new ConcurrentHashMap<>();

    /** Weight in kg; {@code Double.NaN} = not found or item has no weight field. */
    private final ConcurrentHashMap<String, Double> weightCache =
        new ConcurrentHashMap<>();

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Returns the equipment stats for {@code itemName}, or empty if the item's
     * wiki page has no {@code {{Infobox Bonuses}}} template.
     */
    public Optional<EquipmentStats> getStats(String itemName) {
        String key = itemName.toLowerCase(Locale.ROOT);
        Optional<EquipmentStats> cached = statsCache.get(key);
        if (cached != null) return cached;

        try {
            String wikitext = recipeService.getWikitext(itemName);
            Optional<EquipmentStats> result = parseStats(wikitext);
            statsCache.put(key, result);

            // Parse weight from the same wikitext while we have it
            weightCache.put(key, parseWeight(wikitext));

            log.debug("[JEI] EquipmentStats for '{}': {}", itemName,
                result.isPresent() ? "found (slot=" + result.get().slot + ")" : "none");
            return result;

        } catch (Exception e) {
            log.warn("[JEI] EquipmentService failed for '{}': {}", itemName, e.getMessage());
            return Optional.empty(); // NOT cached — will retry
        }
    }

    /**
     * Returns the item weight in kg, or {@link Double#NaN} if unavailable.
     *
     * <p>This is always populated as a side-effect of {@link #getStats}, so
     * call that first.  If you call this before {@code getStats} it will
     * trigger a parse automatically (still zero extra network calls).
     */
    public double getWeight(String itemName) {
        String key = itemName.toLowerCase(Locale.ROOT);
        Double cached = weightCache.get(key);
        if (cached != null) return cached;

        // Trigger full parse — populates weightCache as a side effect
        getStats(itemName);
        return weightCache.getOrDefault(key, Double.NaN);
    }

    // ── Wikitext parsing ───────────────────────────────────────────────────────

    private Optional<EquipmentStats> parseStats(String wikitext) {
        if (wikitext == null || wikitext.isEmpty()) return Optional.empty();

        String lower = wikitext.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf("{{infobox bonuses");
        if (idx < 0) return Optional.empty();

        String block = extractTemplateBlock(wikitext, idx);
        if (block == null) return Optional.empty();

        Map<String, String> p = parseParams(block);

        int astab  = signedInt(p.getOrDefault("astab",  "0"));
        int aslash = signedInt(p.getOrDefault("aslash", "0"));
        int acrush = signedInt(p.getOrDefault("acrush", "0"));
        int amagic = signedInt(p.getOrDefault("amagic", "0"));
        int arange = signedInt(p.getOrDefault("arange", "0"));
        int dstab  = signedInt(p.getOrDefault("dstab",  "0"));
        int dslash = signedInt(p.getOrDefault("dslash", "0"));
        int dcrush = signedInt(p.getOrDefault("dcrush", "0"));
        int dmagic = signedInt(p.getOrDefault("dmagic", "0"));
        int drange = signedInt(p.getOrDefault("drange", "0"));
        int str    = signedInt(p.getOrDefault("str",    "0"));
        int rstr   = signedInt(p.getOrDefault("rstr",   "0"));
        // mdmg may be "1%" or "+1%" — strip non-numeric prefix/suffix
        int mdmg   = signedInt(stripPercent(p.getOrDefault("mdmg", "0")));
        int prayer = signedInt(p.getOrDefault("prayer", "0"));
        String slot = clean(p.getOrDefault("slot", ""));
        int speed  = firstPositiveInt(p.getOrDefault("aspeed",
                        p.getOrDefault("speed", "0")));

        // A page can include a bare {{Infobox Bonuses}} with all zeros
        // (e.g. clothing items).  Include it only if the slot is named.
        if (slot.isEmpty()) return Optional.empty();

        return Optional.of(new EquipmentStats(
            astab, aslash, acrush, amagic, arange,
            dstab, dslash, dcrush, dmagic, drange,
            str, rstr, mdmg, prayer, slot, speed));
    }

    private double parseWeight(String wikitext) {
        if (wikitext == null || wikitext.isEmpty()) return Double.NaN;

        String lower = wikitext.toLowerCase(Locale.ROOT);
        // {{Infobox Item}} (or {{Infobox item}}) holds the weight field
        int idx = lower.indexOf("{{infobox item");
        if (idx < 0) return Double.NaN;

        String block = extractTemplateBlock(wikitext, idx);
        if (block == null) return Double.NaN;

        Map<String, String> p = parseParams(block);
        String raw = clean(p.getOrDefault("weight", ""));
        if (raw.isEmpty() || raw.equals("0")) return 0.0;

        try {
            return Double.parseDouble(raw.replace(",", "."));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    // ── Wikitext template utilities ────────────────────────────────────────────

    private String extractTemplateBlock(String text, int startIdx) {
        int depth = 0, i = startIdx;
        while (i < text.length() - 1) {
            char c = text.charAt(i), nx = text.charAt(i + 1);
            if      (c == '{' && nx == '{') { depth++; i += 2; }
            else if (c == '}' && nx == '}') {
                depth--;
                if (depth == 0) return text.substring(startIdx, i + 2);
                i += 2;
            } else { i++; }
        }
        return null;
    }

    private List<String> splitTopLevel(String text) {
        List<String> parts = new ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (i + 1 < text.length()) {
                char nx = text.charAt(i + 1);
                if ((c == '{' && nx == '{') || (c == '[' && nx == '[')) { depth++; i++; continue; }
                if ((c == '}' && nx == '}') || (c == ']' && nx == ']')) { depth--; i++; continue; }
            }
            if (c == '|' && depth == 0) { parts.add(text.substring(start, i)); start = i + 1; }
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

    // ── Wiki markup stripping ──────────────────────────────────────────────────

    private static final Pattern WIKILINK  = Pattern.compile("\\[\\[(?:[^|\\]]*\\|)?([^\\]]+)\\]\\]");
    private static final Pattern TEMPLATE1 = Pattern.compile("\\{\\{[^{}|]*\\|([^{}|]*)\\}\\}");
    private static final Pattern TEMPLATE2 = Pattern.compile("\\{\\{[^{}]*\\}\\}");
    private static final Pattern HTML_TAG  = Pattern.compile("<[^>]+>");

    private String clean(String text) {
        if (text == null || text.trim().isEmpty()) return "";
        text = WIKILINK.matcher(text).replaceAll("$1");
        text = TEMPLATE1.matcher(text).replaceAll("$1");
        text = TEMPLATE2.matcher(text).replaceAll("");
        text = text.replaceAll("'{2,}", "");
        text = text.replace("&amp;", "&").replace("&lt;", "<")
                   .replace("&gt;", ">").replace("&nbsp;", " ").replace("&#160;", " ");
        text = HTML_TAG.matcher(text).replaceAll("");
        return text.replaceAll("\\s+", " ").trim();
    }

    // ── Number helpers ─────────────────────────────────────────────────────────

    private static final Pattern SIGNED_INT = Pattern.compile("[+-]?\\d+");
    private static final Pattern DIGITS     = Pattern.compile("\\d+");

    /**
     * Parses the first signed integer from a wikitext value string.
     * Handles leading +/-, trailing garbage (units, wiki templates).
     */
    private int signedInt(String raw) {
        if (raw == null || raw.isEmpty()) return 0;
        // Strip wiki markup first
        String cleaned = clean(raw);
        Matcher m = SIGNED_INT.matcher(cleaned.replace(",", ""));
        return m.find() ? Integer.parseInt(m.group()) : 0;
    }

    private int firstPositiveInt(String raw) {
        if (raw == null || raw.isEmpty()) return 0;
        Matcher m = DIGITS.matcher(raw.replace(",", ""));
        return m.find() ? Integer.parseInt(m.group()) : 0;
    }

    private String stripPercent(String s) {
        return s == null ? "0" : s.replace("%", "").trim();
    }
}
