package com.osr.jei.pricing;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.osr.jei.service.PriceService;
import com.osr.jei.wiki.WikiHttp;
import okhttp3.OkHttpClient;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PriceFeedTest {

    @Test
    public void freshQuoteWithinTheTtlHasNoAgeSuffix() {
        PriceQuote quote = new PriceQuote(10, 8, 9, 1_000L, 900L);
        long now = 1_000L * 1000L + PriceSnapshot.TTL_MS - 1L;
        assertEquals("", PriceAge.suffix(quote, now, now));
    }

    @Test
    public void staleQuoteAndSnapshotAgesAreShown() {
        long tradedAt = 1_700_000_000L;
        PriceQuote quote = new PriceQuote(10, 8, 9, tradedAt, tradedAt - 60);
        long now = tradedAt * 1000L + 8L * 60L * 1000L;
        assertEquals("8 min old", PriceAge.suffix(quote, now, now));

        long twoHours = tradedAt * 1000L + 2L * 3_600_000L;
        assertEquals("2 hr old", PriceAge.suffix(quote, twoHours, twoHours));

        long threeDays = tradedAt * 1000L + 3L * 86_400_000L;
        assertEquals("3 days old", PriceAge.suffix(quote, threeDays, threeDays));

        PriceQuote timeless = new PriceQuote(10, 8, 9);
        long fetched = 50_000L;
        long later = fetched + PriceSnapshot.TTL_MS;
        assertEquals("7 min old", PriceAge.suffix(timeless, fetched, later));
        assertEquals(PriceAge.UNAVAILABLE, "Grand Exchange prices are unavailable.");
    }

    @Test
    public void quoteKeepsTradeTimesAndLookupMarksAFailedSnapshot() {
        JsonObject entry = new JsonObject();
        entry.addProperty("high", 120);
        entry.addProperty("low", 80);
        entry.addProperty("highTime", 111);
        entry.addProperty("lowTime", 90);
        PriceQuote quote = PriceQuote.fromItem(entry);
        assertEquals(111L, quote.newestTimeSeconds());

        assertTrue(PriceLookup.from(PriceSnapshot.empty(), true, 2, 1_000L).unavailable);
        long fetched = 1_000L;
        PriceSnapshot fresh = new PriceSnapshot(fetched, java.util.Collections.singletonMap(2, quote));
        PriceLookup ready = PriceLookup.from(fresh, false, 2, fetched + 1_000L);
        assertFalse(ready.unavailable);
        assertFalse(ready.stale);
        assertEquals(120L, ready.quote.instantBuy);
        PriceLookup stale = PriceLookup.from(fresh, true, 2, fetched + PriceSnapshot.TTL_MS);
        assertFalse(stale.unavailable);
        assertTrue(stale.stale);
    }

    @Test
    public void oneLatestDownloadFeedsEveryItemAndAFailureDoesNotRetryImmediately() throws Exception {
        FakeHttp http = new FakeHttp();
        try {
            PriceService prices = new PriceService(http);
            CompletableFuture<PriceLookup> nature = prices.lookup(561);
            assertTrue(http.entered.await(2, TimeUnit.SECONDS));
            CompletableFuture<PriceLookup> law = prices.lookup(563);
            assertEquals(1, http.calls.get());
            http.blocked.complete(latest());
            assertEquals(100L, nature.join().quote.instantBuy);
            assertEquals(40L, law.join().quote.instantBuy);
            assertEquals(1, http.calls.get());
            assertFalse(prices.lookup(561).join().unavailable);
            assertEquals(1, http.calls.get());
        } finally {
            http.shutdown();
        }
    }

    @Test
    public void priceFailureWaitsUntilTheCallerAllowsAnotherAttempt() {
        FakeHttp http = new FakeHttp();
        http.block = false;
        http.failure = new IOException("offline");
        try {
            PriceService prices = new PriceService(http);
            assertTrue(prices.lookup(561).join().unavailable);
            assertEquals(1, http.calls.get());
            assertTrue(prices.lookup(563).join().unavailable);
            assertEquals(1, http.calls.get());
            prices.allowRetry();
            http.failure = null;
            PriceLookup loaded = prices.lookup(561).join();
            assertFalse(loaded.unavailable);
            assertEquals(100L, loaded.quote.instantBuy);
            assertEquals(2, http.calls.get());
        } finally {
            http.shutdown();
        }
    }

    private static JsonObject latest() {
        JsonObject nature = new JsonObject();
        nature.addProperty("high", 100);
        nature.addProperty("low", 90);
        nature.addProperty("highTime", 50);
        JsonObject law = new JsonObject();
        law.addProperty("high", 40);
        law.addProperty("low", 30);
        JsonObject data = new JsonObject();
        data.add("561", nature);
        data.add("563", law);
        JsonObject root = new JsonObject();
        root.add("data", data);
        return root;
    }

    private static final class FakeHttp extends WikiHttp {
        private final AtomicInteger calls = new AtomicInteger();
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CompletableFuture<JsonObject> blocked = new CompletableFuture<>();
        private volatile Throwable failure;
        private volatile boolean block = true;

        private FakeHttp() {
            super(new OkHttpClient(), new Gson());
        }

        @Override
        public CompletableFuture<JsonObject> getJson(String url) {
            calls.incrementAndGet();
            entered.countDown();
            if (failure != null) {
                CompletableFuture<JsonObject> failed = new CompletableFuture<>();
                failed.completeExceptionally(failure);
                return failed;
            }
            if (block) {
                return blocked;
            }
            return CompletableFuture.completedFuture(latest());
        }
    }
}
