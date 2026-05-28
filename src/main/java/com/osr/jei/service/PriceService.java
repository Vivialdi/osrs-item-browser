package com.osr.jei.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches live Grand Exchange prices from the OSRS Wiki prices API.
 * Results are cached so we don't spam the API for the same item.
 */
@Slf4j
@Singleton
public class PriceService {

    private static final String PRICES_API = "https://prices.runescape.wiki/api/v1/osrs/latest";
    private static final String USER_AGENT = "OSRS-JEI-RuneLite-Plugin/1.0 (github contact)";

    @Inject private OkHttpClient okHttpClient;

    // Simple in-memory cache: item ID -> price in gp
    private final ConcurrentHashMap<Integer, Long> cache = new ConcurrentHashMap<>();

    /**
     * Returns the average GE price for the given item ID, or -1 if unavailable.
     * Call this on a background thread — it makes a network request.
     */
    public long getPrice(int itemId) {
        if (cache.containsKey(itemId)) {
            return cache.get(itemId);
        }

        try {
            Request request = new Request.Builder()
                .url(PRICES_API + "?id=" + itemId)
                .header("User-Agent", USER_AGENT)
                .build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return -1;

                JsonObject root = new JsonParser()
                    .parse(response.body().string()).getAsJsonObject();
                JsonObject data = root.getAsJsonObject("data");
                if (data == null || !data.has(String.valueOf(itemId))) return -1;

                JsonObject entry = data.getAsJsonObject(String.valueOf(itemId));
                long high = entry.has("high") ? entry.get("high").getAsLong() : -1;
                long low  = entry.has("low")  ? entry.get("low").getAsLong()  : -1;

                long price;
                if (high > 0 && low > 0) {
                    price = (high + low) / 2;
                } else if (high > 0) {
                    price = high;
                } else {
                    price = low;
                }

                if (price > 0) cache.put(itemId, price);
                return price;
            }
        } catch (Exception e) {
            log.warn("Could not fetch price for item {}: {}", itemId, e.getMessage());
            return -1;
        }
    }
}
