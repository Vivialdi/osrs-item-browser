package com.osr.jei.ui;

import com.osr.jei.ironman.TradeCopy;
import com.osr.jei.model.DropEntry;
import com.osr.jei.model.RecipeInfo;
import com.osr.jei.model.ShopEntry;
import com.osr.jei.wiki.LocationNames;

import java.util.List;
import java.util.Locale;

/**
 * One-line headers for detail sections. Shop and crafting headers keep a
 * short summary. The drop header is only the count. A location header is
 * {@code Location: name} for one place and {@code Locations (N)} for more,
 * with no place name in the count.
 */
public final class SectionSummaries {

    private SectionSummaries() {
    }

    public static String shops(List<ShopEntry> shops) {
        int count = shops == null ? 0 : shops.size();
        if (count == 0) {
            return "Shop sources";
        }
        String title = "Shop sources (" + count + ")";
        ShopEntry cheapest = cheapest(shops);
        if (cheapest == null) {
            return title;
        }
        String price = formatPrice(cheapest);
        if (count == 1) {
            return title + " \u00b7 " + price;
        }
        return title + " \u00b7 cheapest " + price;
    }

    public static String drops(List<DropEntry> drops) {
        int count = drops == null ? 0 : drops.size();
        return "Dropped by (" + count + ")";
    }

    public static String uses(List<String> names) {
        int count = names == null ? 0 : names.size();
        if (count == 0) {
            return "Used to craft";
        }
        if (count == 1 && names.get(0) != null && !names.get(0).isEmpty()) {
            return "Used to craft (1) \u00b7 " + names.get(0);
        }
        return "Used to craft (" + count + ")";
    }

    public static String crafting(List<RecipeInfo> recipes) {
        int count = recipes == null ? 0 : recipes.size();
        if (count == 0) {
            return "Crafting";
        }
        String title = "Crafting (" + count + ")";
        String skill = skillLevel(recipes.get(0));
        if (skill.isEmpty()) {
            return title;
        }
        return title + " \u00b7 " + skill;
    }

    public static String equipment(String slot, boolean twoHanded) {
        if (slot == null || slot.trim().isEmpty()) {
            return "Equipment";
        }
        String pretty = slot.trim();
        String lower = pretty.toLowerCase(Locale.ROOT);
        if (twoHanded && !lower.contains("2h") && !lower.contains("two")) {
            pretty = pretty + ", two-handed";
        }
        return "Equipment \u00b7 " + pretty;
    }

    public static String locations(List<String> places) {
        List<String> shown = LocationNames.distinct(places);
        if (shown.isEmpty()) {
            return "Locations";
        }
        if (shown.size() == 1) {
            return "Location: " + shown.get(0);
        }
        return "Locations (" + shown.size() + ")";
    }

    public static String shopPlain(ShopEntry shop, TradeCopy.ShopLine line) {
        String name = shop == null || shop.shopName == null ? "" : shop.shopName;
        String stock = line == null || line.stock == null ? "" : line.stock;
        String price = line == null || line.price == null ? "" : line.price;
        return name + stock + price;
    }

    public static String shopHtml(ShopEntry shop, TradeCopy.ShopLine line) {
        String name = shop == null || shop.shopName == null ? "" : shop.shopName;
        String stock = line == null || line.stock == null ? "" : line.stock;
        String price = line == null || line.price == null ? "" : line.price;
        String color = line == null || line.priceColor == null ? TradeCopy.NEUTRAL : line.priceColor;
        return "<font color='#bebebe'>\u00b7 " + escape(name) + escape(stock) + "</font>"
            + "<font color='" + color + "'>" + keepPriceTogether(escape(price)) + "</font>";
    }

    static ShopEntry cheapest(List<ShopEntry> shops) {
        if (shops == null) {
            return null;
        }
        ShopEntry coins = null;
        for (ShopEntry shop : shops) {
            if (!priced(shop) || !shop.isCoins()) {
                continue;
            }
            if (coins == null || shop.price < coins.price) {
                coins = shop;
            }
        }
        if (coins != null) {
            return coins;
        }
        String currency = null;
        ShopEntry best = null;
        for (ShopEntry shop : shops) {
            if (!priced(shop) || shop.currency.isEmpty()) {
                continue;
            }
            if (currency == null) {
                currency = shop.currency;
            }
            if (!currency.equals(shop.currency)) {
                continue;
            }
            if (best == null || shop.price < best.price) {
                best = shop;
            }
        }
        return best;
    }

    private static boolean priced(ShopEntry shop) {
        return shop != null && shop.sells && shop.price >= 0;
    }

    static String formatPrice(ShopEntry shop) {
        String unit = shop.isCoins() ? "gp" : shop.currency;
        return String.format(Locale.ROOT, "%,d\u00a0%s", shop.price, unit);
    }

    private static String skillLevel(RecipeInfo recipe) {
        if (recipe == null || recipe.skill == null || recipe.skill.isEmpty()) {
            return "";
        }
        if (recipe.level > 0) {
            return recipe.skill + " (Lv. " + recipe.level + ")";
        }
        return recipe.skill;
    }

    /** Keep "4 gp" on one line while still allowing the price to wrap after the shop name. */
    private static String keepPriceTogether(String price) {
        return price.replaceAll("(\\d) ", "$1\u00a0");
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
