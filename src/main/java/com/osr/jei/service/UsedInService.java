package com.osr.jei.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds every item that can be crafted using a given ingredient by parsing the
 * "Products" section that the OSRS Wiki maintains on each ingredient's own page.
 */
@Slf4j
@Singleton
public class UsedInService {

    private static final String WIKI_API   = "https://oldschool.runescape.wiki/api.php";
    private static final String USER_AGENT = "osrs-jei-plugin";

    @Inject private OkHttpClient okHttpClient;

    private final ConcurrentHashMap<String, List<String>> cache = new ConcurrentHashMap<>();

    // ── Public API ─────────────────────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    public List<String> getCandidateProducts(String ingredientName) {
        String key = ingredientName.toLowerCase(Locale.ROOT);
        List<String> cached = cache.get(key);
        if (cached != null) return cached;

        try {
            List<String> result = fetchProducts(ingredientName);
            cache.put(key, result);
            return result;
        } catch (Exception e) {
            log.warn("[JEI] UsedIn fetch failed for '{}': {}", ingredientName, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ── Fetch ──────────────────────────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    private List<String> fetchProducts(String ingredientName) throws Exception {
        String encoded = URLEncoder.encode(ingredientName.replace(" ", "_"), "UTF-8");

        int sectionIndex = findProductsSection(encoded);
        if (sectionIndex < 0) {
            log.info("[JEI] No Products section for '{}'", ingredientName);
            return Collections.emptyList();
        }

        String html = fetchSectionHtml(encoded, sectionIndex);
        if (html.isEmpty()) return Collections.emptyList();

        List<String> products = parseProductsTable(html);
        log.debug("[JEI] Products for '{}': {} item(s)", ingredientName, products.size());
        return products;
    }

    // ── Section finder ─────────────────────────────────────────────────────────

    private int findProductsSection(String encodedName) throws Exception {
        JsonObject root = fetchJson(WIKI_API
            + "?action=parse&prop=sections&format=json&redirects=true&page=" + encodedName);
        if (root == null || !root.has("parse")) return -1;

        JsonArray sections = root.getAsJsonObject("parse").getAsJsonArray("sections");
        if (sections == null) return -1;

        for (JsonElement el : sections) {
            JsonObject sec = el.getAsJsonObject();
            String title = stripHtml(sec.get("line").getAsString())
                .toLowerCase(Locale.ROOT).trim();
            if (title.equals("products")
                    || title.equals("uses")
                    || title.equals("items created")
                    || title.equals("created from")) {
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

    private static final Pattern ITEM_HREF =
        Pattern.compile("href=\"/w/([^\"#:]+)\"");

    private List<String> parseProductsTable(String html) {
        Set<String> seen = new LinkedHashSet<>();

        int markerIdx = html.indexOf("wikitable");
        if (markerIdx < 0) return new ArrayList<>();
        int tableStart = html.lastIndexOf("<table", markerIdx);
        if (tableStart < 0) return new ArrayList<>();
        int tableEnd = html.indexOf("</table>", markerIdx);
        if (tableEnd < 0) return new ArrayList<>();
        String table = html.substring(tableStart, tableEnd + 8);

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

            Matcher m = ITEM_HREF.matcher(row);
            if (m.find()) {
                String pageName = m.group(1).replace("_", " ");
                if (!pageName.isEmpty()) seen.add(pageName);
            }
        }

        return new ArrayList<>(seen);
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

    // ── Utilities ──────────────────────────────────────────────────────────────

    private String stripHtml(String html) {
        if (html == null || html.isEmpty()) return "";
        return html.replaceAll("<[^>]+>", "").trim();
    }
}
