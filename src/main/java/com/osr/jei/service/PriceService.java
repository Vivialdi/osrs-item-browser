package com.osr.jei.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Singleton;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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

    // Simple in-memory cache: item ID -> price in gp
    private final ConcurrentHashMap<Integer, Long> cache = new ConcurrentHashMap<>();

    /**
     * Returns the average GE price for the given item ID, or -1 if unavailable.
     * Call this on a background thread — it makes a network request.
     */
    public long getPrice(int itemId) {
        // Return cached value if we have it
        if (cache.containsKey(itemId)) {
            return cache.get(itemId);
        }

        try {
            URL url = new URL(PRICES_API + "?id=" + itemId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(8000);

            if (conn.getResponseCode() != 200) {
                return -1;
            }

            try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                // Use new JsonParser().parse() for compatibility with older Gson versions
                JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
                JsonObject data = root.getAsJsonObject("data");
                if (data == null || !data.has(String.valueOf(itemId))) {
                    return -1;
                }

                JsonObject entry = data.getAsJsonObject(String.valueOf(itemId));
                long high = entry.has("high") ? entry.get("high").getAsLong() : -1;
                long low  = entry.has("low")  ? entry.get("low").getAsLong()  : -1;

                long price;
                if (high > 0 && low > 0) {
                    price = (high + low) / 2;   // average of buy/sell
                } else if (high > 0) {
                    price = high;
                } else {
                    price = low;
                }

                if (price > 0) {
                    cache.put(itemId, price);
                }
                return price;
            }
        } catch (Exception e) {
            log.warn("Could not fetch price for item {}: {}", itemId, e.getMessage());
            return -1;
        }
    }
}
