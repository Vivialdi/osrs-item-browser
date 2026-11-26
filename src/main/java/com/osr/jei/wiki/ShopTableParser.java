package com.osr.jei.wiki;

import com.osr.jei.model.ShopEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Parses the rendered "Shop locations" wikitable.
 *
 * <p>Header cells on the wiki are split with {@code <br>}, so "Number in stock"
 * and "Restock time" must be compared after breaks become spaces. "Restock"
 * contains the letters "stock" and must not be selected as the stock column.
 */
public final class ShopTableParser {

    private ShopTableParser() {
    }

    public static List<ShopEntry> parse(String html) {
        if (html == null || html.isEmpty()) {
            return Collections.emptyList();
        }
        int marker = indexOf(html, "store-locations-list", 0);
        if (marker < 0) {
            marker = indexOf(html, "wikitable", 0);
        }
        if (marker < 0) {
            return Collections.emptyList();
        }
        int tableStart = lastIndexOf(html, "<table", marker);
        if (tableStart < 0) {
            return Collections.emptyList();
        }
        int tableEnd = indexOf(html, "</table>", marker);
        if (tableEnd < 0) {
            return Collections.emptyList();
        }
        String table = html.substring(tableStart, tableEnd + 8);

        int sellerCol = 0;
        int priceCol = -1;
        int stockCol = -1;
        int pos = 0;
        boolean headerFound = false;
        while (!headerFound) {
            int rowStart = indexOf(table, "<tr", pos);
            if (rowStart < 0) {
                break;
            }
            int rowEnd = indexOf(table, "</tr>", rowStart);
            if (rowEnd < 0) {
                break;
            }
            String row = table.substring(rowStart, rowEnd + 5);
            pos = rowEnd + 5;
            if (!containsIgnoreCase(row, "<th")) {
                continue;
            }
            headerFound = true;
            List<String> ths = Html.elements(row, "th");
            for (int i = 0; i < ths.size(); i++) {
                String h = Html.headerLabel(ths.get(i));
                if (h.contains("seller") || h.contains("store") || h.contains("shop")) {
                    sellerCol = i;
                }
                if (h.contains("sold") && !h.contains("bought")) {
                    priceCol = i;
                }
                if (isStockHeader(h)) {
                    stockCol = i;
                }
            }
        }

        List<ShopEntry> shops = new ArrayList<>();
        while (true) {
            int rowStart = indexOf(table, "<tr", pos);
            if (rowStart < 0) {
                break;
            }
            int rowEnd = indexOf(table, "</tr>", rowStart);
            if (rowEnd < 0) {
                break;
            }
            String row = table.substring(rowStart, rowEnd + 5);
            pos = rowEnd + 5;
            if (!containsIgnoreCase(row, "<td")) {
                continue;
            }
            List<String> cells = Html.elements(row, "td");
            if (cells.isEmpty()) {
                continue;
            }
            String shopName = "";
            if (sellerCol < cells.size()) {
                shopName = sellerName(cells.get(sellerCol));
            }
            if (shopName.isEmpty() || shopName.equalsIgnoreCase("n/a")) {
                continue;
            }

            int price = 0;
            String currency = "";
            boolean sells = false;
            if (priceCol >= 0 && priceCol < cells.size()) {
                String priceCell = cells.get(priceCol);
                String visible = Html.strip(priceCell).toLowerCase(Locale.ROOT);
                if (!visible.isEmpty()
                    && !visible.equals("n/a")
                    && !visible.equals("na")
                    && !visible.contains("not sold")) {
                    price = Html.sortNumber(priceCell, 0);
                    currency = currencyOf(priceCell);
                    sells = currency.length() > 0 || price > 0;
                    if (sells && currency.isEmpty()) {
                        currency = "gp";
                    }
                }
            }

            int stock = ShopEntry.UNKNOWN;
            if (stockCol >= 0 && stockCol < cells.size()) {
                stock = Html.sortNumber(cells.get(stockCol), ShopEntry.UNKNOWN);
            }

            shops.add(new ShopEntry(cleanName(shopName), price, stock, currency, sells));
        }
        return shops;
    }

    /**
     * Seller cell: the shop link, plus a {@code shop-version} note such as
     * {@code (post-quest)} when the wiki puts that in the same cell.
     */
    static String sellerName(String cell) {
        String name = Html.linkText(cell);
        if (name.isEmpty()) {
            name = Html.strip(cell);
        }
        name = cleanName(name);
        String version = versionNote(cell);
        if (!version.isEmpty() && !name.contains(version)) {
            name = (name + " " + version).trim();
        }
        return name;
    }

    static String versionNote(String cell) {
        if (cell == null) {
            return "";
        }
        String lower = cell.toLowerCase(Locale.ROOT);
        int marker = lower.indexOf("shop-version");
        if (marker < 0) {
            return "";
        }
        int gt = cell.indexOf('>', marker);
        int end = lower.indexOf("</span>", gt);
        if (gt < 0 || end < 0) {
            return "";
        }
        return Html.strip(cell.substring(gt + 1, end)).trim();
    }

    /** Wiki cells sometimes end the seller name with a stray period. */
    static String cleanName(String name) {
        if (name == null) {
            return "";
        }
        String trimmed = name.trim();
        while (trimmed.endsWith(".") || trimmed.endsWith("\u00b7")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    static boolean isStockHeader(String headerLower) {
        if (headerLower == null || headerLower.isEmpty()) {
            return false;
        }
        if (headerLower.contains("restock")) {
            return false;
        }
        return headerLower.contains("in stock") || headerLower.contains("stock");
    }

    static String currencyOf(String cellHtml) {
        String text = Html.strip(cellHtml).toLowerCase(Locale.ROOT);
        if (text.contains("tokkul")) {
            return "Tokkul";
        }
        if (text.contains("trading stick")) {
            return "Trading sticks";
        }
        if (text.contains("archery ticket")) {
            return "Archery tickets";
        }
        if (text.contains("ecto-token") || text.contains("ectotoken")) {
            return "Ecto-tokens";
        }
        if (text.matches(".*\\d.*") || text.contains("coin")) {
            return "gp";
        }
        return "";
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        return haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private static int indexOf(String haystack, String needle, int from) {
        return haystack.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT), from);
    }

    private static int lastIndexOf(String haystack, String needle, int before) {
        String h = haystack.toLowerCase(Locale.ROOT);
        return h.lastIndexOf(needle.toLowerCase(Locale.ROOT), before);
    }
}
