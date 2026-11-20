package com.osr.jei.owned;

/**
 * Sidebar copy for owned counts. An unopened bank is labelled, and a short
 * count uses a trailing {@code +} so it is not presented as a full total.
 */
public final class OwnedText {

    public static final String SHORT_COLOR = "#dc6464";
    public static final String OK_COLOR = "#96dc96";
    public static final String UNKNOWN_COLOR = "#dcb44b";

    public static final String BANK_UNKNOWN_TIP =
        "Inventory and worn equipment only. Open the bank once this session to include it. A trailing + means the bank may hold more.";

    private OwnedText() {
    }

    public static String header(boolean inventoryKnown, boolean bankKnown, long have) {
        if (!inventoryKnown) {
            return "Owned: log in to count";
        }
        long owned = Math.max(0L, have);
        if (!bankKnown) {
            return String.format("Owned: %,d (bank not opened)", owned);
        }
        return String.format("Owned: %,d", owned);
    }

    /** HTML fragment. The item name stays outside this span. */
    public static String haveNeedHtml(long have, long need, boolean bankKnown) {
        long owned = Math.max(0L, have);
        long required = Math.max(0L, need);
        boolean shortOfKnown = OwnedItems.shortfall(owned, required);
        String color = color(shortOfKnown, bankKnown);
        String text = bankKnown
            ? String.format("have %,d / need %,d", owned, required)
            : String.format("have %,d+ / need %,d", owned, required);
        return "<font color='" + color + "'>" + text + "</font>";
    }

    /** HTML fragment for one base-material row. */
    public static String stillNeededHtml(long have, long need, boolean bankKnown) {
        long still = OwnedItems.stillNeeded(have, need);
        boolean shortOfKnown = still > 0L;
        String color = color(shortOfKnown, bankKnown);
        String text = !bankKnown && shortOfKnown
            ? String.format("still needed %,d+", still)
            : String.format("still needed %,d", still);
        return "<font color='" + color + "'>" + text + "</font>";
    }

    private static String color(boolean shortOfKnown, boolean bankKnown) {
        if (!shortOfKnown) {
            return OK_COLOR;
        }
        return bankKnown ? SHORT_COLOR : UNKNOWN_COLOR;
    }
}
