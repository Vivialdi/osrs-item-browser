package com.osr.jei.pricing;

/**
 * One item's price taken from the shared {@code /latest} snapshot.
 * A failed refresh keeps the previous snapshot and marks it stale.
 */
public final class PriceLookup {

    public static final PriceLookup UNAVAILABLE = new PriceLookup(PriceQuote.MISSING, 0L, true, false);
    /** GE prices are turned off. This is not an error. */
    public static final PriceLookup HIDDEN = new PriceLookup(PriceQuote.MISSING, 0L, false, false);

    public final PriceQuote quote;
    public final long fetchedAtMs;
    public final boolean unavailable;
    public final boolean stale;

    public PriceLookup(PriceQuote quote, long fetchedAtMs, boolean unavailable, boolean stale) {
        this.quote = quote == null ? PriceQuote.MISSING : quote;
        this.fetchedAtMs = fetchedAtMs;
        this.unavailable = unavailable;
        this.stale = stale;
    }

    public static PriceLookup from(PriceSnapshot snapshot, boolean failed, int itemId, long nowMs) {
        if (snapshot == null || snapshot.fetchedAt() <= 0L) {
            return UNAVAILABLE;
        }
        boolean stale = failed || !snapshot.fresh(nowMs);
        return new PriceLookup(snapshot.quote(itemId), snapshot.fetchedAt(), false, stale);
    }
}
