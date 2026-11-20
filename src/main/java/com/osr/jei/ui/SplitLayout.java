package com.osr.jei.ui;

/** Pure layout math for the sidebar split between results and item details. */
public final class SplitLayout {

    public static final int MIN_DETAIL_PERCENT = 25;
    public static final int MAX_DETAIL_PERCENT = 75;

    private SplitLayout() {
    }

    public static int clampDetailPercent(int value) {
        return Math.max(MIN_DETAIL_PERCENT, Math.min(MAX_DETAIL_PERCENT, value));
    }

    /** Pixel offset of the divider from the top of a vertical split of {@code height}. */
    public static int topDividerPx(int height, int dividerSize, int detailPercent) {
        if (height <= 0) {
            return 0;
        }
        int pct = clampDetailPercent(detailPercent);
        int top = height - (height * pct / 100) - Math.max(0, dividerSize);
        return Math.max(0, top);
    }

    /** Detail-pane share implied by a divider the user dragged. */
    public static int detailPercentFromDivider(int height, int dividerLocation) {
        if (height <= 0) {
            return 45;
        }
        int topPct = (int) Math.round(100.0 * dividerLocation / height);
        topPct = Math.max(0, Math.min(100, topPct));
        return clampDetailPercent(100 - topPct);
    }
}
