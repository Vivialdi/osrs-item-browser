package com.osr.jei.wiki;

import com.osr.jei.model.DropEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Parses the {@code item-drops} table from an "Item sources" section. */
public final class DropTableParser {

    private DropTableParser() {
    }

    public static List<DropEntry> parse(String html) {
        if (html == null || html.isEmpty()) {
            return Collections.emptyList();
        }
        int marker = html.toLowerCase(Locale.ROOT).indexOf("item-drops");
        if (marker < 0) {
            return Collections.emptyList();
        }
        String lower = html.toLowerCase(Locale.ROOT);
        int tStart = lower.lastIndexOf("<table", marker);
        if (tStart < 0) {
            return Collections.emptyList();
        }
        int tEnd = lower.indexOf("</table>", marker);
        if (tEnd < 0) {
            return Collections.emptyList();
        }
        String table = html.substring(tStart, tEnd + 8);
        int tbodyStart = lowerTable(table).indexOf("<tbody>");
        if (tbodyStart >= 0) {
            int tbodyEnd = lowerTable(table).indexOf("</tbody>", tbodyStart);
            table = tbodyEnd >= 0
                ? table.substring(tbodyStart, tbodyEnd + 8)
                : table.substring(tbodyStart);
        }

        List<DropEntry> drops = new ArrayList<>();
        int pos = 0;
        String tableLower = lowerTable(table);
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
            List<String> cells = Html.cellInner(row);
            if (cells.size() < 3) {
                continue;
            }
            String source = Html.strip(cells.get(0));
            String level = cells.size() > 1 ? Html.strip(cells.get(1)) : "";
            String quantity = cells.size() > 2 ? Html.strip(cells.get(2)) : "";
            String rate = cells.size() > 3 ? Html.strip(cells.get(3)) : "";
            if (source.isEmpty()) {
                continue;
            }
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

    private static String lowerTable(String table) {
        return table.toLowerCase(Locale.ROOT);
    }
}
