package com.osr.jei.pricing;

/**
 * Age text for a GE quote. Shown once the data is at least as old as the
 * shared price snapshot's TTL.
 */
public final class PriceAge {

    public static final String UNAVAILABLE = "Grand Exchange prices are unavailable.";

    private PriceAge() {
    }

    /**
     * {@code "12 min old"} when the quote (or, if it has no trade time, the
     * snapshot) is older than {@link PriceSnapshot#TTL_MS}. Empty otherwise.
     */
    public static String suffix(PriceQuote quote, long snapshotFetchedAtMs, long nowMs) {
        long dataMs = 0L;
        if (quote != null && quote.newestTimeSeconds() > 0L) {
            dataMs = quote.newestTimeSeconds() * 1000L;
        } else if (snapshotFetchedAtMs > 0L) {
            dataMs = snapshotFetchedAtMs;
        }
        if (dataMs <= 0L || nowMs < dataMs) {
            return "";
        }
        long age = nowMs - dataMs;
        if (age < PriceSnapshot.TTL_MS) {
            return "";
        }
        return format(age) + " old";
    }

    static String format(long ageMs) {
        long minutes = ageMs / 60_000L;
        if (minutes < 1L) {
            return "under a minute";
        }
        if (minutes < 90L) {
            return minutes + " min";
        }
        long hours = ageMs / 3_600_000L;
        if (hours < 48L) {
            return hours + " hr";
        }
        long days = ageMs / 86_400_000L;
        if (days < 1L) {
            days = 1L;
        }
        return days == 1L ? "1 day" : days + " days";
    }
}
