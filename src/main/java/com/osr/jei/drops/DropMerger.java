package com.osr.jei.drops;

import com.osr.jei.model.DropEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One drop row per monster and combat level. The wiki lists a separate line
 * for each quantity, so the same monster can appear twice. Those lines become
 * one row: quantities listed together, rates listed from most common to least.
 */
public final class DropMerger {

    /** Joins the wiki rate cells stored on a merged row. Not used in the label. */
    static final String RATE_SEP = " | ";

    private static final Pattern NUMBER = Pattern.compile("(\\d+)");

    private DropMerger() {
    }

    public static List<DropEntry> merge(List<DropEntry> drops) {
        Map<String, List<DropEntry>> groups = new LinkedHashMap<>();
        if (drops != null) {
            for (DropEntry drop : drops) {
                if (drop == null || drop.getSource() == null || drop.getSource().trim().isEmpty()) {
                    continue;
                }
                groups.computeIfAbsent(key(drop), ignored -> new ArrayList<>()).add(drop);
            }
        }
        List<DropEntry> merged = new ArrayList<>();
        for (List<DropEntry> group : groups.values()) {
            merged.add(group.size() == 1 ? group.get(0) : combine(group));
        }
        return merged;
    }

    /** Compact rate for the row. A single wiki cell keeps its existing display. */
    public static String rateLabel(String rate, String quantity) {
        if (rate == null || rate.trim().isEmpty()) {
            return "";
        }
        if (!rate.contains(RATE_SEP)) {
            return displayOf(DropRate.parse(rate, quantity));
        }
        List<String> labels = new ArrayList<>();
        for (String part : rate.split("\\|")) {
            String display = displayOf(DropRate.parse(part.trim(), null));
            if (!display.isEmpty() && !labels.contains(display)) {
                labels.add(display);
            }
        }
        return String.join(", ", labels);
    }

    /** Tooltip text. Merged rows keep every original wiki fraction. */
    public static String tooltipRate(DropEntry drop) {
        String rate = drop == null || drop.getRate() == null ? "" : drop.getRate();
        if (rate.contains(RATE_SEP)) {
            return rate.replace(RATE_SEP, ", ");
        }
        DropRate parsed = DropRate.parse(rate, drop == null ? null : drop.getQuantity());
        if (parsed.original != null && !parsed.original.isEmpty()) {
            return parsed.original;
        }
        return parsed.display == null ? "" : parsed.display;
    }

    private static DropEntry combine(List<DropEntry> group) {
        DropEntry first = group.get(0);
        DropEntry combined = new DropEntry();
        combined.setSource(first.getSource());
        combined.setLevel(first.getLevel());
        combined.setQuantity(quantities(group));
        combined.setRate(rates(group));
        combined.setRarity(first.getRarity() == null ? "" : first.getRarity());
        return combined;
    }

    private static String quantities(List<DropEntry> group) {
        List<String> values = new ArrayList<>();
        for (DropEntry drop : group) {
            String quantity = drop.getQuantity() == null ? "" : drop.getQuantity().trim();
            if (quantity.isEmpty() || "?".equals(quantity) || values.contains(quantity)) {
                continue;
            }
            values.add(quantity);
        }
        values.sort((left, right) -> {
            int rank = Integer.compare(quantityRank(left), quantityRank(right));
            return rank != 0 ? rank : left.compareTo(right);
        });
        if (values.isEmpty()) {
            return "?";
        }
        return String.join(", ", values);
    }

    private static String rates(List<DropEntry> group) {
        List<Rated> rated = new ArrayList<>();
        for (int i = 0; i < group.size(); i++) {
            String original = group.get(i).getRate() == null ? "" : group.get(i).getRate().trim();
            if (original.isEmpty()) {
                continue;
            }
            boolean seen = false;
            for (Rated existing : rated) {
                if (existing.original.equals(original)) {
                    seen = true;
                    break;
                }
            }
            if (seen) {
                continue;
            }
            DropRate parsed = DropRate.parse(original, group.get(i).getQuantity());
            rated.add(new Rated(original, parsed.probability, i));
        }
        rated.sort((left, right) -> {
            if (left.probability == null && right.probability == null) {
                return Integer.compare(left.order, right.order);
            }
            if (left.probability == null) {
                return 1;
            }
            if (right.probability == null) {
                return -1;
            }
            int chance = Double.compare(right.probability, left.probability);
            return chance != 0 ? chance : Integer.compare(left.order, right.order);
        });
        if (rated.isEmpty()) {
            return "";
        }
        StringBuilder joined = new StringBuilder();
        for (Rated entry : rated) {
            if (joined.length() > 0) {
                joined.append(RATE_SEP);
            }
            joined.append(entry.original);
        }
        return joined.toString();
    }

    private static String displayOf(DropRate parsed) {
        if (parsed == null || parsed.display == null || parsed.display.isEmpty() || "?".equals(parsed.display)) {
            return "";
        }
        return parsed.display;
    }

    private static int quantityRank(String quantity) {
        Matcher matcher = NUMBER.matcher(quantity);
        if (!matcher.find()) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private static String key(DropEntry drop) {
        return norm(drop.getSource()) + "\n" + norm(drop.getLevel());
    }

    private static String norm(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static final class Rated {
        private final String original;
        private final Double probability;
        private final int order;

        private Rated(String original, Double probability, int order) {
            this.original = original;
            this.probability = probability;
            this.order = order;
        }
    }
}
