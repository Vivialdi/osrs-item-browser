package com.osr.jei.pricing;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GeLimitsTest {

    @Test
    public void taxIsTwoPercentRoundedDownAndCapped() {
        assertEquals(0L, GeTax.tax(49, false));
        assertEquals(1L, GeTax.tax(50, false));
        assertEquals(2L, GeTax.tax(100, false));
        assertEquals(2L, GeTax.tax(149, false));
        assertEquals(5_000_000L, GeTax.tax(250_000_000L, false));
        assertEquals(5_000_000L, GeTax.tax(300_000_000L, false));
        assertEquals(4_999_999L, GeTax.tax(249_999_999L, false));
        assertEquals(0L, GeTax.tax(300_000_000L, true));
        assertEquals(0L, GeTax.tax(0, false));
        assertEquals(0L, GeTax.tax(-10, false));

        assertTrue(GeTax.exempt(ItemID.OSRS_BOND));
        assertTrue(GeTax.exempt(ItemID._4DOSE1ENERGY));
        assertTrue(GeTax.exempt(ItemID.HAMMER));
        assertTrue(GeTax.exempt(ItemID.WATERING_CAN_0));
        assertTrue(GeTax.exempt(ItemID.RING_OF_DUELING_8));
        assertTrue(GeTax.exempt(ItemID.NECKLACE_OF_MINIGAMES_8));
        assertTrue(GeTax.exempt(ItemID.POH_TABLET_FORTISTELEPORT));
        assertFalse(GeTax.exempt(ItemID.NATURERUNE));
        assertFalse(GeTax.exempt(ItemID.RING_OF_DUELING_1));
        assertFalse(GeTax.exempt(ItemID.WATERING_CAN_8));

        assertNull(GeTax.margin(1_000, -1, false));
        assertNull(GeTax.margin(-1, 1_100, false));
        assertEquals(Long.valueOf(78L), GeTax.margin(1_000, 1_100, false));
        assertEquals(Long.valueOf(100L), GeTax.margin(1_000, 1_100, true));
        assertEquals(Long.valueOf(294_999_900L), GeTax.margin(100, 300_000_000L, false));
        assertEquals(1L, GeTax.tax(60, false));
        assertEquals(Long.valueOf(-41L), GeTax.margin(100, 60, false));
        assertNull(GeTax.perLimit(null, 10));
        assertNull(GeTax.perLimit(78L, null));
        assertNull(GeTax.perLimit(78L, 0));
        assertEquals(Long.valueOf(780L), GeTax.perLimit(78L, 10));
    }

    @Test
    public void mappingKeepsPositiveLimitsAndIgnoresTheRest() {
        JsonObject nature = new JsonObject();
        nature.addProperty("id", ItemID.NATURERUNE);
        nature.addProperty("name", "Nature rune");
        nature.addProperty("limit", 15000);
        JsonObject noLimit = new JsonObject();
        noLimit.addProperty("id", 2);
        noLimit.addProperty("name", "Cannonball");
        JsonObject zero = new JsonObject();
        zero.addProperty("id", 3);
        zero.addProperty("limit", 0);
        JsonObject broken = new JsonObject();
        broken.addProperty("id", 4);
        broken.add("limit", new JsonObject());
        JsonArray mapping = new JsonArray();
        mapping.add(nature);
        mapping.add(noLimit);
        mapping.add(zero);
        mapping.add(broken);
        mapping.add("junk");

        long now = 5_000L;
        BuyLimitSnapshot snapshot = BuyLimitSnapshot.fromMapping(mapping, now);
        assertTrue(snapshot.fresh(now + BuyLimitSnapshot.TTL_MS - 1));
        assertFalse(snapshot.fresh(now + BuyLimitSnapshot.TTL_MS));
        assertEquals(Integer.valueOf(15000), snapshot.limit(ItemID.NATURERUNE));
        assertNull(snapshot.limit(2));
        assertNull(snapshot.limit(3));
        assertNull(snapshot.limit(4));
        assertFalse(BuyLimitSnapshot.empty().fresh(now));
        assertNull(BuyLimitSnapshot.fromMapping(new JsonObject(), now).limit(ItemID.NATURERUNE));
    }

    @Test
    public void marketLineShowsPostTaxMarginAndDeemphasizesIronman() {
        PriceQuote quote = new PriceQuote(1_000, 1_100, 1_050);
        MarketQuote normal = MarketQuote.describe(false, ItemID.NATURERUNE, quote, 10, true);
        assertEquals(
            "Instant sell: 1,100 gp \u00b7 margin 78 gp after tax \u00b7 limit 10 \u00b7 per 4h 780 gp",
            normal.text);
        assertEquals(1, normal.marginSign);

        MarketQuote iron = MarketQuote.describe(true, ItemID.NATURERUNE, quote, 10, true);
        assertTrue(iron.text.startsWith("Reference only"));
        assertFalse(iron.text.contains("GE buy"));
        assertTrue(iron.text.contains("margin 78 gp after tax"));

        MarketQuote exempt = MarketQuote.describe(false, ItemID.HAMMER, quote, 50, true);
        assertTrue(exempt.text.contains("margin 100 gp, tax exempt"));
        assertTrue(exempt.text.contains("per 4h 5,000 gp"));

        MarketQuote loss = MarketQuote.describe(false, ItemID.NATURERUNE, new PriceQuote(100, 60, 80), 8, true);
        assertEquals(-1, loss.marginSign);
        assertTrue(loss.text.contains("margin -41 gp after tax"));
        assertTrue(loss.text.contains("per 4h -328 gp"));

        MarketQuote loading = MarketQuote.describe(false, ItemID.NATURERUNE, PriceQuote.MISSING, null, false);
        assertEquals("buy limit loading", loading.text);
        assertEquals(0, loading.marginSign);

        MarketQuote unlisted = MarketQuote.describe(true, ItemID.NATURERUNE, PriceQuote.MISSING, null, true);
        assertEquals("Reference only \u00b7 buy limit not listed", unlisted.text);
    }
}
