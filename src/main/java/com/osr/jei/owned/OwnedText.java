package com.osr.jei.owned;

import java.awt.Color;

/**
 * Sidebar copy for owned counts. Until the bank has been checked, the copy
 * names that gap instead of showing a zero or a trailing plus.
 */
public final class OwnedText {

    public static final String SHORT_COLOR = "#dc6464";
    public static final String OK_COLOR = "#96dc96";
    public static final String UNKNOWN_COLOR = "#c8c8c8";

    public static final String BANK_UNKNOWN_TIP =
        "Inventory and worn equipment only. Open the bank once to include it. Bank not checked means those items are not in this count.";

    private OwnedText() {
    }

    public static String header(boolean inventoryKnown, boolean bankKnown, long have) {
        if (!inventoryKnown) {
            return "Owned: log in to count";
        }
        long owned = Math.max(0L, have);
        if (!bankKnown) {
            if (owned <= 0L) {
                return "Owned: bank not checked";
            }
            return String.format("Owned: %,d (bank not checked)", owned);
        }
        return String.format("Owned: %,d", owned);
    }

    /** HTML fragment. The item name stays outside this span. */
    public static String haveNeedHtml(long have, long need, boolean bankKnown) {
        long owned = Math.max(0L, have);
        long required = Math.max(0L, need);
        String color = bankKnown ? color(OwnedItems.shortfall(owned, required)) : UNKNOWN_COLOR;
        return "<font color='" + color + "'>" + haveNeedPlain(owned, required, bankKnown) + "</font>";
    }

    /** Plain have/need text for a tree row. */
    public static String haveNeedPlain(long have, long need, boolean bankKnown) {
        long owned = Math.max(0L, have);
        long required = Math.max(0L, need);
        if (!bankKnown) {
            if (owned <= 0L) {
                return String.format("bank not checked / need %,d", required);
            }
            return String.format("have %,d (bank not checked) / need %,d", owned, required);
        }
        return String.format("have %,d / need %,d", owned, required);
    }

    /** Green or red once the bank is known. Neutral while it has not been checked. */
    public static Color haveNeedColor(long have, long need, boolean bankKnown) {
        if (!bankKnown) {
            return neutral();
        }
        boolean shortOfKnown = OwnedItems.shortfall(Math.max(0L, have), Math.max(0L, need));
        return shortOfKnown ? new Color(0xDC, 0x64, 0x64) : new Color(0x96, 0xDC, 0x96);
    }

    /** HTML fragment for one base-material row. */
    public static String stillNeededHtml(long have, long need, boolean bankKnown) {
        long still = OwnedItems.stillNeeded(have, need);
        String color = bankKnown ? color(still > 0L) : UNKNOWN_COLOR;
        String text = bankKnown
            ? String.format("still needed %,d", still)
            : String.format("still needed %,d (bank not checked)", still);
        return "<font color='" + color + "'>" + text + "</font>";
    }

    private static Color neutral() {
        return new Color(0xC8, 0xC8, 0xC8);
    }

    private static String color(boolean shortOfKnown) {
        return shortOfKnown ? SHORT_COLOR : OK_COLOR;
    }
}
