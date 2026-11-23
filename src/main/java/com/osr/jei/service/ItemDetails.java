package com.osr.jei.service;

import com.osr.jei.model.DropEntry;
import com.osr.jei.model.EquipmentStats;
import com.osr.jei.model.RecipeInfo;
import com.osr.jei.model.ShopEntry;
import com.osr.jei.requirements.ItemRequirements;
import com.osr.jei.requirements.RequirementParser;
import com.osr.jei.wiki.DropTableParser;
import com.osr.jei.wiki.EquipmentParser;
import com.osr.jei.wiki.HtmlSections;
import com.osr.jei.wiki.PageContent;
import com.osr.jei.wiki.RecipeParser;
import com.osr.jei.wiki.ShopTableParser;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Everything parsed from a single item-page response. */
public final class ItemDetails {

    public static final ItemDetails EMPTY = new ItemDetails(
        Collections.emptyList(),
        Optional.empty(),
        Double.NaN,
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList()
    );

    public final List<RecipeInfo> recipes;
    public final Optional<EquipmentStats> stats;
    public final double weight;
    public final List<ShopEntry> shops;
    public final List<DropEntry> drops;
    public final List<String> products;
    public final ItemRequirements requirements;
    /** True when the wiki confirmed it has no page for this name. */
    public final boolean missing;

    public static final ItemDetails MISSING = new ItemDetails(
        Collections.emptyList(),
        Optional.empty(),
        Double.NaN,
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        ItemRequirements.NONE,
        true
    );

    public ItemDetails(
        List<RecipeInfo> recipes,
        Optional<EquipmentStats> stats,
        double weight,
        List<ShopEntry> shops,
        List<DropEntry> drops,
        List<String> products
    ) {
        this(recipes, stats, weight, shops, drops, products, ItemRequirements.NONE);
    }

    public ItemDetails(
        List<RecipeInfo> recipes,
        Optional<EquipmentStats> stats,
        double weight,
        List<ShopEntry> shops,
        List<DropEntry> drops,
        List<String> products,
        ItemRequirements requirements
    ) {
        this(recipes, stats, weight, shops, drops, products, requirements, false);
    }

    public ItemDetails(
        List<RecipeInfo> recipes,
        Optional<EquipmentStats> stats,
        double weight,
        List<ShopEntry> shops,
        List<DropEntry> drops,
        List<String> products,
        ItemRequirements requirements,
        boolean missing
    ) {
        this.recipes = recipes;
        this.stats = stats;
        this.weight = weight;
        this.shops = shops;
        this.drops = drops;
        this.products = products;
        this.requirements = requirements == null ? ItemRequirements.NONE : requirements;
        this.missing = missing;
    }

    public static ItemDetails from(PageContent page) {
        if (page == null || page.missing) {
            return MISSING;
        }
        String shopsHtml = shopSection(page.html);
        String sourcesHtml = firstSection(page.html, "Item_sources", "Sources", "Drop_sources", "Drops");
        String productsHtml = firstSection(page.html, "Products", "Uses", "Items_created", "Created_from");
        return new ItemDetails(
            RecipeParser.parseAll(page.wikitext),
            EquipmentParser.parseStats(page.wikitext),
            EquipmentParser.parseWeight(page.wikitext),
            ShopTableParser.parse(shopsHtml),
            DropTableParser.parse(sourcesHtml),
            UsedInService.parseProductsTable(productsHtml),
            RequirementParser.parse(page.wikitext)
        );
    }

    /**
     * Shop rows live in a {@code store-locations-list} table. Prefer the shop
     * heading, but if that slice has no such table, scan the rest of the page.
     * Some pages render the table under a heading id this plugin does not know.
     */
    private static String shopSection(String html) {
        String slice = firstSection(html, "Shop_locations", "Shops", "Sold_by", "Store_locations");
        if (hasStoreTable(slice)) {
            return slice;
        }
        if (hasStoreTable(html)) {
            return html;
        }
        return slice;
    }

    private static boolean hasStoreTable(String html) {
        return html != null && html.toLowerCase(Locale.ROOT).contains("store-locations-list");
    }

    private static String firstSection(String html, String... ids) {
        for (String id : ids) {
            String slice = HtmlSections.sliceById(html, id);
            if (!slice.isEmpty()) {
                return slice;
            }
        }
        return "";
    }
}
