package com.osr.jei.ui;

import com.osr.jei.ironman.TradeCopy;
import com.osr.jei.model.ShopEntry;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * One shop: the name is a wiki link, the price is its own column, and map is a separate action.
 */
public final class ShopRows {

    private static final Color LINK = new Color(100, 180, 255);
    private static final Color NAME = new Color(190, 190, 190);
    /** Gold, not the shop-name blue, so the control reads as an action. */
    public static final Color MAP_ACTION = new Color(196, 163, 90);
    public static final String MAP_LINK = "\u2316 Show on map";
    public static final String MAP_OPENING = "\u2316 Opening map\u2026";
    public static final String MAP_PINNED = "\u2316 Pinned. Open the world map to view.";

    private ShopRows() {
    }

    public static JPanel row(
        UiStyle style,
        ShopEntry shop,
        TradeCopy.ShopLine line,
        Runnable openWiki,
        Consumer<WrappingLabel> showMap
    ) {
        JPanel row = new JPanel(new BorderLayout(6, 0)) {
            @Override
            public Dimension getMaximumSize() {
                Dimension preferred = getPreferredSize();
                return new Dimension(Integer.MAX_VALUE, preferred.height);
            }

            @Override
            public Dimension getMinimumSize() {
                Dimension preferred = getPreferredSize();
                return new Dimension(0, preferred.height);
            }
        };
        row.setOpaque(false);
        row.setAlignmentX(0f);

        String stock = line == null || line.stock == null ? "" : line.stock.trim();
        String name = shop == null || shop.shopName == null ? "" : shop.shopName;
        String label = stock.isEmpty() ? name : name + " " + stock;
        WrappingLabel title = new WrappingLabel();
        if (style != null) {
            style.apply(title, "small");
        }
        title.setForeground(LINK);
        title.setHtml(escape(label));
        title.setToolTipText("Open the " + name + " wiki page");
        title.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (openWiki != null) {
            title.addMouseListener(click(openWiki));
        }

        String priceText = priceText(shop);
        JLabel price = new JLabel(priceText);
        if (style != null) {
            style.apply(price, "small");
        }
        price.setForeground(color(line));
        price.setHorizontalAlignment(JLabel.RIGHT);
        Dimension priceSize = price.getPreferredSize();
        price.setPreferredSize(priceSize);
        price.setMinimumSize(priceSize);

        JPanel text = new JPanel(new BorderLayout(0, 0));
        text.setOpaque(false);
        text.add(title, BorderLayout.CENTER);
        WrappingLabel map = mapLink(style);
        if (showMap != null) {
            map.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    showMap.accept(map);
                }
            });
        }
        text.add(map, BorderLayout.SOUTH);

        row.add(text, BorderLayout.CENTER);
        row.add(price, BorderLayout.EAST);
        row.setToolTipText(name + " " + priceText);
        return row;
    }

    /** Indented, smaller, gold action. Not the same blue as a shop or monster name. */
    public static WrappingLabel mapLink(UiStyle style) {
        WrappingLabel map = new WrappingLabel();
        if (style != null) {
            style.apply(map, "small");
        }
        map.setForeground(MAP_ACTION);
        map.setHtml(MAP_LINK);
        map.setBorder(BorderFactory.createEmptyBorder(0, 16, 2, 0));
        map.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        map.setToolTipText("Pin this place and open the world map");
        return map;
    }

    public static String priceText(ShopEntry shop) {
        if (shop == null) {
            return "";
        }
        String unit = shop.isCoins() ? "gp" : shop.currency;
        return String.format(java.util.Locale.ROOT, "%,d %s", shop.price, unit);
    }

    private static Color color(TradeCopy.ShopLine line) {
        if (line == null || line.priceColor == null) {
            return NAME;
        }
        try {
            return Color.decode(line.priceColor);
        } catch (NumberFormatException e) {
            return NAME;
        }
    }

    private static MouseAdapter click(Runnable action) {
        return new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                action.run();
            }
        };
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
