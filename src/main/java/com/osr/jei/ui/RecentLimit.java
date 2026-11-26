package com.osr.jei.ui;

/**
 * How many icons Recent keeps. Each step is one full row of six.
 */
public enum RecentLimit {
    SIX(6),
    TWELVE(12),
    EIGHTEEN(18),
    TWENTY_FOUR(24),
    THIRTY(30),
    THIRTY_SIX(36);

    private final int count;

    RecentLimit(int count) {
        this.count = count;
    }

    public int count() {
        return count;
    }

    @Override
    public String toString() {
        return Integer.toString(count);
    }

    /** Nearest step. A tie rounds to the higher step, then stays inside 6 to 36. */
    public static RecentLimit nearest(int value) {
        int snapped = (int) (Math.round(value / 6.0) * 6);
        if (snapped < SIX.count) {
            snapped = SIX.count;
        }
        if (snapped > THIRTY_SIX.count) {
            snapped = THIRTY_SIX.count;
        }
        for (RecentLimit limit : values()) {
            if (limit.count == snapped) {
                return limit;
            }
        }
        return TWELVE;
    }
}
