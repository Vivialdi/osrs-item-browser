package com.osr.jei.drops;

import com.osr.jei.model.DropEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Orders an already-parsed drop list. Missing combat levels sort after every real level. */
public final class DropSorter {

    private static final Pattern NUMBER = Pattern.compile("(\\d+)");

    private DropSorter() {
    }

    public static List<DropEntry> ordered(List<DropEntry> drops, DropSort sort) {
        List<DropEntry> copy = new ArrayList<>();
        if (drops != null) {
            copy.addAll(drops);
        }
        DropSort mode = sort == null ? DropSort.RATE : sort;
        copy.sort(comparator(mode));
        return copy;
    }

    /** First combat level in the cell, or {@link Integer#MAX_VALUE} when there is none. */
    public static int combatRank(String level) {
        if (level == null) {
            return Integer.MAX_VALUE;
        }
        String trimmed = level.trim();
        if (trimmed.isEmpty() || trimmed.equals("?") || trimmed.equalsIgnoreCase("n/a") || trimmed.equals("-")) {
            return Integer.MAX_VALUE;
        }
        Matcher matcher = NUMBER.matcher(trimmed);
        if (!matcher.find()) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private static Comparator<DropEntry> comparator(DropSort sort) {
        Comparator<DropEntry> name = new Comparator<DropEntry>() {
            @Override
            public int compare(DropEntry left, DropEntry right) {
                return source(left).compareToIgnoreCase(source(right));
            }
        };
        if (sort == DropSort.NAME) {
            return name;
        }
        if (sort == DropSort.COMBAT) {
            return new Comparator<DropEntry>() {
                @Override
                public int compare(DropEntry left, DropEntry right) {
                    int levels = Integer.compare(combatRank(left.getLevel()), combatRank(right.getLevel()));
                    return levels != 0 ? levels : name.compare(left, right);
                }
            };
        }
        return new Comparator<DropEntry>() {
            @Override
            public int compare(DropEntry left, DropEntry right) {
                Double leftChance = DropRate.parse(left.getRate(), left.getQuantity()).probability;
                Double rightChance = DropRate.parse(right.getRate(), right.getQuantity()).probability;
                if (leftChance == null && rightChance == null) {
                    return name.compare(left, right);
                }
                if (leftChance == null) {
                    return 1;
                }
                if (rightChance == null) {
                    return -1;
                }
                int rate = Double.compare(rightChance, leftChance);
                return rate != 0 ? rate : name.compare(left, right);
            }
        };
    }

    private static String source(DropEntry drop) {
        if (drop == null || drop.getSource() == null) {
            return "";
        }
        return drop.getSource();
    }
}
