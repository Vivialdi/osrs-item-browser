package com.osr.jei.pricing;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * One bulk {@code /latest} response. Items absent from the map have no GE price
 * for the life of this snapshot, so they are not requested again until it expires.
 */
public final class PriceSnapshot {

    public static final long TTL_MS = 7 * 60 * 1000L;

    private final long fetchedAt;
    private final Map<Integer, PriceQuote> quotes;

    public PriceSnapshot(long fetchedAt, Map<Integer, PriceQuote> quotes) {
        this.fetchedAt = fetchedAt;
        this.quotes = quotes;
    }

    public static PriceSnapshot empty() {
        return new PriceSnapshot(0, Collections.emptyMap());
    }

    public boolean fresh(long nowMs) {
        return fetchedAt > 0 && nowMs - fetchedAt < TTL_MS;
    }

    public PriceQuote quote(int itemId) {
        PriceQuote quote = quotes.get(itemId);
        return quote == null ? PriceQuote.MISSING : quote;
    }

    public static PriceSnapshot fromRoot(JsonObject root, long nowMs) {
        Map<Integer, PriceQuote> quotes = new HashMap<>();
        if (root != null && root.has("data") && root.get("data").isJsonObject()) {
            JsonObject data = root.getAsJsonObject("data");
            for (Map.Entry<String, JsonElement> entry : data.entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }
                try {
                    int id = Integer.parseInt(entry.getKey());
                    quotes.put(id, PriceQuote.fromItem(entry.getValue().getAsJsonObject()));
                } catch (NumberFormatException ignored) {
                    // skip unexpected keys
                }
            }
        }
        return new PriceSnapshot(nowMs, quotes);
    }
}
