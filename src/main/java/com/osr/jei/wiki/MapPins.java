package com.osr.jei.wiki;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * World coordinates already present on a wiki page. Shop and monster pages
 * carry them in {@code {{Map}}} (and sometimes an infobox {@code mapX}/{@code mapY}).
 */
public final class MapPins {

    public static final int MAX = 8;

    public static final class Pin {
        public final int x;
        public final int y;
        public final int plane;

        public Pin(int x, int y, int plane) {
            this.x = x;
            this.y = y;
            this.plane = plane;
        }
    }

    private MapPins() {
    }

    public static List<Pin> parse(String wikitext) {
        Set<String> seen = new LinkedHashSet<>();
        List<Pin> pins = new ArrayList<>();
        if (wikitext == null || wikitext.isEmpty()) {
            return pins;
        }
        for (int index : WikiText.findTemplates(wikitext, "map")) {
            if (pins.size() >= MAX) {
                break;
            }
            String block = WikiText.extractTemplateBlock(wikitext, index);
            add(pins, seen, fromTemplate(block));
        }
        if (pins.isEmpty()) {
            add(pins, seen, fromInfobox(wikitext));
        }
        return pins;
    }

    private static Pin fromTemplate(String block) {
        if (block == null) {
            return null;
        }
        Map<String, String> params = WikiText.parseParams(block);
        String xText = WikiText.coalesce(params, "x", "mapx", "1");
        String yText = WikiText.coalesce(params, "y", "mapy", "2");
        String planeText = WikiText.coalesce(params, "plane", "z", "3");
        Pin named = pin(xText, yText, planeText);
        if (named != null) {
            return named;
        }
        List<String> parts = WikiText.splitTopLevel(stripBraces(block));
        if (parts.size() >= 2) {
            return fromCoordinateText(parts.get(1), planeText);
        }
        return null;
    }

    private static Pin fromInfobox(String wikitext) {
        List<Integer> shops = WikiText.findTemplates(wikitext, "infobox shop");
        List<Integer> monsters = WikiText.findTemplates(wikitext, "infobox monster");
        List<Integer> hits = shops.isEmpty() ? monsters : shops;
        if (hits.isEmpty()) {
            return null;
        }
        String block = WikiText.extractTemplateBlock(wikitext, hits.get(0));
        if (block == null) {
            return null;
        }
        Map<String, String> params = WikiText.parseParams(block);
        return pin(
            WikiText.coalesce(params, "mapx", "x"),
            WikiText.coalesce(params, "mapy", "y"),
            WikiText.coalesce(params, "plane", "mapplane", "z")
        );
    }

    private static Pin fromCoordinateText(String text, String planeText) {
        if (text == null) {
            return null;
        }
        String[] bits = text.split(",");
        if (bits.length < 2) {
            return null;
        }
        String plane = bits.length >= 3 ? bits[2] : planeText;
        return pin(bits[0], bits[1], plane);
    }

    private static Pin pin(String xText, String yText, String planeText) {
        Integer x = coord(xText);
        Integer y = coord(yText);
        if (x == null || y == null || !onMap(x.intValue(), y.intValue())) {
            return null;
        }
        int plane = 0;
        Integer parsedPlane = coord(planeText);
        if (parsedPlane != null && parsedPlane.intValue() >= 0 && parsedPlane.intValue() <= 3) {
            plane = parsedPlane.intValue();
        }
        return new Pin(x.intValue(), y.intValue(), plane);
    }

    private static boolean onMap(int x, int y) {
        return x >= 1024 && x <= 4500 && y >= 1024 && y <= 11000;
    }

    private static Integer coord(String text) {
        if (text == null) {
            return null;
        }
        String digits = text.replace(",", "").trim().toLowerCase(Locale.ROOT);
        int end = 0;
        while (end < digits.length() && Character.isDigit(digits.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return null;
        }
        try {
            return Integer.valueOf(digits.substring(0, end));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void add(List<Pin> pins, Set<String> seen, Pin pin) {
        if (pin == null || pins.size() >= MAX) {
            return;
        }
        String key = pin.x + ":" + pin.y + ":" + pin.plane;
        if (seen.add(key)) {
            pins.add(pin);
        }
    }

    private static String stripBraces(String block) {
        String inner = block;
        if (inner.startsWith("{{")) {
            inner = inner.substring(2);
        }
        if (inner.endsWith("}}")) {
            inner = inner.substring(0, inner.length() - 2);
        }
        return inner;
    }
}
