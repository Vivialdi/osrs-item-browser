package com.osr.jei.model;

/**
 * One row from an item's "Shop locations" table.
 *
 * <p>{@link #stock} is the wiki's default stock, not the restock timer.
 * {@link #UNKNOWN} means the cell was missing. {@link #INFINITE} means the
 * shop stocks the item without a finite cap ({@code ∞} / {@code 10e50}).
 * A real {@code 0} means the shop does not generate the item.
 */
public class ShopEntry {

    public static final int UNKNOWN = -1;
    public static final int INFINITE = Integer.MAX_VALUE;

    /** Display name of the shop (for example "Bob's Brilliant Axes"). */
    public final String shopName;

    /**
     * Price the player pays to buy one unit, in {@link #currency}.
     * {@code 0} is a real free price when {@link #sells} is true.
     */
    public final int price;

    /** Default stock. See the class constants. */
    public final int stock;

    /** {@code gp} for coins, otherwise the wiki currency name such as {@code Tokkul}. */
    public final String currency;

    /** False when the "Price sold at" cell is empty or "not sold" (buy-only rows). */
    public final boolean sells;

    public ShopEntry(String shopName, int price, int stock, String currency, boolean sells) {
        this.shopName = shopName;
        this.price = price;
        this.stock = stock;
        this.currency = currency == null ? "" : currency;
        this.sells = sells;
    }

    /** The shop's default stock is zero, so it never generates this item. */
    public boolean permanentlyOutOfStock() {
        return stock == 0;
    }

    public boolean infiniteStock() {
        return stock == INFINITE;
    }

    public boolean isCoins() {
        return "gp".equals(currency);
    }
}
