package com.osr.jei.pricing;

import com.google.gson.JsonObject;
import com.osr.jei.model.SearchItem;
import com.osr.jei.search.ItemSearch;
import com.osr.jei.ui.SplitLayout;
import com.osr.jei.wiki.BoundedCache;
import com.osr.jei.wiki.WikiHttp;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PriceAndAlchemyTest {

    @Test
    public void missingPricesDoNotInventProfit() {
        assertNull(Alchemy.compute(0, 100, 180));
        Alchemy.Result noGe = Alchemy.compute(300, -1, 180);
        assertEquals(300, noGe.haValue);
        assertNull(noGe.profit);
        Alchemy.Result noNature = Alchemy.compute(300, 50, -1);
        assertNull(noNature.profit);
        Alchemy.Result profit = Alchemy.compute(1000, 100, 200);
        assertEquals(Long.valueOf(700), profit.profit);
        Alchemy.Result loss = Alchemy.compute(100, 50, 180);
        assertEquals(Long.valueOf(-130), loss.profit);
    }

    @Test
    public void snapshotKeepsKnownPricesAndTreatsAbsentIdsAsMissing() {
        JsonObject entry = new JsonObject();
        entry.addProperty("high", 120);
        entry.addProperty("low", 80);
        JsonObject data = new JsonObject();
        data.add("561", entry);
        JsonObject root = new JsonObject();
        root.add("data", data);

        long now = 1_000_000L;
        PriceSnapshot snapshot = PriceSnapshot.fromRoot(root, now);
        assertTrue(snapshot.fresh(now + PriceSnapshot.TTL_MS - 1));
        assertFalse(snapshot.fresh(now + PriceSnapshot.TTL_MS));
        assertEquals(120L, snapshot.quote(561).instantBuy);
        assertEquals(80L, snapshot.quote(561).instantSell);
        assertEquals(100L, snapshot.quote(561).mid);
        assertEquals(120L, snapshot.quote(561).buyPrice());
        assertFalse(snapshot.quote(999).known());
        assertFalse(PriceSnapshot.empty().fresh(now));
    }

    @Test
    public void prefixMatchesRankAheadOfLaterIds() {
        SearchItem runePlate = new SearchItem(1127, "Rune platebody", true, 39000);
        SearchItem air = new SearchItem(556, "Air rune", false, 3);
        List<SearchItem> ranked = ItemSearch.rank(Arrays.asList(air, runePlate), "rune", 10);
        assertEquals("Rune platebody", ranked.get(0).name);
        assertEquals("Air rune", ranked.get(1).name);
        assertEquals("rune platebody", runePlate.nameLower);
    }

    @Test
    public void cacheDropsTheLeastRecentlyUsed() {
        BoundedCache<String, String> cache = new BoundedCache<>(2);
        cache.put("a", "1");
        cache.put("b", "2");
        assertEquals("1", cache.get("a"));
        cache.put("c", "3");
        assertNull(cache.get("b"));
        assertEquals("1", cache.get("a"));
        assertEquals("3", cache.get("c"));
    }

    @Test
    public void splitAndHttpHelpersStayInsideTheirBounds() {
        assertEquals(25, SplitLayout.clampDetailPercent(1));
        assertEquals(75, SplitLayout.clampDetailPercent(90));
        assertEquals(45, SplitLayout.clampDetailPercent(45));
        assertTrue(SplitLayout.topDividerPx(200, 6, 45) < 200);
        assertTrue(WikiHttp.withMaxlag("https://oldschool.runescape.wiki/api.php?action=parse").contains("maxlag=5"));
        assertTrue(WikiHttp.withMaxlag("https://oldschool.runescape.wiki/api.php?maxlag=1").contains("maxlag=1"));
        assertEquals(2_000L, WikiHttp.retryAfterMillis("2", 9_000L));
        assertEquals(WikiHttp.MAX_BACKOFF_MS, WikiHttp.retryAfterMillis("120", 1_000L));
        assertEquals(1_500L, WikiHttp.retryAfterMillis("nope", 1_500L));
    }
}
