package com.osr.jei.ui;

import com.osr.jei.model.DropEntry;
import com.osr.jei.model.RecipeInfo;
import com.osr.jei.model.ShopEntry;
import com.osr.jei.pricing.MarketQuote;
import com.osr.jei.pricing.PriceQuote;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SectionSummaryTest {

    @Test
    public void shopHeaderNamesTheCheapestCoinPrice() {
        ShopEntry guild = shop("Magic Guild Store (Runes and Staves)", 17, "gp");
        ShopEntry tokkul = shop("TzHaar-Mej-Roh's Rune Store", 20, "Tokkul");
        ShopEntry aubury = shop("Aubury's Rune Shop", 4, "gp");
        List<ShopEntry> shops = Arrays.asList(guild, tokkul, aubury);
        assertEquals("Shop sources (3) \u00b7 cheapest 4\u00a0gp", SectionSummaries.shops(shops));
        assertEquals("Shop sources (1) \u00b7 4\u00a0gp", SectionSummaries.shops(Collections.singletonList(aubury)));
        assertEquals(
            "Shop sources (2) \u00b7 cheapest 5\u00a0Tokkul",
            SectionSummaries.shops(Arrays.asList(shop("TzHaar", 15, "Tokkul"), shop("Other", 5, "Tokkul"))));
    }

    @Test
    public void dropHeaderIsOnlyTheCount() {
        DropEntry rare = drop("Abyssal demon", "1/1000");
        DropEntry common = drop("Goblin", "1/16");
        assertEquals("Dropped by (2)", SectionSummaries.drops(Arrays.asList(rare, common)));
        assertEquals("Dropped by (1)", SectionSummaries.drops(Collections.singletonList(common)));
        assertEquals("Dropped by (0)", SectionSummaries.drops(Collections.emptyList()));
    }

    @Test
    public void useCraftingEquipmentAndLocationHeadersStayShort() {
        assertEquals("Used to craft (1) \u00b7 Staff of air", SectionSummaries.uses(Collections.singletonList("Staff of air")));
        assertEquals("Used to craft (27)", SectionSummaries.uses(Collections.nCopies(27, "Product")));
        RecipeInfo runecraft = new RecipeInfo("Runecraft", 1, "Air tiara, Air talisman", 5, Collections.emptyList());
        assertEquals("Crafting (2) \u00b7 Runecraft (Lv. 1)", SectionSummaries.crafting(Arrays.asList(runecraft, runecraft)));
        assertEquals("Equipment \u00b7 2h", SectionSummaries.equipment("2h", true));
        assertEquals("Equipment \u00b7 Weapon, two-handed", SectionSummaries.equipment("Weapon", true));
        assertEquals(
            "Locations (2) \u00b7 Lumbridge Swamp",
            SectionSummaries.locations(Arrays.asList("Lumbridge Swamp", "Varrock Sewers")));
    }

    @Test
    public void sectionCollapseIsRememberedPerKey() {
        SectionState state = new SectionState();
        assertFalse(state.expanded(SectionState.SHOPS));
        assertFalse(state.expanded(SectionState.DROPS));
        assertFalse(state.expanded(SectionState.CRAFTING));
        assertFalse(state.expanded(SectionState.LOCATIONS));
        assertFalse(state.expanded(SectionState.TRADING));
        assertTrue(state.toggle(SectionState.SHOPS));
        assertTrue(state.expanded(SectionState.SHOPS));
        assertFalse(state.expanded(SectionState.DROPS));
        assertFalse(state.toggle(SectionState.SHOPS));
        state.setExpanded(SectionState.TRADING, true);
        state.setExpanded(SectionState.DROPS, true);
        state.setExpanded(SectionState.CRAFTING, true);
        state.reset();
        assertFalse(state.expanded(SectionState.SHOPS));
        assertFalse(state.expanded(SectionState.TRADING));
        assertFalse(state.expanded(SectionState.DROPS));
        assertFalse(state.expanded(SectionState.CRAFTING));
        assertFalse(state.expanded(SectionState.LOCATIONS));
        assertFalse(state.expanded(SectionState.USED_IN));
    }

    @Test
    public void marketRowsDoNotRepeatTheLongSentence() {
        PriceQuote quote = new PriceQuote(6, 5, 5);
        MarketQuote market = MarketQuote.describe(false, 556, quote, 12_000, true);
        assertTrue(market.text.contains("\u00b7"));
        assertTrue(market.lines.size() >= 3);
        StringBuilder joined = new StringBuilder();
        for (MarketQuote.Line line : market.lines) {
            assertFalse(line.key.contains("\u00b7"));
            assertFalse(line.value.contains("\u00b7"));
            assertTrue(line.value.length() < market.text.length());
            joined.append(line.key).append(line.value);
        }
        assertFalse(joined.toString().contains("\u00b7"));
        assertEquals("Instant sell", market.lines.get(0).key);
        assertEquals("5 gp", market.lines.get(0).value);
        assertEquals("Margin", market.lines.get(1).key);
        assertTrue(market.lines.get(1).value.contains("-1 gp"));
    }

    private static ShopEntry shop(String name, int price, String currency) {
        return new ShopEntry(name, price, 5_000, currency, true);
    }

    private static DropEntry drop(String source, String rate) {
        DropEntry entry = new DropEntry();
        entry.setSource(source);
        entry.setRate(rate);
        entry.setQuantity("1");
        entry.setLevel("1");
        return entry;
    }
}
