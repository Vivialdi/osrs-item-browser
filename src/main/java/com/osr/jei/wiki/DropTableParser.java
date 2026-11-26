package com.osr.jei.wiki;

import com.osr.jei.model.DropEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the {@code item-drops} table from an "Item sources" section. */
public final class DropTableParser {

    /** {@code drops-sailing}, {@code drops-combat}, {@code drops-reward}, and the other level icons. */
    private static final Pattern LEVEL_KIND = Pattern.compile("class=\"drops-([a-z]+)\"");

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
            String levelCell = cells.size() > 1 ? cells.get(1) : "";
            String level = plain(levelCell);
            String quantity = cells.size() > 2 ? plain(cells.get(2)) : "";
            String rate = cells.size() > 3 ? plain(cells.get(3)) : "";
            if (source.isEmpty()) {
                continue;
            }
            DropEntry drop = new DropEntry();
            drop.setSource(source);
            drop.setLevel(level);
            drop.setLevelKind(levelKind(levelCell));
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

    static String levelKind(String cellHtml) {
        if (cellHtml == null || cellHtml.isEmpty()) {
            return "";
        }
        Matcher matcher = LEVEL_KIND.matcher(cellHtml.toLowerCase(Locale.ROOT));
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String plain(String html) {
        return Html.strip(html)
            .replace('\u2013', '-')
            .replace('\u2014', '-')
            .replace('\u2212', '-');
    }
}
