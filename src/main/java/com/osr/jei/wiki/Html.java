package com.osr.jei.wiki;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small HTML helpers shared by the wiki table parsers.
 * These operate on fragments from the MediaWiki parse API, not arbitrary HTML.
 */
public final class Html {

    private static final Pattern DATA_SORT = Pattern.compile("data-sort-value=\"([^\"]*)\"");

    private Html() {
    }

    /** Turns line-break tags into spaces before other tags are stripped. */
    public static String breaksToSpaces(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        return html.replaceAll("(?i)<br\\s*/?>", " ");
    }

    public static String strip(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        String text = breaksToSpaces(html).replaceAll("<[^>]+>", "");
        text = text.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&#160;", " ")
            .replace("&ndash;", "–")
            .replace("&mdash;", "—")
            .replace("&#91;", "[")
            .replace("&#93;", "]");
        return text.trim().replaceAll("\\s+", " ");
    }

    /**
     * Returns each {@code <tag>...</tag>} element, including its outer tags.
     * Nested elements of the same tag are not supported; wiki table cells are flat.
     */
    public static List<String> elements(String row, String tag) {
        List<String> cells = new ArrayList<>();
        String open = "<" + tag;
        String close = "</" + tag + ">";
        int pos = 0;
        while (true) {
            int start = indexOfIgnoreCase(row, open, pos);
            if (start < 0) {
                break;
            }
            int end = indexOfIgnoreCase(row, close, start);
            if (end < 0) {
                break;
            }
            cells.add(row.substring(start, end + close.length()));
            pos = end + close.length();
        }
        return cells;
    }

    /** Inner HTML of each cell, excluding the outer {@code <td>} wrapper. */
    public static List<String> cellInner(String row) {
        List<String> cells = new ArrayList<>();
        int pos = 0;
        while (true) {
            int start = indexOfIgnoreCase(row, "<td", pos);
            if (start < 0) {
                break;
            }
            int closeGt = row.indexOf('>', start);
            if (closeGt < 0) {
                break;
            }
            int end = indexOfIgnoreCase(row, "</td>", closeGt);
            if (end < 0) {
                break;
            }
            cells.add(row.substring(closeGt + 1, end));
            pos = end + 5;
        }
        return cells;
    }

    public static String linkText(String cell) {
        int aStart = indexOfIgnoreCase(cell, "<a ", 0);
        if (aStart < 0) {
            aStart = indexOfIgnoreCase(cell, "<a\n", 0);
        }
        if (aStart < 0) {
            return "";
        }
        int gt = cell.indexOf('>', aStart);
        if (gt < 0) {
            return "";
        }
        int end = indexOfIgnoreCase(cell, "</a>", gt);
        if (end < 0) {
            return "";
        }
        return strip(cell.substring(gt + 1, end)).trim();
    }

    public static String headerLabel(String thHtml) {
        return strip(thHtml).toLowerCase(Locale.ROOT).trim();
    }

    /**
     * Reads {@code data-sort-value} when present, otherwise the first integer in the visible text.
     * Scientific values such as {@code 10e50} and the infinity glyph map to {@link Integer#MAX_VALUE}.
     *
     * @param whenMissing returned when the cell has no number (N/A, blank)
     */
    public static int sortNumber(String cellHtml, int whenMissing) {
        if (cellHtml == null || cellHtml.isEmpty()) {
            return whenMissing;
        }
        if (isInfinite(cellHtml)) {
            return Integer.MAX_VALUE;
        }
        Matcher sort = DATA_SORT.matcher(cellHtml);
        if (sort.find()) {
            Integer parsed = parseSortToken(sort.group(1));
            if (parsed != null) {
                return parsed;
            }
        }
        String text = strip(cellHtml).replace(",", "");
        if (text.isEmpty() || text.equalsIgnoreCase("n/a") || text.equalsIgnoreCase("na")) {
            return whenMissing;
        }
        if (isInfinite(text)) {
            return Integer.MAX_VALUE;
        }
        Matcher digits = Pattern.compile("\\d+").matcher(text);
        if (!digits.find()) {
            return whenMissing;
        }
        try {
            return Integer.parseInt(digits.group());
        } catch (NumberFormatException e) {
            return whenMissing;
        }
    }

    private static boolean isInfinite(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT);
        return lower.contains("∞")
            || lower.contains("infinity")
            || lower.contains("10e50")
            || lower.contains("10e99");
    }

    private static Integer parseSortToken(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim().replace(",", "");
        if (v.isEmpty()) {
            return null;
        }
        if (isInfinite(v) || v.equalsIgnoreCase("inf")) {
            return Integer.MAX_VALUE;
        }
        try {
            if (v.toLowerCase(Locale.ROOT).contains("e")) {
                double d = Double.parseDouble(v);
                if (Double.isInfinite(d) || d > Integer.MAX_VALUE) {
                    return Integer.MAX_VALUE;
                }
                if (d < 0) {
                    return null;
                }
                return (int) d;
            }
            long n = Long.parseLong(v);
            if (n > Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
            if (n < 0) {
                return null;
            }
            return (int) n;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int indexOfIgnoreCase(String haystack, String needle, int from) {
        if (haystack == null || needle == null) {
            return -1;
        }
        return haystack.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT), from);
    }
}
