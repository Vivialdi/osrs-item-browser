package com.osr.jei.pricing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Grand Exchange sell, post-tax margin, buy limit, and profit across one
 * 4-hour limit. {@link #text} is the single sentence kept for callers that
 * still want it. {@link #lines} is the same information split into short
 * rows so a narrow sidebar can show each figure on its own line.
 * Ironman copy is explicitly a reference.
 */
public final class MarketQuote {

    /** One short row. An empty key is a full-width note, such as "Reference only". */
    public static final class Line {
        public final String key;
        public final String value;
        /** {@code 1} profit, {@code -1} loss, {@code 0} neutral. */
        public final int tone;

        public Line(String key, String value, int tone) {
            this.key = key == null ? "" : key;
            this.value = value == null ? "" : value;
            this.tone = tone;
        }
    }

    public final String text;
    /** {@code 1} profit, {@code -1} loss, {@code 0} when margin is unknown. */
    public final int marginSign;
    public final List<Line> lines;

    private MarketQuote(String text, int marginSign, List<Line> lines) {
        this.text = text;
        this.marginSign = marginSign;
        this.lines = lines == null ? Collections.emptyList() : Collections.unmodifiableList(lines);
    }

    public static MarketQuote describe(
        boolean ironman,
        int itemId,
        PriceQuote quote,
        Integer buyLimit,
        boolean limitsReady
    ) {
        return describe(ironman, itemId, quote, buyLimit, limitsReady, false);
    }

    public static MarketQuote describe(
        boolean ironman,
        int itemId,
        PriceQuote quote,
        Integer buyLimit,
        boolean limitsReady,
        boolean limitsUnavailable
    ) {
        boolean exempt = GeTax.exempt(itemId);
        boolean priced = quote != null && quote.instantBuy > 0L && quote.instantSell > 0L;
        Long margin = priced ? GeTax.margin(quote.instantBuy, quote.instantSell, exempt) : null;

        StringBuilder body = new StringBuilder();
        List<Line> lines = new ArrayList<>();
        if (priced && margin != null) {
            String sell = String.format("%,d gp", quote.instantSell);
            body.append("Instant sell: ").append(sell);
            lines.add(new Line("Instant sell", sell, 0));
            body.append(" \u00b7 ");
            int tone = Long.signum(margin);
            if (exempt) {
                String marginValue = String.format("%,d gp, tax exempt", margin);
                body.append("margin ").append(marginValue);
                lines.add(new Line("Margin", marginValue, tone));
            } else {
                String marginValue = String.format("%,d gp after tax", margin);
                body.append("margin ").append(marginValue);
                lines.add(new Line("Margin", marginValue, tone));
            }
        }
        appendLimit(body, lines, buyLimit, limitsReady, limitsUnavailable, margin);
        if (body.length() == 0) {
            return new MarketQuote("", 0, Collections.emptyList());
        }
        int sign = margin == null ? 0 : Long.signum(margin);
        String text = body.toString();
        if (ironman) {
            text = "Reference only \u00b7 " + text;
            lines.add(0, new Line("", "Reference only", 0));
        }
        return new MarketQuote(text, sign, lines);
    }

    private static void appendLimit(
        StringBuilder body,
        List<Line> lines,
        Integer buyLimit,
        boolean limitsReady,
        boolean limitsUnavailable,
        Long margin
    ) {
        String limitText;
        String limitKey;
        String limitValue;
        if (!limitsReady && limitsUnavailable) {
            limitText = "buy limits unavailable";
            limitKey = "Buy limit";
            limitValue = "unavailable";
        } else if (!limitsReady) {
            limitText = "buy limit loading";
            limitKey = "Buy limit";
            limitValue = "loading";
        } else if (buyLimit == null) {
            limitText = "buy limit not listed";
            limitKey = "Buy limit";
            limitValue = "not listed";
        } else {
            limitText = String.format("limit %,d", buyLimit);
            limitKey = "Limit";
            limitValue = String.format("%,d", buyLimit);
        }
        if (body.length() > 0) {
            body.append(" \u00b7 ");
        }
        body.append(limitText);
        lines.add(new Line(limitKey, limitValue, 0));
        Long perLimit = GeTax.perLimit(margin, buyLimit);
        if (perLimit != null) {
            String perValue = String.format("%,d gp", perLimit);
            body.append(" \u00b7 per 4h ").append(perValue);
            lines.add(new Line("Per 4h", perValue, Long.signum(perLimit)));
        }
    }
}
