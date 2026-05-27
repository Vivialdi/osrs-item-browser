package com.osr.jei.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.osr.jei.model.DropEntry;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Singleton;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches drop-table data from the OSRS Wiki.
 *
 * Strategy:
 *  1. GET action=parse&prop=sections to find which section index holds "Item sources".
 *  2. GET action=parse&prop=text&section=N to retrieve the rendered HTML for that section.
 *  3. Locate the table whose class includes "item-drops" and parse each <tr>.
 *
 * The rendered table always has columns: Source | Level | Quantity | Rarity.
 * The Rarity column contains the display rate (e.g. "Always", "1/128 (0.78%)").
 *
 * Results are cached per item name.  Network errors are NOT cached.
 */
@Slf4j
@Singleton
public class WikiService {

    private static final String WIKI_API   = "https://oldschool.runescape.wiki/api.php";
    private static final String USER_AGENT = "OSRS-JEI-RuneLite-Plugin/1.0";

    private final ConcurrentHashMap<String, List<DropEntry>> dropCache = new ConcurrentHashMap<>();

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Returns every monster/source that drops {@code itemName}, or an empty list
     * if the item has no "Item sources" section or if the request fails.
     * Call this on a background thread.
     */
    @SuppressWarnings("deprecation")
    public List<DropEntry> getDropSources(String itemName) {
        if (dropCache.containsKey(itemName)) return dropCache.get(itemName);

        List<DropEntry> drops = new ArrayList<>();
        try {
            String encoded = URLEncoder.encode(itemName.replace(" ", "_"), "UTF-8");

            // ── 1. Find the "Item sources" (or "Sources") section index ───────
            int sectionIndex = findItemSourcesSection(encoded);
            if (sectionIndex < 0) {
                log.info("[JEI] No 'Item sources' section for '{}'", itemName);
                dropCache.put(itemName, drops);
                return drops;
            }

            // ── 2. Fetch rendered HTML for that section ────────────────────────
            String textUrl = WIKI_API
                + "?action=parse&prop=text&format=json&section=" + sectionIndex
                + "&page=" + encoded;

            JsonObject textRoot = fetchJson(textUrl);
            if (textRoot == null || !textRoot.has("parse")) {
                dropCache.put(itemName, drops);
                return drops;
            }

            String html = textRoot.getAsJsonObject("parse")
                .getAsJsonObject("text")
                .get("*").getAsString();

            // ── 3. Parse the item-drops table ──────────────────────────────────
            drops = parseItemDropsTable(html);
            log.info("[JEI] Drops for '{}': {} entries", itemName, drops.size());
            dropCache.put(itemName, drops);

        } catch (Exception e) {
            log.warn("[JEI] Failed to fetch drops for '{}': {}", itemName, e.getMessage());
            // NOT cached — will retry next time
        }
        return drops;
    }

    // ── Section finder ─────────────────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    private int findItemSourcesSection(String encodedName) throws Exception {
        String url = WIKI_API
            + "?action=parse&prop=sections&format=json&redirects=true&page=" + encodedName;

        JsonObject root = fetchJson(url);
        if (root == null || !root.has("parse")) return -1;

        JsonArray sections = root.getAsJsonObject("parse").getAsJsonArray("sections");
        if (sections == null) return -1;

