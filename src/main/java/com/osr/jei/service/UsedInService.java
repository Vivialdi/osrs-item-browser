package com.osr.jei.service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.CompletableFuture;

/** Products parsed from the Products section of the shared item-page HTML. */
@Singleton
public class UsedInService {

    private static final Pattern ITEM_HREF = Pattern.compile("href=\"/w/([^\"#:]+)\"");

    private final ItemDetailsService items;

    @Inject
    public UsedInService(ItemDetailsService items) {
        this.items = items;
    }

    public CompletableFuture<List<String>> getCandidateProducts(String ingredientName) {
        return items.load(ingredientName).thenApply(details -> details.products);
    }

    public static List<String> parseProductsTable(String html) {
        Set<String> seen = new LinkedHashSet<>();
        if (html == null || html.isEmpty()) {
            return new ArrayList<>();
        }
        String lower = html.toLowerCase(Locale.ROOT);
        int markerIdx = lower.indexOf("wikitable");
        if (markerIdx < 0) {
            return new ArrayList<>();
        }
        int tableStart = lower.lastIndexOf("<table", markerIdx);
        if (tableStart < 0) {
            return new ArrayList<>();
        }
        int tableEnd = lower.indexOf("</table>", markerIdx);
        if (tableEnd < 0) {
            return new ArrayList<>();
        }
        String table = html.substring(tableStart, tableEnd + 8);
        String tableLower = table.toLowerCase(Locale.ROOT);
        int tbodyStart = tableLower.indexOf("<tbody>");
        if (tbodyStart >= 0) {
            int tbodyEnd = tableLower.indexOf("</tbody>", tbodyStart);
            table = tbodyEnd >= 0
                ? table.substring(tbodyStart, tbodyEnd + 8)
                : table.substring(tbodyStart);
            tableLower = table.toLowerCase(Locale.ROOT);
        }

        int pos = 0;
        while (true) {
            int rowStart = tableLower.indexOf("<tr", pos);
            if (rowStart < 0) {
                break;
            }
            int rowEnd = tableLower.indexOf("</tr>", rowStart);
            if (rowEnd < 0) {
                break;
            }
            String row = table.substring(rowStart, rowEnd + 5);
            pos = rowEnd + 5;
            if (!row.toLowerCase(Locale.ROOT).contains("<td")) {
                continue;
            }
            Matcher matcher = ITEM_HREF.matcher(row);
            if (matcher.find()) {
                String pageName = URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8).replace("_", " ");
                if (!pageName.isEmpty()) {
                    seen.add(pageName);
                }
            }
        }
        return new ArrayList<>(seen);
    }
}
