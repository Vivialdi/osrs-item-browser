package com.osr.jei.pricing;

import com.google.gson.JsonObject;

/**
 * One item's latest GE quote from the OSRS Wiki prices API.
 * {@code -1} means that side was missing.
 */
public final class PriceQuote {

    public static final PriceQuote MISSING = new PriceQuote(-1, -1, -1);

    /** Instant-buy price ({@code high}). */
    public final long instantBuy;
    /** Instant-sell price ({@code low}). */
    public final long instantSell;
    /** Midpoint when both sides exist, otherwise whichever side is present. */
    public final long mid;

    public PriceQuote(long instantBuy, long instantSell, long mid) {
        this.instantBuy = instantBuy;
        this.instantSell = instantSell;
        this.mid = mid;
    }

    public boolean known() {
        return instantBuy > 0 || instantSell > 0 || mid > 0;
    }

    /** Price a player pays to buy immediately, falling back to the mid. */
    public long buyPrice() {
        if (instantBuy > 0) {
            return instantBuy;
        }
        return mid > 0 ? mid : -1;
    }

    public static PriceQuote fromItem(JsonObject entry) {
        if (entry == null) {
            return MISSING;
        }
        long high = entry.has("high") && !entry.get("high").isJsonNull()
            ? entry.get("high").getAsLong() : -1;
        long low = entry.has("low") && !entry.get("low").isJsonNull()
            ? entry.get("low").getAsLong() : -1;
        long mid;
        if (high > 0 && low > 0) {
            mid = (high + low) / 2;
        } else if (high > 0) {
            mid = high;
        } else {
            mid = low;
        }
        return new PriceQuote(high, low, mid);
    }

    public static PriceQuote fromRoot(JsonObject root, int itemId) {
        if (root == null) {
            return MISSING;
        }
        JsonObject data = root.getAsJsonObject("data");
        if (data == null || !data.has(String.valueOf(itemId))) {
            return MISSING;
        }
        return fromItem(data.getAsJsonObject(String.valueOf(itemId)));
    }
}
