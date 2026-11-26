package com.osr.jei.drops;

import com.osr.jei.model.DropEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
/**
 * One drop row per monster and combat level. The wiki lists a separate line
 * for each quantity. Those lines become one row: the level, then each
 * quantity with its own rate, most common first.
 */
public final class DropMerger {

    /** Joins the wiki rate cells stored on a merged row. Not used in the label. */
    static final String RATE_SEP = " | ";

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

    /**
     * One variant is {@code Lv. 48 · 5 at 1/51.2}. Several variants put the
     * level on its own line, then {@code 7 at 1/6.4} and {@code 2 at 1/8.53}.
     */
    public static String detail(DropEntry drop) {
        if (drop == null) {
            return "?";
        }
        List<String> pairs = pairs(drop);
        String level = levelText(drop);
        if (pairs.size() <= 1) {
            String pair = pairs.isEmpty() ? "" : pairs.get(0);
            if (level.isEmpty()) {
                return pair.isEmpty() ? "?" : pair;
            }
            if (pair.isEmpty()) {
                return level;
            }
            return level + " \u00b7 " + pair;
        }
        StringBuilder text = new StringBuilder();
        if (!level.isEmpty()) {
            text.append(level);
        }
        for (String pair : pairs) {
            if (pair.isEmpty()) {
                continue;
            }
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(pair);
        }
        return text.length() == 0 ? "?" : text.toString();
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
        List<Rated> rated = new ArrayList<>();
        for (int i = 0; i < group.size(); i++) {
            String quantity = shownQuantity(group.get(i).getQuantity());
            String original = group.get(i).getRate() == null ? "" : group.get(i).getRate().trim();
            if (quantity.isEmpty() && original.isEmpty()) {
                continue;
            }
            boolean seen = false;
            for (Rated existing : rated) {
                if (existing.quantity.equals(quantity) && existing.original.equals(original)) {
                    seen = true;
                    break;
                }
            }
            if (seen) {
                continue;
            }
            DropRate parsed = DropRate.parse(original, quantity.isEmpty() ? null : quantity);
            rated.add(new Rated(quantity, original, parsed.probability, i));
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
        DropEntry combined = new DropEntry();
        combined.setSource(first.getSource());
        combined.setLevel(first.getLevel() == null ? "" : first.getLevel().trim());
        combined.setQuantity(join(rated, true));
        combined.setRate(join(rated, false));
        combined.setRarity(first.getRarity() == null ? "" : first.getRarity());
        return combined;
    }

    private static String join(List<Rated> rated, boolean quantity) {
        StringBuilder joined = new StringBuilder();
        for (Rated entry : rated) {
            if (joined.length() > 0) {
                joined.append(RATE_SEP);
            }
            joined.append(quantity ? entry.quantity : entry.original);
        }
        return joined.toString();
    }

    private static List<String> pairs(DropEntry drop) {
        String rate = drop.getRate() == null ? "" : drop.getRate();
        String quantity = drop.getQuantity() == null ? "" : drop.getQuantity();
        boolean several = rate.contains(RATE_SEP) || quantity.contains(RATE_SEP);
        String[] rates = several ? rate.split("\\|", -1) : new String[] {rate};
        String[] quantities = several ? quantity.split("\\|", -1) : new String[] {quantity};
        int count = Math.max(rates.length, quantities.length);
        List<String> pairs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String qty = shownQuantity(i < quantities.length ? quantities[i] : "");
            String original = i < rates.length ? rates[i].trim() : "";
            String display = displayOf(DropRate.parse(original, qty.isEmpty() ? null : qty));
            if (!qty.isEmpty() && !display.isEmpty()) {
                pairs.add(qty + " at " + display);
            } else if (!display.isEmpty()) {
                pairs.add(display);
            } else if (!qty.isEmpty()) {
                pairs.add(qty);
            }
        }
        return pairs;
    }

    private static String levelText(DropEntry drop) {
        String level = drop.getLevel() == null ? "" : drop.getLevel().trim();
        if (level.isEmpty() || "?".equals(level)) {
            return "";
        }
        return "Lv. " + level;
    }

    private static String shownQuantity(String quantity) {
        String text = quantity == null ? "" : quantity.trim();
        if (text.isEmpty() || "?".equals(text)) {
            return "";
        }
        return text;
    }

    private static String displayOf(DropRate parsed) {
        if (parsed == null || parsed.display == null || parsed.display.isEmpty() || "?".equals(parsed.display)) {
            return "";
        }
        return parsed.display;
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
        private final String quantity;
        private final String original;
        private final Double probability;
        private final int order;

        private Rated(String quantity, String original, Double probability, int order) {
            this.quantity = quantity;
            this.original = original;
            this.probability = probability;
            this.order = order;
        }
    }
}
