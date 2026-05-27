package com.osr.jei.model;

/**
 * One shop that sells the current item, parsed from a {@code {{StoreLine}}}
 * template on the OSRS Wiki.
 */
public class ShopEntry {

    /** Display name of the shop (e.g. "Bob's Brilliant Axes"). */
    public final String shopName;

    /**
     * Price the player pays to buy one unit from the shop, in coins.
     * {@code 0} means the price was not listed or the item uses a non-coin currency.
     */
    public final int price;

    /**
     * Base stock quantity held by the shop.
     * {@code 0} means the stock was not listed or infinite.
     */
    public final int stock;

    public ShopEntry(String shopName, int price, int stock) {
        this.shopName = shopName;
        this.price    = price;
        this.stock    = stock;
    }
}
