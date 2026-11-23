package com.osr.jei.wiki;

import com.osr.jei.model.ShopEntry;
import com.osr.jei.service.ItemDetails;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Live wiki {@code store-locations-list} tables. Air battlestaff's own page does
 * not include this table (and the storeline bucket has no row for it), so an
 * empty shop list there is the page, not a missed column. Staff of air, bread,
 * bronze axe, and air rune do include it, including version notes in the seller cell.
 */
public class StoreLocationsListTest {

    @Test
    public void staffOfAirIncludesZaffAndVersionNotes() throws Exception {
        List<ShopEntry> shops = ShopTableParser.parse(fixture("wiki/staff-of-air-shops.html"));
        ShopEntry zaff = named(shops, "Zaff's Superior Staffs!");
        assertEquals(1500, zaff.price);
        assertEquals(2, zaff.stock);
        assertEquals("gp", zaff.currency);
        assertTrue(zaff.sells);
        assertTrue(names(shops).contains("Magic Guild Store (Runes and Staves)"));
        assertTrue(names(shops).contains("Baba Yaga's Magic Shop (post-quest)"));
        assertTrue(names(shops).contains("Baba Yaga's Magic Shop"));
    }

    @Test
    public void breadBronzeAxeAndAirRuneUseTheSameTable() throws Exception {
        List<ShopEntry> bread = ShopTableParser.parse(fixture("wiki/bread-shops.html"));
        ShopEntry stall = named(bread, "Ardougne Baker's Stall (east)");
        assertEquals(12, stall.price);
        assertEquals(10, stall.stock);

        List<ShopEntry> axes = ShopTableParser.parse(fixture("wiki/bronze-axe-shops.html"));
        ShopEntry bob = named(axes, "Bob's Brilliant Axes");
        assertEquals(16, bob.price);
        assertEquals(10, bob.stock);
        assertTrue(names(axes).contains("Obli's General Store (Karamja gloves)"));

        List<ShopEntry> runes = ShopTableParser.parse(fixture("wiki/air-rune-shops.html"));
        ShopEntry aubury = named(runes, "Aubury's Rune Shop");
        assertEquals(4, aubury.price);
        assertEquals(5000, aubury.stock);
        assertTrue(names(runes).contains("Baba Yaga's Magic Shop (post-quest)"));
    }

    @Test
    public void tableIsFoundWithoutTheExpectedHeading() throws Exception {
        String table = fixture("wiki/staff-of-air-shops.html");
        String page = "<h2 id=\"Trivia\">Trivia</h2><p>No shop heading.</p>" + table;
        ItemDetails details = ItemDetails.from(new PageContent("", page, false));
        assertTrue(names(details.shops).contains("Zaff's Superior Staffs!"));
    }

    @Test
    public void pageWithoutAStoreTableHasNoShops() {
        ItemDetails details = ItemDetails.from(new PageContent("", "<p>Air battlestaff</p>", false));
        assertTrue(details.shops.isEmpty());
    }

    private static ShopEntry named(List<ShopEntry> shops, String name) {
        for (ShopEntry shop : shops) {
            if (name.equals(shop.shopName)) {
                return shop;
            }
        }
        throw new AssertionError(name + " missing from " + names(shops));
    }

    private static String names(List<ShopEntry> shops) {
        StringBuilder text = new StringBuilder();
        for (ShopEntry shop : shops) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(shop.shopName);
        }
        return text.toString();
    }

    private static String fixture(String path) throws Exception {
        InputStream in = StoreLocationsListTest.class.getClassLoader().getResourceAsStream(path);
        if (in == null) {
            throw new AssertionError("Missing fixture " + path);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        in.close();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
