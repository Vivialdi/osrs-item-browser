package com.osr.jei.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.osr.jei.model.DropEntry;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URLEncoder;
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
 * Results are cached per item name. Network errors are NOT cached.
 */
@Slf4j
@Singleton
public class WikiService {

    private static final String WIKI_API   = "https://oldschool.runescape.wiki/api.php";
    private static final String USER_AGENT = "OSRS-JEI-RuneLite-Plugin/1.0";

    @Inject private OkHttpClient okHttpClient;

    private final ConcurrentHashMap<String, List<DropEntry>> dropCache = new ConcurrentHashMap<>();

    // ── Public API ─────────────────────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    public List<DropEntry> getDropSources(String itemName) {
        if (dropCache.containsKey(itemName)) return dropCache.get(itemName);

        List<DropEntry> drops = new ArrayList<>();
        try {
            String encoded = URLEncoder.encode(itemName.replace(" ", "_"), "UTF-8");

            int sectionIndex = findItemSourcesSection(encoded);
            if (sectionIndex < 0) {
                log.info("[JEI] No 'Item sources' section for '{}'", itemName);
                dropCache.put(itemName, drops);
                return drops;
            }

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

            drops = parseItemDropsTable(html);
            log.info("[JEI] Drops for '{}': {} entries", itemName, drops.size());
            dropCache.put(itemName, drops);

        } catch (Exception e) {
            log.warn("[JEI] Failed to fetch drops for '{}': {}", itemName, e.getMessage());
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
            String title = stripHtml(sec.get("line").getAsString()).toLowerCase();
            if (title.equals("item sources") || title.equals("sources")
                    || title.equals("drop sources") || title.equals("drops")) {
                return Integer.parseInt(sec.get("index").getAsString());
            }
        }
        return -1;
    }

    // ── HTML table parser ──────────────────────────────────────────────────────

    private List<DropEntry> parseItemDropsTable(String html) {
        List<DropEntry> drops = new ArrayList<>();

        int markerIdx = html.indexOf("item-drops");
        if (markerIdx < 0) {
            log.debug("[JEI] No item-drops table found in section HTML");
            return drops;
        }

        int tStart = html.lastIndexOf("<table", markerIdx);
        if (tStart < 0) return drops;
        int tEnd = html.indexOf("</table>", markerIdx);
        if (tEnd < 0) return drops;
        String table = html.substring(tStart, tEnd + 8);

        int tbodyStart = table.indexOf("<tbody>");
        if (tbodyStart >= 0) {
            int tbodyEnd = table.indexOf("</tbody>", tbodyStart);
            table = tbodyEnd >= 0
                ? table.substring(tbodyStart, tbodyEnd + 8)
                : table.substring(tbodyStart);
        }

        int pos = 0;
        while (true) {
            int rowStart = table.indexOf("<tr", pos);
            if (rowStart < 0) break;
            int rowEnd = table.indexOf("</tr>", rowStart);
            if (rowEnd < 0) break;
            String row = table.substring(rowStart, rowEnd + 5);
            pos = rowEnd + 5;

            if (!row.contains("<td")) continue;

            List<String> cells = extractCells(row);
            if (cells.size() < 3) continue;

            String source   = stripHtml(cells.get(0));
            String level    = stripHtml(cells.size() > 1 ? cells.get(1) : "");
            String quantity = stripHtml(cells.size() > 2 ? cells.get(2) : "");
            String rate     = stripHtml(cells.size() > 3 ? cells.get(3) : "");

            if (source.isEmpty()) continue;

            DropEntry drop = new DropEntry();
            drop.setSource(source);
            drop.setLevel(level);
            drop.setQuantity(quantity.isEmpty() ? "?" : quantity);
            drop.setRate(rate);
            drop.setRarity("");
            drops.add(drop);
        }
        return drops;
    }

    // ── HTML utilities ─────────────────────────────────────────────────────────

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
            Request request = new Request.Builder()
                .url(urlStr)
                .header("User-Agent", USER_AGENT)
                .build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("[JEI] HTTP {} for {}", response.code(), urlStr);
                    return null;
                }
                return new JsonParser().parse(response.body().string()).getAsJsonObject();
            }
        } catch (Exception e) {
            log.warn("[JEI] Request failed: {}", e.getMessage());
            return null;
        }
    }
}