        for (JsonElement el : sections) {
            JsonObject sec   = el.getAsJsonObject();
            // "line" may contain HTML (e.g. <span>); strip it before comparing
            String title = stripHtml(sec.get("line").getAsString()).toLowerCase();
            if (title.equals("item sources") || title.equals("sources")
                    || title.equals("drop sources") || title.equals("drops")) {
                return Integer.parseInt(sec.get("index").getAsString());
            }
        }
        return -1;
    }

    // ── HTML table parser ──────────────────────────────────────────────────────

    /**
     * Finds the first table whose class contains "item-drops" and extracts one
     * {@link DropEntry} per data row.
     *
     * Expected columns (0-based): 0=Source, 1=Level, 2=Quantity, 3=Rarity.
     * The Rarity cell already contains the rate fraction, so we store it as the
     * rate field and leave rarity blank to avoid duplication in the UI.
     */
    private List<DropEntry> parseItemDropsTable(String html) {
        List<DropEntry> drops = new ArrayList<>();

        // Find the start of the item-drops table
        int markerIdx = html.indexOf("item-drops");
        if (markerIdx < 0) {
            log.debug("[JEI] No item-drops table found in section HTML");
            return drops;
        }

        // Walk back to the opening <table tag
        int tStart = html.lastIndexOf("<table", markerIdx);
        if (tStart < 0) return drops;

        // Walk forward to the closing </table>
        int tEnd = html.indexOf("</table>", markerIdx);
        if (tEnd < 0) return drops;
        String table = html.substring(tStart, tEnd + 8);

        // Use tbody content if present so we skip <thead> header rows
        int tbodyStart = table.indexOf("<tbody>");
        if (tbodyStart >= 0) {
            int tbodyEnd = table.indexOf("</tbody>", tbodyStart);
            table = tbodyEnd >= 0
                ? table.substring(tbodyStart, tbodyEnd + 8)
                : table.substring(tbodyStart);
        }

        // Iterate <tr> elements
        int pos = 0;
        while (true) {
            int rowStart = table.indexOf("<tr", pos);
            if (rowStart < 0) break;
            int rowEnd = table.indexOf("</tr>", rowStart);
            if (rowEnd < 0) break;
            String row = table.substring(rowStart, rowEnd + 5);
            pos = rowEnd + 5;

            // Skip header rows (contain <th> but no <td>)
            if (!row.contains("<td")) continue;

            List<String> cells = extractCells(row);
            if (cells.size() < 3) continue;

            String source   = stripHtml(cells.get(0));              // monster name
            String level    = stripHtml(cells.size() > 1 ? cells.get(1) : ""); // combat level
            String quantity = stripHtml(cells.size() > 2 ? cells.get(2) : "");
            // Rarity column contains the rate fraction — use it as rate
            String rate     = stripHtml(cells.size() > 3 ? cells.get(3) : "");

            if (source.isEmpty()) continue;

            DropEntry drop = new DropEntry();
            drop.setSource(source);
            drop.setLevel(level);
            drop.setQuantity(quantity.isEmpty() ? "?" : quantity);
            drop.setRate(rate);
            drop.setRarity(""); // rate already in the rate field
            drops.add(drop);
        }
        return drops;
    }

    // ── HTML utilities ─────────────────────────────────────────────────────────

    /** Extracts the inner HTML of each top-level &lt;td&gt; in a table row. */
    private List<String> extractCells(String row) {
        List<String> cells = new ArrayList<>();
        int pos = 0;
        while (true) {
            int start   = row.indexOf("<td", pos);
            if (start < 0) break;
            int closeGt = row.indexOf(">", start);
            if (closeGt < 0) break;
            int end     = row.indexOf("</td>", closeGt);
            if (end < 0) break;
            cells.add(row.substring(closeGt + 1, end));
            pos = end + 5;
        }
        return cells;
    }

    /** Strips all HTML tags and decodes common entities, returning plain text. */
    private String stripHtml(String html) {
        if (html == null || html.isEmpty()) return "";
        String text = html.replaceAll("<[^>]+>", "");
        text = text.replace("&amp;",  "&")
                   .replace("&lt;",   "<")
                   .replace("&gt;",   ">")
                   .replace("&nbsp;", " ")
                   .replace("&#160;", " ")
                   .replace("&ndash;","–")
                   .replace("&mdash;","—");
        return text.trim().replaceAll("\\s+", " ");
    }

    // ── Network ────────────────────────────────────────────────────────────────

    private JsonObject fetchJson(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(6_000);
            conn.setReadTimeout(15_000);

            int code = conn.getResponseCode();
            if (code != 200) {
                log.warn("[JEI] HTTP {} for {}", code, urlStr);
                return null;
            }

            try (InputStreamReader reader = new InputStreamReader(
                    conn.getInputStream(), StandardCharsets.UTF_8)) {
                return new JsonParser().parse(reader).getAsJsonObject();
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            log.warn("[JEI] Request failed: {}", e.getMessage());
            return null;
        }
    }
}
