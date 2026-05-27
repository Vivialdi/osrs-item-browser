package com.osr.jei.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.osr.jei.model.ShopEntry;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Singleton;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds every shop that sells a given item by parsing the "Shop locations"
 * section on the item's OSRS Wiki page.
 *
 * <p>Shop data is rendered by the Lua-backed {@code {{Store locations list}}}
 * template — individual {@code {{StoreLine}}} entries never appear in the raw
 * wikitext, so we must parse the <em>rendered HTML</em>.
 *
 * <p>The rendered table has class {@code store-locations-list} and columns:
 * <pre>
 *   Seller | Location | Number in stock | Restock time |
 *   Price sold at | Price bought at | Change per | Members | League region
 * </pre>
 * "Price sold at" is what the <b>player pays</b> to buy from the shop.
 * Numeric cells carry a {@code data-sort-value} attribute which we use
 * instead of parsing the cell text (coin images in the text make text-parsing
 * unreliable).
 *
 * <p><b>Cost: 2 network calls per unique item (section lookup + section HTML),
 * then cached forever.</b>
 */
@Slf4j
@Singleton
public class ShopService {

    private static final String WIKI_API   = "https://oldschool.runescape.wiki/api.php";
    private static final String USER_AGENT = "osrs-jei-plugin";

    /** absent = never fetched; present (possibly empty list) = already parsed. */
    private final ConcurrentHashMap<String, List<ShopEntry>> cache =
        new ConcurrentHashMap<>();

    // ── Public API ─────────────────────────────────────────────────────────────

