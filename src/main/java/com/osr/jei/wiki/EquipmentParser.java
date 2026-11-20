package com.osr.jei.wiki;

import com.osr.jei.model.EquipmentStats;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Parses {@code {{Infobox Bonuses}}} and item weight from cached wikitext. */
public final class EquipmentParser {

    private EquipmentParser() {
    }

    public static Optional<EquipmentStats> parseStats(String wikitext) {
        if (wikitext == null || wikitext.isEmpty()) {
            return Optional.empty();
        }
        int start = first(wikitext, "infobox bonuses");
        if (start < 0) {
            return Optional.empty();
        }
        String block = WikiText.extractTemplateBlock(wikitext, start);
        if (block == null) {
            return Optional.empty();
        }
        Map<String, String> p = WikiText.parseParams(block);
        String slot = WikiText.clean(p.getOrDefault("slot", ""));
        if (slot.isEmpty()) {
            return Optional.empty();
        }
        int speed = WikiText.firstInt(p.getOrDefault("aspeed", p.getOrDefault("speed", "0")));
        return Optional.of(new EquipmentStats(
            WikiText.signedInt(p.getOrDefault("astab", "0")),
            WikiText.signedInt(p.getOrDefault("aslash", "0")),
            WikiText.signedInt(p.getOrDefault("acrush", "0")),
            WikiText.signedInt(p.getOrDefault("amagic", "0")),
            WikiText.signedInt(p.getOrDefault("arange", "0")),
            WikiText.signedInt(p.getOrDefault("dstab", "0")),
            WikiText.signedInt(p.getOrDefault("dslash", "0")),
            WikiText.signedInt(p.getOrDefault("dcrush", "0")),
            WikiText.signedInt(p.getOrDefault("dmagic", "0")),
            WikiText.signedInt(p.getOrDefault("drange", "0")),
            WikiText.signedInt(p.getOrDefault("str", "0")),
            WikiText.signedInt(p.getOrDefault("rstr", "0")),
            WikiText.signedInt(stripPercent(p.getOrDefault("mdmg", "0"))),
            WikiText.signedInt(p.getOrDefault("prayer", "0")),
            slot,
            speed
        ));
    }

    /** Item weight in kilograms, or {@link Double#NaN} when the page has no weight. */
    public static double parseWeight(String wikitext) {
        if (wikitext == null || wikitext.isEmpty()) {
            return Double.NaN;
        }
        int start = first(wikitext, "infobox item");
        if (start < 0) {
            return Double.NaN;
        }
        String block = WikiText.extractTemplateBlock(wikitext, start);
        if (block == null) {
            return Double.NaN;
        }
        Map<String, String> p = WikiText.parseParams(block);
        String raw = WikiText.clean(p.getOrDefault("weight", ""));
        if (raw.isEmpty()) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(raw.replace(",", ""));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static String stripPercent(String s) {
        return s == null ? "0" : s.replace("%", "").trim();
    }

    private static int first(String wikitext, String templateName) {
        List<Integer> hits = WikiText.findTemplates(wikitext, templateName);
        return hits.isEmpty() ? -1 : hits.get(0);
    }
}
