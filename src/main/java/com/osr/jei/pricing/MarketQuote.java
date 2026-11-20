package com.osr.jei.pricing;

/**
 * Second GE line: instant sell, post-tax margin, buy limit, and profit
 * across one 4-hour limit. Ironman copy is explicitly a reference.
 */
public final class MarketQuote {

    public final String text;
    /** {@code 1} profit, {@code -1} loss, {@code 0} when margin is unknown. */
    public final int marginSign;

    private MarketQuote(String text, int marginSign) {
        this.text = text;
        this.marginSign = marginSign;
    }

    public static MarketQuote describe(
        boolean ironman,
        int itemId,
        PriceQuote quote,
        Integer buyLimit,
        boolean limitsReady
    ) {
        boolean exempt = GeTax.exempt(itemId);
        boolean priced = quote != null && quote.instantBuy > 0L && quote.instantSell > 0L;
        Long margin = priced ? GeTax.margin(quote.instantBuy, quote.instantSell, exempt) : null;

        StringBuilder body = new StringBuilder();
        if (priced && margin != null) {
            body.append(String.format("Instant sell: %,d gp", quote.instantSell));
            body.append(" \u00b7 ");
            if (exempt) {
                body.append(String.format("margin %,d gp, tax exempt", margin));
            } else {
                body.append(String.format("margin %,d gp after tax", margin));
            }
        }
        appendLimit(body, buyLimit, limitsReady, margin);
        if (body.length() == 0) {
            return new MarketQuote("", 0);
        }
        int sign = margin == null ? 0 : Long.signum(margin);
        String text = body.toString();
        if (ironman) {
            text = "Reference only \u00b7 " + text;
        }
        return new MarketQuote(text, sign);
    }

    private static void appendLimit(StringBuilder body, Integer buyLimit, boolean limitsReady, Long margin) {
        String limitText;
        if (!limitsReady) {
            limitText = "buy limit loading";
        } else if (buyLimit == null) {
            limitText = "buy limit not listed";
        } else {
            limitText = String.format("limit %,d", buyLimit);
        }
        if (body.length() > 0) {
            body.append(" \u00b7 ");
        }
        body.append(limitText);
        Long perLimit = GeTax.perLimit(margin, buyLimit);
        if (perLimit != null) {
            body.append(String.format(" \u00b7 per 4h %,d gp", perLimit));
        }
    }
}