    public List<ShopEntry> getShops(String itemName) {
        String key = itemName.toLowerCase(Locale.ROOT);
        List<ShopEntry> cached = cache.get(key);
        if (cached != null) return cached;

        try {
            List<ShopEntry> result = fetchShops(itemName);
            cache.put(key, result);
            log.debug("[JEI] ShopService '{}': {} shop(s)", itemName, result.size());
            return result;
        } catch (Exception e) {
            log.warn("[JEI] ShopService failed for '{}': {}", itemName, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ── Fetch ──────────────────────────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    private List<ShopEntry> fetchShops(String itemName) throws Exception {
        String encoded = URLEncoder.encode(itemName.replace(" ", "_"), "UTF-8");

        int sectionIndex = findShopSection(encoded);
        if (sectionIndex < 0) {
            log.info("[JEI] No shop-locations section for '{}'", itemName);
            return Collections.emptyList();
        }

        String html = fetchSectionHtml(encoded, sectionIndex);
        if (html.isEmpty()) return Collections.emptyList();

        return parseShopTable(html);
    }

    // ── Section finder ─────────────────────────────────────────────────────────

    private int findShopSection(String encodedName) throws Exception {
        String urlStr = WIKI_API
            + "?action=parse&prop=sections&format=json&redirects=true&page=" + encodedName;

        JsonObject root = fetchJson(urlStr);
        if (root == null || !root.has("parse")) return -1;

        JsonArray sections = root.getAsJsonObject("parse").getAsJsonArray("sections");
        if (sections == null) return -1;

        for (JsonElement el : sections) {
            JsonObject sec = el.getAsJsonObject();
            String title = stripHtml(sec.get("line").getAsString())
                .toLowerCase(Locale.ROOT).trim();
            if (title.equals("shop locations")
                    || title.equals("sold by")
                    || title.equals("store locations")
                    || title.equals("availability")
                    || title.equals("shops")) {
                return Integer.parseInt(sec.get("index").getAsString());
            }
        }
        return -1;
    }

    private String fetchSectionHtml(String encodedName, int sectionIdx) throws Exception {
        String urlStr = WIKI_API
            + "?action=parse&prop=text&format=json&section=" + sectionIdx
            + "&page=" + encodedName;

        JsonObject root = fetchJson(urlStr);
        if (root == null || !root.has("parse")) return "";
        return root.getAsJsonObject("parse").getAsJsonObject("text").get("*").getAsString();
    }

    // ── HTML table parser ──────────────────────────────────────────────────────

    /**
     * Parses the rendered shop table into {@link ShopEntry} objects.
     *
     * <p>Column-index detection is header-driven so the parser stays correct
     * if the wiki ever reorders columns.  Numeric values are read from the
     * {@code data-sort-value} attribute on each {@code <td>} — this avoids
     * the coin-image alt-text being mixed into the price string.
     */
    private List<ShopEntry> parseShopTable(String html) {
        List<ShopEntry> shops = new ArrayList<>();

        // Prefer the specific "store-locations-list" table class; fall back to any wikitable
        int markerIdx = html.indexOf("store-locations-list");
        if (markerIdx < 0) markerIdx = html.indexOf("wikitable");
        if (markerIdx < 0) return shops;

        int tableStart = html.lastIndexOf("<table", markerIdx);
        if (tableStart < 0) return shops;
        int tableEnd = html.indexOf("</table>", markerIdx);
        if (tableEnd < 0) return shops;
        String table = html.substring(tableStart, tableEnd + 8);

        // ── Detect column indices from the header row ──────────────────────────
        int sellerCol = 0;   // "Seller"         — shop name
        int priceCol  = -1;  // "Price sold at"  — what the player pays
        int stockCol  = -1;  // "Number in stock"

        int pos = 0;
        boolean headerFound = false;
        while (!headerFound) {
            int rowStart = table.indexOf("<tr", pos);
            if (rowStart < 0) break;
            int rowEnd = table.indexOf("</tr>", rowStart);
            if (rowEnd < 0) break;
            String row = table.substring(rowStart, rowEnd + 5);
            pos = rowEnd + 5;

            if (!row.contains("<th")) continue;
            headerFound = true;

            List<String> ths = extractCells(row, "th");
            for (int i = 0; i < ths.size(); i++) {
                String h = stripHtml(ths.get(i)).toLowerCase(Locale.ROOT).trim();
                if (h.contains("seller") || h.contains("store") || h.contains("shop")) sellerCol = i;
                // "Price sold at" = shop sells to player = player pays
                if (h.contains("sold"))  priceCol = i;
                if (h.contains("stock")) stockCol = i;
            }
        }

        // ── Parse data rows ────────────────────────────────────────────────────
        while (true) {
            int rowStart = table.indexOf("<tr", pos);
            if (rowStart < 0) break;
            int rowEnd = table.indexOf("</tr>", rowStart);
            if (rowEnd < 0) break;
            String row = table.substring(rowStart, rowEnd + 5);
            pos = rowEnd + 5;

            if (!row.contains("<td")) continue;

            // Collect full cell HTML (including <td ...> tag) so we can read
            // data-sort-value attributes on numeric cells.
            List<String> cells = extractCells(row, "td");
            if (cells.isEmpty()) continue;

            // Shop name: first link text in the seller cell
            String shopName = "";
            if (sellerCol < cells.size()) {
                shopName = extractLinkText(cells.get(sellerCol));
                if (shopName.isEmpty()) shopName = stripHtml(cells.get(sellerCol));
            }
            if (shopName.isEmpty() || shopName.equalsIgnoreCase("n/a")) continue;

            // Price sold at (data-sort-value is reliable; text has coin images mixed in)
            int price = 0;
            if (priceCol >= 0 && priceCol < cells.size()) {
                price = sortableInt(cells.get(priceCol));
            }

            // Stock
            int stock = 0;
            if (stockCol >= 0 && stockCol < cells.size()) {
                stock = sortableInt(cells.get(stockCol));
            }

            shops.add(new ShopEntry(shopName, price, stock));
        }

        return shops;
    }

    // ── HTML utilities ─────────────────────────────────────────────────────────

    /**
     * Extracts the <b>full</b> cell HTML (including the opening {@code <td>} or
     * {@code <th>} tag) so callers can read {@code data-sort-value} attributes.
     */
    private List<String> extractCells(String row, String tag) {
        List<String> cells = new ArrayList<>();
        String openTag = "<" + tag;
        String closeTag = "</" + tag + ">";
        int pos = 0;
        while (true) {
            int start = row.indexOf(openTag, pos);
            if (start < 0) break;
            int end = row.indexOf(closeTag, start);
            if (end < 0) break;
            // Include the closing tag so callers get the complete cell
            cells.add(row.substring(start, end + closeTag.length()));
            pos = end + closeTag.length();
        }
        return cells;
    }

    /** Returns the visible text of the first anchor link inside a cell. */
    private String extractLinkText(String cell) {
        int aStart = cell.indexOf("<a ");
        if (aStart < 0) aStart = cell.indexOf("<a\n");
        if (aStart < 0) return "";
        int gt = cell.indexOf(">", aStart);
        if (gt < 0) return "";
        int end = cell.indexOf("</a>", gt);
        if (end < 0) return "";
        return stripHtml(cell.substring(gt + 1, end)).trim();
    }

    /**
     * Extracts a numeric value from a table cell.
     *
     * <p>Prefers {@code data-sort-value="N"} on the {@code <td>} element,
     * which the OSRS wiki sets for all sortable numeric columns.  Falls back
     * to the last integer in the stripped cell text (coin image alt-text
     * appears before the actual number, so "last" beats "first").
     */
    private static final Pattern DATA_SORT = Pattern.compile("data-sort-value=\"([0-9,]+)\"");
    private static final Pattern ALL_INTS  = Pattern.compile("\\d+");

    private int sortableInt(String cellHtml) {
        // Primary: data-sort-value attribute on the <td> tag
        Matcher m = DATA_SORT.matcher(cellHtml);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1).replace(",", "")); }
            catch (NumberFormatException ignored) { }
        }

        // Fallback: strip HTML, collect all numbers, take the last one.
        // Coin image alt-texts like "Coins 1000" appear before the actual
        // price, so the rightmost number is the real value.
        String text = stripHtml(cellHtml).replace(",", "");
        Matcher dm = ALL_INTS.matcher(text);
        int last = 0;
        while (dm.find()) {
            try { last = Integer.parseInt(dm.group()); }
            catch (NumberFormatException ignored) { }
        }
        return last;
    }

    // ── HTML stripping ─────────────────────────────────────────────────────────

    private String stripHtml(String html) {
        if (html == null || html.isEmpty()) return "";
        String text = html.replaceAll("<[^>]+>", "");
        text = text.replace("&amp;", "&").replace("&lt;", "<")
                   .replace("&gt;", ">").replace("&nbsp;", " ")
                   .replace("&#160;", " ").replace("&ndash;", "–");
        return text.trim().replaceAll("\\s+", " ");
    }

    // ── Network ────────────────────────────────────────────────────────────────

    private JsonObject fetchJson(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setConnectTimeout(6_000);
        conn.setReadTimeout(15_000);

        int code = conn.getResponseCode();
        if (code != 200) {
            log.warn("[JEI] HTTP {} for {}", code, urlStr);
            conn.disconnect();
            return null;
        }

        try (InputStreamReader reader = new InputStreamReader(
                conn.getInputStream(), StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader).getAsJsonObject();
        } finally {
            conn.disconnect();
        }
    }
}
