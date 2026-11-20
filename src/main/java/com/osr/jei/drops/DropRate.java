package com.osr.jei.drops;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes a drop-table rate to {@code 1/X}. The original cell text is kept
 * for the tooltip. A quantity such as {@code 1-3} is not itself a rate.
 */
public final class DropRate {

    private static final Pattern FRACTION = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*/\\s*(\\d+(?:\\.\\d+)?)");
    private static final Pattern PERCENT = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*%");
    private static final Pattern TIMES = Pattern.compile("(?i)(\\d+(?:\\.\\d+)?)\\s*x\\s*\\d");
    private static final Pattern QUANTITY_PREFIX = Pattern.compile("(?i)^\\s*\\d+\\s*-\\s*\\d+\\s*x\\s*");
    private static final Pattern QUANTITY_ONLY = Pattern.compile("\\d+\\s*-\\s*\\d+");

    public final String display;
    public final String original;
    /** Chance from 0 to 1, or null when the cell is not a number (for example {@code Rare}). */
    public final Double probability;

    private DropRate(String display, String original, Double probability) {
        this.display = display;
        this.original = original == null ? "" : original;
        this.probability = probability;
    }

    public static DropRate parse(String rate, String quantity) {
        String original = rate == null ? "" : rate.trim();
        if (original.isEmpty()) {
            return new DropRate("?", "", null);
        }
        String working = original
            .replace('\u00d7', 'x')
            .replace('\u2013', '-')
            .replace('\u2014', '-')
            .replace("~", "")
            .replace(",", "")
            .trim();
        working = QUANTITY_PREFIX.matcher(working).replaceFirst("");
        String lower = working.toLowerCase(Locale.ROOT).trim();
        if (lower.equals("always") || lower.equals("guaranteed") || lower.equals("every kill") || lower.equals("100%")) {
            return new DropRate("1/1", original, 1.0);
        }
        if (isWord(lower)) {
            return new DropRate(original, original, null);
        }
        if (QUANTITY_ONLY.matcher(lower).matches()) {
            return new DropRate(original, original, null);
        }
        List<Double> chances = new ArrayList<>();
        Matcher fractions = FRACTION.matcher(working);
        while (fractions.find()) {
            double numerator = Double.parseDouble(fractions.group(1));
            double denominator = Double.parseDouble(fractions.group(2));
            if (denominator > 0) {
                chances.add(numerator / denominator);
            }
        }
        if (chances.size() == 1) {
            double chance = chances.get(0);
            Matcher times = TIMES.matcher(working);
            if (times.find()) {
                chance = chance * Double.parseDouble(times.group(1));
            }
            if (chance < 0) {
                chance = 0;
            }
            return new DropRate(oneOver(Math.min(chance, 1.0)), original, Math.min(chance, 1.0));
        }
        if (chances.size() >= 2) {
            double first = chances.get(0);
            double second = chances.get(chances.size() - 1);
            String display = oneOver(first) + "\u2013" + oneOver(second);
            return new DropRate(display, original, (first + second) / 2.0);
        }
        Matcher percent = PERCENT.matcher(working);
        if (percent.find()) {
            double chance = Double.parseDouble(percent.group(1)) / 100.0;
            return new DropRate(oneOver(chance), original, chance);
        }
        if (quantity != null && QUANTITY_ONLY.matcher(quantity.trim().replace('\u2013', '-')).matches() && original.isEmpty()) {
            return new DropRate("?", "", null);
        }
        return new DropRate(original, original, null);
    }

    static String oneOver(double probability) {
        if (probability >= 1.0) {
            return "1/1";
        }
        if (probability <= 0) {
            return "1/\u221e";
        }
        double denominator = 1.0 / probability;
        double nearest = Math.rint(denominator);
        if (Math.abs(denominator - nearest) < 0.001) {
            return "1/" + (long) nearest;
        }
        String rounded = String.format(Locale.ROOT, "%.2f", denominator);
        if (rounded.endsWith("0")) {
            rounded = rounded.substring(0, rounded.length() - 1);
        }
        if (rounded.endsWith(".0")) {
            rounded = rounded.substring(0, rounded.length() - 2);
        }
        return "1/" + rounded;
    }

    private static boolean isWord(String lower) {
        return lower.equals("rare")
            || lower.equals("very rare")
            || lower.equals("uncommon")
            || lower.equals("common")
            || lower.equals("varies")
            || lower.equals("unknown");
    }
}
