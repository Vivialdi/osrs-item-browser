package com.osr.jei.ironman;

import com.osr.jei.model.ShopEntry;
import com.osr.jei.pricing.Alchemy;
import com.osr.jei.pricing.PriceQuote;

/**
 * Price and shop lines. Ironman copy keeps high alch and store prices,
 * and does not frame anything as a Grand Exchange purchase.
 */
public final class TradeCopy {

    public static final String NEUTRAL = "#bebebe";
    public static final String CHEAPER = "#64d264";
    public static final String DEARER = "#dc6464";

    public static final class ShopLine {
        public final String stock;
        public final String price;
        public final String priceColor;
        /** True only when the price colour is a comparison against the GE buy price. */
        public final boolean comparedToGe;

        public ShopLine(String stock, String price, String priceColor, boolean comparedToGe) {
            this.stock = stock;
            this.price = price;
            this.priceColor = priceColor;
            this.comparedToGe = comparedToGe;
        }
    }

    private TradeCopy() {
    }

    /** Prices are requested only when the GE setting is on, ironman or not. */
    public static boolean shouldFetchGePrices(boolean showPrices) {
        return showPrices;
    }

    public static String geLine(boolean ironman, boolean showPrices, PriceQuote quote) {
        if (!showPrices) {
            return "";
        }
        if (quote == null || !quote.known()) {
            return ironman ? "" : "Not on GE / no price data";
        }
        long value = quote.instantBuy > 0 ? quote.instantBuy : quote.mid;
        if (ironman) {
            return String.format("GE reference: %,d gp", value);
        }
        if (quote.instantBuy > 0) {
            return String.format("GE buy: %,d gp", quote.instantBuy);
        }
        return String.format("GE price: %,d gp", quote.mid);
    }

    /**
     * High-alch value. Profit versus a GE buy is only shown for non-iron accounts
     * when both the GE price and the nature-rune price are known.
     */
    public static String alchLine(boolean ironman, Alchemy.Result result) {
        if (result == null) {
            return "";
        }
        if (ironman || result.profit == null) {
            return String.format("High Alch: %,d gp", result.haValue);
        }
        String profit = result.profit >= 0
            ? String.format("+%,d gp", result.profit)
            : String.format("%,d gp", result.profit);
        return String.format("High Alch: %,d gp  (%s)", result.haValue, profit);
    }

    public static ShopLine shop(boolean ironman, ShopEntry shop, long geBuy) {
        String stock = "";
        if (shop.infiniteStock()) {
            stock = " (\u221e)";
        } else if (shop.stock > 0) {
            stock = " (" + String.format("%,d", shop.stock) + ")";
        }
        String unit = shop.isCoins() ? "gp" : shop.currency;
        String price = " \u2014 " + String.format("%,d %s", shop.price, unit);
        if (ironman || !shop.isCoins() || geBuy <= 0 || shop.price <= 0) {
            return new ShopLine(stock, price, NEUTRAL, false);
        }
        String color = shop.price <= geBuy ? CHEAPER : DEARER;
        return new ShopLine(stock, price, color, true);
    }
}
