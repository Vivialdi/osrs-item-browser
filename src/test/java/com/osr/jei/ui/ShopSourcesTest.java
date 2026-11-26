package com.osr.jei.ui;

import com.osr.jei.model.ShopEntry;
import com.osr.jei.wiki.ShopTableParser;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ShopSourcesTest {

    private static final String AUBURY = "Aubury's Rune Shop";

    @Test
    public void zeroStockIsHiddenForThatItemAndTheSameShopStaysForAnother() {
        ShopEntry auburyEmpty = new ShopEntry(AUBURY, 2, 0, "gp", true);
        ShopEntry betty = new ShopEntry("Betty's Magic Emporium", 10, 5, "gp", true);
        ShopEntry unlimited = new ShopEntry("Theatre of Blood Supplies", 1, ShopEntry.INFINITE, "gp", true);

        List<ShopEntry> feathers = ShopSources.visible(Arrays.asList(auburyEmpty, betty, unlimited));
        assertEquals(Arrays.asList("Betty's Magic Emporium", "Theatre of Blood Supplies"), names(feathers));
        assertEquals("Shop sources (2) \u00b7 cheapest 1\u00a0gp", SectionSummaries.shops(feathers));
        assertTrue(feathers.get(1).infiniteStock());

        ShopEntry auburyStocked = new ShopEntry(AUBURY, 4, 30, "gp", true);
        List<ShopEntry> runes = ShopSources.visible(Collections.singletonList(auburyStocked));
        assertEquals(Collections.singletonList(AUBURY), names(runes));
        assertEquals(30, runes.get(0).stock);
        assertEquals("Shop sources (1) \u00b7 4\u00a0gp", SectionSummaries.shops(runes));
    }

    @Test
    public void parsedZeroRowIsDroppedAndALaterStockOfTheSameShopIsKept() {
        List<ShopEntry> empty = ShopSources.visible(ShopTableParser.parse(row(AUBURY, "0", "0", "4")));
        List<ShopEntry> stocked = ShopSources.visible(ShopTableParser.parse(row(AUBURY, "12", "12", "4")));
        List<ShopEntry> infinite = ShopSources.visible(ShopTableParser.parse(row(AUBURY, "10e50", "\u221e", "1")));

        assertTrue(empty.isEmpty());
        assertEquals("Shop sources", SectionSummaries.shops(empty));
        assertEquals(Collections.singletonList(AUBURY), names(stocked));
        assertEquals(12, stocked.get(0).stock);
        assertEquals(1, infinite.size());
        assertTrue(infinite.get(0).infiniteStock());
        assertEquals(AUBURY, infinite.get(0).shopName);
    }

    private static List<String> names(List<ShopEntry> shops) {
        List<String> names = new java.util.ArrayList<>();
        for (ShopEntry shop : shops) {
            names.add(shop.shopName);
        }
        return names;
    }

    private static String row(String shop, String sort, String visible, String price) {
        return ""
            + "<table class=\"wikitable store-locations-list\"><tr>"
            + "<th>Seller</th><th>Number<br />in stock</th><th>Price<br />sold at</th>"
            + "</tr><tr>"
            + "<td><a href=\"/w/Shop\">" + shop + "</a></td>"
            + "<td data-sort-value=\"" + sort + "\">" + visible + "</td>"
            + "<td data-sort-value=\"" + price + "\">" + price + "</td>"
            + "</tr></table>";
    }
}
