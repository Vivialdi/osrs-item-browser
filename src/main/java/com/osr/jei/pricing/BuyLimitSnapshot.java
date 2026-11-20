package com.osr.jei.pricing;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Buy limits from one {@code /mapping} response. Items without a positive
 * {@code limit} are absent, which is not the same as a limit of zero.
 */
public final class BuyLimitSnapshot {

    public static final long TTL_MS = 24L * 60L * 60L * 1000L;

    private final long fetchedAt;
    private final Map<Integer, Integer> limits;

    private BuyLimitSnapshot(long fetchedAt, Map<Integer, Integer> limits) {
        this.fetchedAt = fetchedAt;
        this.limits = limits;
    }

    public static BuyLimitSnapshot empty() {
        return new BuyLimitSnapshot(0L, Collections.emptyMap());
    }

    public boolean fresh(long nowMs) {
        return fetchedAt > 0L && nowMs - fetchedAt < TTL_MS;
    }

    /** Null when this id was not given a positive limit. */
    public Integer limit(int itemId) {
        return limits.get(itemId);
    }

    public static BuyLimitSnapshot fromMapping(JsonElement root, long nowMs) {
        Map<Integer, Integer> limits = new HashMap<>();
        if (root != null && root.isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray()) {
                if (element == null || !element.isJsonObject()) {
                    continue;
                }
                JsonObject item = element.getAsJsonObject();
                if (!item.has("id") || item.get("id").isJsonNull()) {
                    continue;
                }
                if (!item.has("limit") || item.get("limit").isJsonNull()) {
                    continue;
                }
                try {
                    int id = item.get("id").getAsInt();
                    int limit = item.get("limit").getAsInt();
                    if (id >= 0 && limit > 0) {
                        limits.put(id, limit);
                    }
                } catch (RuntimeException ignored) {
                    // skip a malformed row
                }
            }
        }
        return new BuyLimitSnapshot(nowMs, limits);
    }
}
