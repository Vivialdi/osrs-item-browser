package com.osr.jei.wiki;

import com.osr.jei.model.DropEntry;
import com.osr.jei.model.EquipmentStats;
import com.osr.jei.model.RecipeInfo;
import com.osr.jei.model.ShopEntry;
import com.osr.jei.service.ItemDetails;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ShopAndPageParseTest {

    private static final String SHOPS = ""
        + "<div class=\"mw-heading mw-heading3\"><h3 id=\"Shop_locations\">Shop locations</h3></div>"
        + "<table class=\"wikitable store-locations-list\"><tr>"
        + "<th>Seller</th><th>Location</th>"
        + "<th>Number<br />in stock</th><th>Restock<br />time</th>"
        + "<th>Price<br />sold at</th><th>Price<br />bought at</th>"
        + "</tr>"
        + "<tr><td><a href=\"/w/Shantay_Pass_Shop\">Shantay Pass Shop.</a></td><td>Desert</td>"
        + "<td data-sort-value=\"0\">0</td><td data-sort-value=\"60\">1m</td>"
        + "<td data-sort-value=\"2\">2</td><td>1</td></tr>"
        + "<tr><td>Fishing Guild Shop</td><td>Guild</td>"
        + "<td data-sort-value=\"10\">10</td><td>26m</td>"
        + "<td data-sort-value=\"70\">70</td><td>20</td></tr>"
        + "<tr><td>Theatre of Blood Supplies</td><td>ToB</td>"
        + "<td data-sort-value=\"10e50\"><span>\u221e</span></td><td>N/A</td>"
        + "<td data-sort-value=\"1\">1</td><td>1</td></tr>"
        + "<tr><td>TzHaar shop</td><td>Mor Ul Rek</td>"
        + "<td data-sort-value=\"5\">5</td><td>1m</td>"
        + "<td data-sort-value=\"67\">67 Tokkul</td><td>20</td></tr>"
        + "</table>";

    private static final String PAGE = ""
        + "<h2 id=\"Products\">Products</h2>"
        + "<table class=\"wikitable\"><tr><td><a href=\"/w/Iron_platebody\">Iron platebody</a></td></tr></table>"
        + "<div class=\"mw-heading mw-heading2\"><h2 id=\"Item_sources\">Item sources</h2></div>"
        + "<table class=\"item-drops\"><tr><td>Goblin</td><td>2</td><td>1</td><td>1/128</td></tr></table>"
        + SHOPS
        + "<h2 id=\"Changes\">Changes</h2><p>later</p>";

    private static final String WIKI = ""
        + "{{Infobox Item\n|name = Bucket\n|weight = 1.5\n}}\n"
        + "{{Recipe\n|skill1 = Smithing\n|skill1lvl = 33\n|skill1exp = 37.5\n"
        + "|facilities = Anvil\n|mat1 = Iron bar\n|mat1quantity = 5\n}}\n"
        + "{{Recipe\n|skill1 = Cooking\n|skill1lvl = 1\n|skill1exp = 0\n"
        + "|mat1 = Bucket of water\n|mat1quantity = 1\n}}\n"
        + "{{Infobox Bonuses\n|astab = 0\n|dstab = 3\n|slot = body\n|aspeed = 4\n}}\n";

    @Test
    public void stockColumnIsNotRestockAndZeroStaysZero() {
        List<ShopEntry> shops = ShopTableParser.parse(SHOPS);
        assertEquals(4, shops.size());

        ShopEntry shantay = shops.get(0);
        assertEquals("Shantay Pass Shop", shantay.shopName);
        assertEquals(0, shantay.stock);
        assertTrue(shantay.permanentlyOutOfStock());
        assertEquals(2, shantay.price);
        assertEquals("gp", shantay.currency);
        assertTrue(shantay.sells);

        ShopEntry guild = shops.get(1);
        assertEquals(10, guild.stock);
        assertFalse(guild.permanentlyOutOfStock());
        assertEquals(70, guild.price);

        ShopEntry tob = shops.get(2);
        assertEquals(ShopEntry.INFINITE, tob.stock);
        assertTrue(tob.infiniteStock());

        ShopEntry tzhaar = shops.get(3);
        assertEquals("Tokkul", tzhaar.currency);
        assertFalse(tzhaar.isCoins());
        assertEquals(67, tzhaar.price);
        assertEquals(5, tzhaar.stock);
    }

    @Test
    public void onePageFeedsRecipesDropsAndShops() {
        ItemDetails details = ItemDetails.from(new PageContent(WIKI, PAGE, false));
        assertEquals(2, details.recipes.size());
        RecipeInfo smithing = details.recipes.get(0);
        assertEquals("Smithing", smithing.skill);
        assertEquals(33, smithing.level);
        assertEquals(1, smithing.ingredients.size());
        assertEquals("Iron bar", smithing.ingredients.get(0).name);
        assertEquals(5, smithing.ingredients.get(0).quantity);
        assertEquals("Cooking", details.recipes.get(1).skill);

        assertEquals(1, details.drops.size());
        DropEntry drop = details.drops.get(0);
        assertEquals("Goblin", drop.getSource());
        assertEquals("2", drop.getLevel());
        assertEquals("1", drop.getQuantity());
        assertEquals("1/128", drop.getRate());

        assertEquals(4, details.shops.size());
        assertTrue(details.stats.isPresent());
        EquipmentStats stats = details.stats.get();
        assertEquals("body", stats.slot);
        assertEquals(3, stats.defStab);
        assertEquals(4, stats.speed);
        assertEquals(1.5, details.weight, 0.001);
        assertEquals(1, details.products.size());
        assertEquals("Iron platebody", details.products.get(0));
    }

    @Test
    public void sectionSliceStopsAtTheNextHeading() {
        String products = HtmlSections.sliceById(PAGE, "Products");
        assertTrue(products.contains("Iron platebody"));
        assertFalse(products.contains("Goblin"));
        String sources = HtmlSections.sliceById(PAGE, "Item_sources");
        assertTrue(sources.contains("Goblin"));
        assertTrue(sources.contains("Shantay Pass Shop"));
        assertFalse(sources.contains("later"));
    }
}
