package com.osr.jei.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Singleton;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
 *
 * <p>The wiki already curates this table (rendered by {@code {{Uses material list}}}).
 * We fetch the rendered HTML of that one section — exact, no false positives,
 * no per-product verification calls needed.
 *
 * <p><b>Cost: 2 network calls per unique ingredient (section lookup + section HTML),
 * then cached forever.</b>
 *
 * <p>If the ingredient page has no "Products" section (e.g. quest-only items) the
 * result is an empty list.  Network errors are NOT cached so the next call retries.
 */
@Slf4j
@Singleton
public class UsedInService {

    private static final String WIKI_API   = "https://oldschool.runescape.wiki/api.php";
    private static final String USER_AGENT = "osrs-jei-plugin";

    /** Cache: absent = never fetched; present = fetched (possibly empty list). */
    private final ConcurrentHashMap<String, List<String>> cache = new ConcurrentHashMap<>();

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Returns a deduplicated list of wiki page titles (item names) that can be
     * crafted using {@code ingredientName}, or an empty list if no "Products"
     * section exists or the request fails.
     */
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
            return Collections.emptyList(); // NOT cached — will retry next time
        }
    }

    // ── Fetch ──────────────────────────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    private List<String> fetchProducts(String ingredientName) throws Exception {
        String encoded = URLEncoder.encode(ingredientName.replace(" ", "_"), "UTF-8");

        // ── 1. Find the "Products" section index on the ingredient's page ──────
        int sectionIndex = findProductsSection(encoded);
        if (sectionIndex < 0) {
            log.info("[JEI] No Products section for '{}'", ingredientName);
            return Collections.emptyList();
        }

        // ── 2. Fetch the rendered HTML of that section ─────────────────────────
        String html = fetchSectionHtml(encoded, sectionIndex);
        if (html.isEmpty()) return Collections.emptyList();

        // ── 3. Parse product names from the wikitable ──────────────────────────
        List<String> products = parseProductsTable(html);
        log.debug("[JEI] Products for '{}': {} item(s)", ingredientName, products.size());
        return products;
    }

    // ── Section finder ─────────────────────────────────────────────────────────

    private int findProductsSection(String encodedName) throws Exception {
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
            // "Products" is the standard OSRS wiki section name for {{Uses material list}}.
            // A few pages use "Uses" or "Items created" — include them as fallbacks.
            if (title.equals("products")
                    || title.equals("uses")
                    || title.equals("items created")
                    || title.equals("created from")) {
                return Integer.parseInt(sec.get("index").getAsString());
            }
        }
        return -1;
    }

    // ── HTML section fetch ─────────────────────────────────────────────────────

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
     * Extracts item page titles from a Products wikitable.
     *
     * <p>Every data row's first column holds the item icon and name, both
     * linking to {@code /w/PageName}.  We match the first such link per row —
     * the icon link and the text link both point to the same page, so the first
     * one is always correct.  Namespace links (File:, Template:, etc.) are
     * excluded by the pattern (they contain a colon after {@code /w/}).
     *
     * <p>Duplicate page names are collapsed with a {@link LinkedHashSet} so items
     * that appear in multiple recipe variants (e.g. two cannonball quantities)
     * show up only once.
     */
    // Matches href="/w/PageName" where PageName contains no colon (skips File:, etc.)
    private static final Pattern ITEM_HREF =
        Pattern.compile("href=\"/w/([^\"#:]+)\"");

    private List<String> parseProductsTable(String html) {
        Set<String> seen = new LinkedHashSet<>();

        // Locate the wikitable
        int markerIdx = html.indexOf("wikitable");
        if (markerIdx < 0) return new ArrayList<>();
        int tableStart = html.lastIndexOf("<table", markerIdx);
        if (tableStart < 0) return new ArrayList<>();
        int tableEnd = html.indexOf("</table>", markerIdx);
        if (tableEnd < 0) return new ArrayList<>();
        String table = html.substring(tableStart, tableEnd + 8);

        // Use <tbody> to skip <thead> header rows if present
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

            if (!row.contains("<td")) continue; // skip header rows with only <th>

            // The first /w/PageName link in the row is the item icon or name —
            // both point to the same page, so we just take whichever comes first.
            Matcher m = ITEM_HREF.matcher(row);
            if (m.find()) {
                String pageName = m.group(1).replace("_", " ");
                if (!pageName.isEmpty()) seen.add(pageName);
            }
        }

        return new ArrayList<>(seen);
    }

    // ── Utilities ──────────────────────────────────────────────────────────────

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

    private String stripHtml(String html) {
        if (html == null || html.isEmpty()) return "";
        return html.replaceAll("<[^>]+>", "").trim();
    }
}
