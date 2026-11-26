package com.osr.jei.wiki;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Display names for wiki location cells. {@code {{FloorNumber|uk=1}}} survives
 * ordinary template cleanup as the text {@code uk=1}, which is not a place name.
 */
public final class LocationNames {

    private static final Pattern UK_PARAM = Pattern.compile("(?i)\\buk\\s*=\\s*[^\\s),]+");
    private static final Pattern EMPTY_PARENS = Pattern.compile("\\(\\s*\\)");
    private static final Pattern SPACE_AFTER_OPEN = Pattern.compile("\\(\\s+");
    private static final Pattern SPACE_BEFORE_CLOSE = Pattern.compile("\\s+\\)");

    private LocationNames() {
    }

    public static String clean(String raw) {
        String text = WikiText.clean(raw);
        if (text.isEmpty()) {
            return "";
        }
        text = text.replace('\u2013', '-').replace('\u2014', '-').replace('\u2212', '-');
        text = UK_PARAM.matcher(text).replaceAll("");
        String previous;
        do {
            previous = text;
            text = EMPTY_PARENS.matcher(text).replaceAll("");
        } while (!text.equals(previous));
        text = SPACE_AFTER_OPEN.matcher(text).replaceAll("(");
        text = SPACE_BEFORE_CLOSE.matcher(text).replaceAll(")");
        return text.replaceAll("\\s+", " ").trim();
    }

    public static List<String> distinct(List<String> places) {
        Set<String> seen = new LinkedHashSet<>();
        if (places != null) {
            for (String place : places) {
                String cleaned = clean(place);
                if (!cleaned.isEmpty()
                    && !cleaned.equalsIgnoreCase("none")
                    && !cleaned.equalsIgnoreCase("n/a")) {
                    seen.add(cleaned);
                }
            }
        }
        return new ArrayList<>(seen);
    }
}
