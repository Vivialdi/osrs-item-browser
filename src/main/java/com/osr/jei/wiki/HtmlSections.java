package com.osr.jei.wiki;

import java.util.Locale;

/**
 * Cuts one section out of a full MediaWiki parse ({@code prop=text}).
 * Headings look like {@code <h2 id="Shop_locations">} inside {@code mw-heading} divs.
 * The table of contents links to {@code #Shop_locations} and must not be treated as the section.
 */
public final class HtmlSections {

    private HtmlSections() {
    }

    public static String sliceById(String html, String headlineId) {
        if (html == null || html.isEmpty() || headlineId == null || headlineId.isEmpty()) {
            return "";
        }
        String needle = "id=\"" + headlineId + "\"";
        int idAt = html.indexOf(needle);
        if (idAt < 0) {
            return "";
        }
        int headingAt = -1;
        int level = 0;
        for (int lv = 2; lv <= 6; lv++) {
            int at = html.lastIndexOf("<h" + lv, idAt);
            if (at > headingAt) {
                headingAt = at;
                level = lv;
            }
        }
        if (headingAt < 0 || level == 0) {
            return "";
        }
        int start = includeHeadingDiv(html, headingAt);
        int end = html.length();
        int searchFrom = idAt + needle.length();
        String lower = html.toLowerCase(Locale.ROOT);
        for (int lv = 2; lv <= level; lv++) {
            int found = lower.indexOf("<h" + lv, searchFrom);
            if (found >= 0) {
                found = includeHeadingDiv(html, found);
                if (found < end) {
                    end = found;
                }
            }
        }
        if (end <= start) {
            return "";
        }
        return html.substring(start, end);
    }

    private static int includeHeadingDiv(String html, int headingAt) {
        int div = html.lastIndexOf("<div", headingAt);
        if (div >= 0 && headingAt - div < 90) {
            String between = html.substring(div, headingAt);
            if (between.contains("mw-heading")) {
                return div;
            }
        }
        return headingAt;
    }
}
