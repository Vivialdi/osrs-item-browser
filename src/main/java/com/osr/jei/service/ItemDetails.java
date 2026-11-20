package com.osr.jei.service;

import com.osr.jei.model.DropEntry;
import com.osr.jei.model.EquipmentStats;
import com.osr.jei.model.RecipeInfo;
import com.osr.jei.model.ShopEntry;
import com.osr.jei.wiki.DropTableParser;
import com.osr.jei.wiki.EquipmentParser;
import com.osr.jei.wiki.HtmlSections;
import com.osr.jei.wiki.PageContent;
import com.osr.jei.wiki.RecipeParser;
import com.osr.jei.wiki.ShopTableParser;

import java.util.Collections;
import java.util.List;
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

    public ItemDetails(
        List<RecipeInfo> recipes,
        Optional<EquipmentStats> stats,
        double weight,
        List<ShopEntry> shops,
        List<DropEntry> drops,
        List<String> products
    ) {
        this.recipes = recipes;
        this.stats = stats;
        this.weight = weight;
        this.shops = shops;
        this.drops = drops;
        this.products = products;
    }

    public static ItemDetails from(PageContent page) {
        if (page == null || page.missing) {
            return EMPTY;
        }
        String shopsHtml = firstSection(page.html, "Shop_locations", "Shops", "Sold_by", "Store_locations");
        String sourcesHtml = firstSection(page.html, "Item_sources", "Sources", "Drop_sources", "Drops");
        String productsHtml = firstSection(page.html, "Products", "Uses", "Items_created", "Created_from");
        return new ItemDetails(
            RecipeParser.parseAll(page.wikitext),
            EquipmentParser.parseStats(page.wikitext),
            EquipmentParser.parseWeight(page.wikitext),
            ShopTableParser.parse(shopsHtml),
            DropTableParser.parse(sourcesHtml),
            UsedInService.parseProductsTable(productsHtml)
        );
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
