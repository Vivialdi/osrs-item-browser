package com.osr.jei.wiki;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared wikitext helpers. Recipe, equipment, and monster parsing all use this
 * so template walking stays consistent.
 */
public final class WikiText {

    private static final Pattern WIKILINK = Pattern.compile("\\[\\[(?:[^|\\]]*\\|)?([^\\]]+)\\]\\]");
    private static final Pattern TEMPLATE1 = Pattern.compile("\\{\\{[^{}|]*\\|([^{}|]*)\\}\\}");
    private static final Pattern TEMPLATE2 = Pattern.compile("\\{\\{[^{}]*\\}\\}");
    private static final Pattern EXTLINK = Pattern.compile("\\[https?://\\S+\\s([^\\]]+)\\]");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final Pattern SIGNED_INT = Pattern.compile("[+-]?\\d+");
    private static final Pattern DECIMAL = Pattern.compile("[0-9]+(?:\\.[0-9]+)?");

    private WikiText() {
    }

    public static String extractTemplateBlock(String text, int startIdx) {
        if (text == null || startIdx < 0 || startIdx >= text.length()) {
            return null;
        }
        int depth = 0;
        int i = startIdx;
        while (i < text.length() - 1) {
            char c = text.charAt(i);
            char next = text.charAt(i + 1);
            if (c == '{' && next == '{') {
                depth++;
                i += 2;
            } else if (c == '}' && next == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(startIdx, i + 2);
                }
                i += 2;
            } else {
                i++;
            }
        }
        return null;
    }

    /** Indexes of templates whose name starts with {@code nameLower}, for example {@code "recipe"}. */
    public static List<Integer> findTemplates(String wikitext, String nameLower) {
        List<Integer> hits = new ArrayList<>();
        if (wikitext == null || wikitext.isEmpty() || nameLower == null) {
            return hits;
        }
        String lower = wikitext.toLowerCase(Locale.ROOT);
        String needle = "{{" + nameLower.toLowerCase(Locale.ROOT);
        int from = 0;
        while (from < lower.length()) {
            int idx = lower.indexOf(needle, from);
            if (idx < 0) {
                break;
            }
            int after = idx + needle.length();
            if (after >= lower.length()) {
                break;
            }
            char boundary = lower.charAt(after);
            // Reject prefixes such as {{recipename. A real call is followed by |, space, newline, or }.
            if (boundary == '|' || boundary == ' ' || boundary == '\n' || boundary == '\t' || boundary == '}') {
                hits.add(idx);
                from = after;
            } else {
                from = idx + 2;
            }
        }
        return hits;
    }

    public static Map<String, String> parseParams(String block) {
        Map<String, String> params = new LinkedHashMap<>();
        if (block == null || block.isEmpty()) {
            return params;
        }
        String inner = block;
        if (inner.startsWith("{{")) {
            inner = inner.substring(2);
        }
        if (inner.endsWith("}}")) {
            inner = inner.substring(0, inner.length() - 2);
        }
        List<String> parts = splitTopLevel(inner);
        for (int i = 1; i < parts.size(); i++) {
            String part = parts.get(i).replace("\n", " ").trim();
            if (part.isEmpty()) {
                continue;
            }
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = part.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String val = part.substring(eq + 1).trim();
            if (!key.isEmpty()) {
                params.put(key, val);
            }
        }
        return params;
    }

    public static List<String> splitTopLevel(String text) {
        List<String> parts = new ArrayList<>();
        if (text == null) {
            return parts;
        }
        int depth = 0;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (i + 1 < text.length()) {
                char nx = text.charAt(i + 1);
                if ((c == '{' && nx == '{') || (c == '[' && nx == '[')) {
                    depth++;
                    i++;
                    continue;
                }
                if ((c == '}' && nx == '}') || (c == ']' && nx == ']')) {
                    depth = Math.max(0, depth - 1);
                    i++;
                    continue;
                }
            }
            if (c == '|' && depth == 0) {
                parts.add(text.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(text.substring(start));
        return parts;
    }

    public static String coalesce(Map<String, String> params, String... keys) {
        if (params == null) {
            return "";
        }
        for (String key : keys) {
            String v = params.get(key);
            if (v != null && !v.trim().isEmpty()) {
                return v;
            }
        }
        return "";
    }

    public static String clean(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        text = WIKILINK.matcher(text).replaceAll("$1");
        text = TEMPLATE1.matcher(text).replaceAll("$1");
        text = TEMPLATE2.matcher(text).replaceAll("");
        text = EXTLINK.matcher(text).replaceAll("$1");
        text = text.replaceAll("'{2,}", "");
        text = text.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&#160;", " ");
        text = HTML_TAG.matcher(text).replaceAll("");
        return text.replaceAll("\\s+", " ").trim();
    }

    public static int firstInt(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        Matcher m = DIGITS.matcher(text.replace(",", ""));
        return m.find() ? Integer.parseInt(m.group()) : 0;
    }

    public static int signedInt(String raw) {
        if (raw == null || raw.isEmpty()) {
            return 0;
        }
        String cleaned = clean(raw).replace(",", "");
        Matcher m = SIGNED_INT.matcher(cleaned);
        return m.find() ? Integer.parseInt(m.group()) : 0;
    }

    public static double firstDouble(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        Matcher m = DECIMAL.matcher(text.replace(",", ""));
        return m.find() ? Double.parseDouble(m.group()) : 0;
    }
}
