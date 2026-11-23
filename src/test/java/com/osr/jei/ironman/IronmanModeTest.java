package com.osr.jei.ironman;

import com.osr.jei.model.ShopEntry;
import com.osr.jei.pricing.Alchemy;
import com.osr.jei.pricing.PriceQuote;
import org.junit.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IronmanModeTest {

    @Test
    public void autoDetectsIronAccountsAndOnOffOverride() {
        assertFalse(IronmanAccounts.effective(IronmanMode.AUTO, IronmanAccounts.NORMAL));
        assertTrue(IronmanAccounts.effective(IronmanMode.AUTO, IronmanAccounts.IRONMAN));
        assertTrue(IronmanAccounts.effective(IronmanMode.AUTO, IronmanAccounts.ULTIMATE));
        assertTrue(IronmanAccounts.effective(IronmanMode.AUTO, IronmanAccounts.HARDCORE));
        assertTrue(IronmanAccounts.effective(IronmanMode.AUTO, IronmanAccounts.GROUP));
        assertTrue(IronmanAccounts.effective(IronmanMode.AUTO, IronmanAccounts.HARDCORE_GROUP));
        assertTrue(IronmanAccounts.effective(IronmanMode.AUTO, IronmanAccounts.UNRANKED_GROUP));
        assertFalse(IronmanAccounts.effective(IronmanMode.AUTO, 7));

        assertTrue(IronmanAccounts.effective(IronmanMode.ON, IronmanAccounts.NORMAL));
        assertFalse(IronmanAccounts.effective(IronmanMode.OFF, IronmanAccounts.ULTIMATE));
        assertFalse(IronmanAccounts.effective(null, IronmanAccounts.IRONMAN));

        assertFalse(IronmanAccounts.shouldRefreshDetail(false, false));
        assertFalse(IronmanAccounts.shouldRefreshDetail(true, true));
        assertTrue(IronmanAccounts.shouldRefreshDetail(false, true));
        assertTrue(IronmanAccounts.shouldRefreshDetail(true, false));
    }

    @Test
    public void ironmanSectionsLeadWithShopsDropsAndCrafting() {
        List<String> iron = DetailLayout.order(true);
        assertTrue(iron.indexOf(DetailLayout.SHOPS) < iron.indexOf(DetailLayout.DROPS));
        assertTrue(iron.indexOf(DetailLayout.DROPS) < iron.indexOf(DetailLayout.CRAFTING));
        assertTrue(iron.indexOf(DetailLayout.CRAFTING) < iron.indexOf(DetailLayout.GE));
        assertEquals(DetailLayout.SHOPS, iron.get(0));
        assertFalse(iron.contains(DetailLayout.ALCH));

        List<String> standard = DetailLayout.order(false);
        assertTrue(standard.indexOf(DetailLayout.GE) < standard.indexOf(DetailLayout.SHOPS));
        assertTrue(standard.indexOf(DetailLayout.SHOPS) < standard.indexOf(DetailLayout.CRAFTING));
    }

    @Test
    public void shopAndAlchCopyDoNotAssumeAGeBuy() {
        ShopEntry shop = new ShopEntry("Bob's Brilliant Axes", 10, 5, "gp", true);
        TradeCopy.ShopLine ironShop = TradeCopy.shop(true, shop, 1_000);
        String shopText = (ironShop.stock + ironShop.price).toLowerCase(Locale.ROOT);
        assertEquals(TradeCopy.NEUTRAL, ironShop.priceColor);
        assertFalse(ironShop.comparedToGe);
        assertFalse(shopText.contains("ge"));
        assertFalse(shopText.contains("cheaper"));
        assertFalse(shopText.contains("buy"));
        assertTrue(ironShop.price.contains("10"));
        assertTrue(ironShop.stock.contains("5"));

        TradeCopy.ShopLine standard = TradeCopy.shop(false, shop, 1_000);
        assertEquals(TradeCopy.CHEAPER, standard.priceColor);
        assertTrue(standard.comparedToGe);
        TradeCopy.ShopLine dear = TradeCopy.shop(false, shop, 4);
        assertEquals(TradeCopy.DEARER, dear.priceColor);

        ShopEntry tokkul = new ShopEntry("TzHaar", 67, 5, "Tokkul", true);
        TradeCopy.ShopLine currency = TradeCopy.shop(true, tokkul, 1);
        assertTrue(currency.price.contains("Tokkul"));
        assertFalse(currency.price.contains("buy"));

        Alchemy.Result profit = Alchemy.compute(1_000, 100, 200);
        String ironAlch = TradeCopy.alchLine(true, profit);
        assertEquals("High Alch: 1,000 gp", ironAlch);
        assertFalse(ironAlch.contains("+"));
        assertFalse(ironAlch.toLowerCase(Locale.ROOT).contains("ge"));
        assertFalse(ironAlch.toLowerCase(Locale.ROOT).contains("buy"));
        assertTrue(TradeCopy.alchLine(false, profit).contains("+700"));

        PriceQuote quote = new PriceQuote(1_200, 800, 1_000);
        String reference = TradeCopy.geLine(true, true, quote);
        assertEquals("GE reference: 1,200 gp", reference);
        assertFalse(reference.toLowerCase(Locale.ROOT).contains("buy"));
        assertEquals("GE buy: 1,200 gp", TradeCopy.geLine(false, true, quote));
        assertEquals("", TradeCopy.geLine(true, false, quote));
        assertEquals("", TradeCopy.geLine(false, false, quote));
        assertEquals("", TradeCopy.geLine(true, true, PriceQuote.MISSING));
        assertFalse(TradeCopy.shouldFetchGePrices(false));
        assertTrue(TradeCopy.shouldFetchGePrices(true));
    }
}
