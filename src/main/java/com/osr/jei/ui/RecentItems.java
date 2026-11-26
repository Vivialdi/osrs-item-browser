package com.osr.jei.ui;

import java.util.List;

/**
 * Recent ids are newest first. The oldest entry is at the end of the list.
 */
public final class RecentItems {

    /** The list length Recent used before Max recent items existed. */
    public static final int PREVIOUS_CAP = 12;

    private RecentItems() {
    }

    public static int cap(RecentLimit limit) {
        return limit == null ? RecentLimit.TWELVE.count() : limit.count();
    }

    /** Drops the oldest entries past {@code cap}. Returns whether the list changed. */
    public static boolean trim(List<Integer> newestFirst, int cap) {
        if (newestFirst == null) {
            return false;
        }
        int limit = Math.max(0, cap);
        boolean changed = false;
        while (newestFirst.size() > limit) {
            newestFirst.remove(newestFirst.size() - 1);
            changed = true;
        }
        return changed;
    }

    public static void clear(List<Integer> newestFirst) {
        if (newestFirst != null) {
            newestFirst.clear();
        }
    }
}
