package com.osr.jei.equipment;

import java.util.Arrays;

/** Canonical item ids in the worn container. Empty slots are {@code -1}. */
public final class WornGear {

    public static final int SLOTS = 14;
    public static final WornGear LOGGED_OUT = new WornGear(false, empty());

    public final boolean loggedIn;
    public final int[] ids;

    public WornGear(boolean loggedIn, int[] ids) {
        this.loggedIn = loggedIn;
        int[] copy = empty();
        if (ids != null) {
            System.arraycopy(ids, 0, copy, 0, Math.min(ids.length, copy.length));
        }
        this.ids = copy;
    }

    public int id(int slot) {
        if (slot < 0 || slot >= ids.length) {
            return -1;
        }
        return ids[slot];
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WornGear)) {
            return false;
        }
        WornGear that = (WornGear) other;
        return loggedIn == that.loggedIn && Arrays.equals(ids, that.ids);
    }

    @Override
    public int hashCode() {
        return (loggedIn ? 1 : 0) * 31 + Arrays.hashCode(ids);
    }

    private static int[] empty() {
        int[] ids = new int[SLOTS];
        Arrays.fill(ids, -1);
        return ids;
    }
}
