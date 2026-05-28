package com.osr.jei.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.osr.jei.model.ShopEntry;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URLEncoder;
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
 */
@Slf4j
@Singleton
public class ShopService {

    private static final String WIKI_API   = "https://oldschool.runescape.wiki/api.php";
    private static final String USER_AGENT = "osrs-jei-plugin";

    @Inject private OkHttpClient okHttpClient;

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
        JsonObject root = fetchJson(WIKI_API
            + "?action=parse&prop=sections&format=json&redirects=true&page=" + encodedName);
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
        JsonObject root = fetchJson(WIKI_API
            + "?action=parse&prop=text&format=json&section=" + sectionIdx
            + "&page=" + encodedName);
        if (root == null || !root.has("parse")) return "";
        return root.getAsJsonObject("parse").getAsJsonObject("text").get("*").getAsString();
    }

    // ── HTML table parser ──────────────────────────────────────────────────────

    private List<ShopEntry> parseShopTable(String html) {
        List<ShopEntry> shops = new ArrayList<>();

        int markerIdx = html.indexOf("store-locations-list");
        if (markerIdx < 0) markerIdx = html.indexOf("wikitable");
        if (markerIdx < 0) return shops;

        int tableStart = html.lastIndexOf("<table", markerIdx);
        if (tableStart < 0) return shops;
        int tableEnd = html.indexOf("</table>", markerIdx);
        if (tableEnd < 0) return shops;
        String table = html.substring(tableStart, tableEnd + 8);

        int sellerCol = 0;
        int priceCol  = -1;
        int stockCol  = -1;

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
                if (h.contains("sold"))  priceCol = i;
                if (h.contains("stock")) stockCol = i;
            }
        }

        while (true) {
            int rowStart = table.indexOf("<tr", pos);
            if (rowStart < 0) break;
            int rowEnd = table.indexOf("</tr>", rowStart);
            if (rowEnd < 0) break;
            String row = table.substring(rowStart, rowEnd + 5);
            pos = rowEnd + 5;

            if (!row.contains("<td")) continue;

            List<String> cells = extractCells(row, "td");
            if (cells.isEmpty()) continue;

            String shopName = "";
            if (sellerCol < cells.size()) {
                shopName = extractLinkText(cells.get(sellerCol));
                if (shopName.isEmpty()) shopName = stripHtml(cells.get(sellerCol));
            }
            if (shopName.isEmpty() || shopName.equalsIgnoreCase("n/a")) continue;

            int price = 0;
            if (priceCol >= 0 && priceCol < cells.size()) {
                price = sortableInt(cells.get(priceCol));
            }

            int stock = 0;
            if (stockCol >= 0 && stockCol < cells.size()) {
                stock = sortableInt(cells.get(stockCol));
            }

            shops.add(new ShopEntry(shopName, price, stock));
        }

        return shops;
    }

    // ── HTML utilities ─────────────────────────────────────────────────────────

    private List<String> extractCells(String row, String tag) {
        List<String> cells = new ArrayList<>();
        String openTag  = "<" + tag;
        String closeTag = "</" + tag + ">";
        int pos = 0;
        while (true) {
            int start = row.indexOf(openTag, pos);
            if (start < 0) break;
            int end = row.indexOf(closeTag, start);
            if (end < 0) break;
            cells.add(row.substring(start, end + closeTag.length()));
            pos = end + closeTag.length();
        }
        return cells;
    }

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

    private static final Pattern DATA_SORT = Pattern.compile("data-sort-value=\"([0-9,]+)\"");
    private static final Pattern ALL_INTS  = Pattern.compile("\\d+");

    private int sortableInt(String cellHtml) {
        Matcher m = DATA_SORT.matcher(cellHtml);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1).replace(",", "")); }
            catch (NumberFormatException ignored) { }
        }
        String text = stripHtml(cellHtml).replace(",", "");
        Matcher dm = ALL_INTS.matcher(text);
        int last = 0;
        while (dm.find()) {
            try { last = Integer.parseInt(dm.group()); }
            catch (NumberFormatException ignored) { }
        }
        return last;
    }

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
    }
}
