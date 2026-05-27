package com.osr.jei.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.osr.jei.model.MonsterInfo;
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
 * Fetches monster location data from the OSRS Wiki.
 *
 * <p>One API call per unique monster (cached forever after first lookup):
 * wikitext ({@code prop=wikitext}) parsed for combat level via
 * {@code {{Infobox Monster}}} and location names via {@code {{LocLine}}} blocks.
 */
@Slf4j
@Singleton
public class MonsterService {

    private static final String WIKI_API   = "https://oldschool.runescape.wiki/api.php";
    private static final String USER_AGENT = "osrs-jei-plugin";

    /** absent = never fetched; empty Optional = fetched, no useful data found. */
    private final ConcurrentHashMap<String, Optional<MonsterInfo>> cache =
        new ConcurrentHashMap<>();

    // ── Public API ─────────────────────────────────────────────────────────────

    public Optional<MonsterInfo> getMonsterInfo(String monsterName) {
        String key = monsterName.toLowerCase(Locale.ROOT);
        Optional<MonsterInfo> cached = cache.get(key);
        if (cached != null) return cached;

        try {
            Optional<MonsterInfo> result = fetchAndParse(monsterName);
            cache.put(key, result);
            return result;
        } catch (Exception e) {
            log.warn("[JEI] Monster info fetch failed for '{}': {}", monsterName, e.getMessage());
            return Optional.empty(); // NOT cached — will retry
        }
    }

    // ── Network ────────────────────────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    private Optional<MonsterInfo> fetchAndParse(String monsterName) throws Exception {
        String encoded = URLEncoder.encode(monsterName.replace(" ", "_"), "UTF-8");

        JsonObject wikitextRoot = fetchJson(WIKI_API
            + "?action=parse&prop=wikitext&format=json&redirects=true&page=" + encoded);
        if (!wikitextRoot.has("parse")) return Optional.empty();

        String wikitext = wikitextRoot.getAsJsonObject("parse")
            .getAsJsonObject("wikitext").get("*").getAsString();

        int          combat    = parseCombatLevel(wikitext);
        List<String> locations = parseLocLines(wikitext);

        log.debug("[JEI] Monster '{}': combat={} locations={}", monsterName, combat, locations);

        if (locations.isEmpty() && combat == 0) return Optional.empty();

        return Optional.of(new MonsterInfo(monsterName, combat, locations));
    }

    @SuppressWarnings("deprecation")
    private JsonObject fetchJson(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(6_000);
        conn.setReadTimeout(12_000);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        try (InputStreamReader reader = new InputStreamReader(
                conn.getInputStream(), StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader).getAsJsonObject();
        } finally {
            conn.disconnect();
        }
    }

    // ── Wikitext parsing ───────────────────────────────────────────────────────

    private int parseCombatLevel(String wikitext) {
        String lower = wikitext.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf("{{infobox monster");
        if (idx < 0) return 0;
        String block = extractTemplateBlock(wikitext, idx);
        if (block == null) return 0;
        Map<String, String> params = parseParams(block);
        return firstInt(clean(coalesce(params, "combat1", "combat", "combat2")));
    }

    private List<String> parseLocLines(String wikitext) {
        Set<String> seen = new LinkedHashSet<>();
        String lower = wikitext.toLowerCase(Locale.ROOT);
        int pos = 0;
        while (true) {
            int idx = lower.indexOf("{{locline", pos);
            if (idx < 0) break;
            String block = extractTemplateBlock(wikitext, idx);
            if (block == null) { pos = idx + 9; continue; }
            Map<String, String> params = parseParams(block);
            String location = clean(params.getOrDefault("location", ""));
            if (!location.isEmpty()
                    && !location.equalsIgnoreCase("none")
                    && !location.equalsIgnoreCase("n/a")) {
                seen.add(location);
            }
            pos = idx + block.length();
        }
        return new ArrayList<>(seen);
    }

    // ── Wikitext template utilities ────────────────────────────────────────────

    private String extractTemplateBlock(String text, int startIdx) {
        int depth = 0, i = startIdx;
        while (i < text.length() - 1) {
            char c = text.charAt(i), next = text.charAt(i + 1);
            if      (c == '{' && next == '{') { depth++; i += 2; }
            else if (c == '}' && next == '}') {
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

    private static final Pattern DIGITS = Pattern.compile("\\d+");

    private int firstInt(String text) {
        if (text == null || text.isEmpty()) return 0;
        Matcher m = DIGITS.matcher(text.replace(",", ""));
        return m.find() ? Integer.parseInt(m.group()) : 0;
    }
}
