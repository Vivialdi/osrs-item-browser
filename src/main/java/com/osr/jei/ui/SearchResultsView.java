package com.osr.jei.ui;

import net.runelite.client.ui.ColorScheme;

import javax.swing.BorderFactory;
import java.awt.Color;

/**
 * Search-result icons and the line shown when a search has no matches.
 * The line is a full-width wrapped label in the pane font. The icon grid
 * sizes every child to one cell, so the line lives beside the grid.
 */
public final class SearchResultsView extends ScrollWidthPanel {

    public static final String NO_MATCH = "No items match that search.";
    public static final String STILL_LOADING = "The item list is still loading.";

    private static final Color MUTED = new Color(150, 150, 150);

    private final IconGridPanel grid;
    private final WrappingLabel empty;

    public SearchResultsView(UiStyle style, int cell, int gap) {
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setOpaque(true);
        empty = new WrappingLabel();
        empty.setName("emptyResults");
        if (style != null) {
            style.apply(empty, "small");
        }
        empty.setForeground(MUTED);
        empty.setAlignmentX(LEFT_ALIGNMENT);
        empty.setBorder(BorderFactory.createEmptyBorder(4, 2, 8, 2));
        empty.setVisible(false);
        grid = new IconGridPanel(cell, gap);
        grid.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        grid.setAlignmentX(LEFT_ALIGNMENT);
        add(empty);
        add(grid);
    }

    public IconGridPanel grid() {
        return grid;
    }

    public WrappingLabel emptyLabel() {
        return empty;
    }

    public void showMessage(String text) {
        String plain = text == null ? "" : text;
        empty.setHtml(escape(plain));
        empty.setToolTipText(plain.isEmpty() ? null : plain);
        empty.setVisible(true);
        revalidate();
        repaint();
    }

    public void hideMessage() {
        if (!empty.isVisible()) {
            return;
        }
        empty.setVisible(false);
        revalidate();
        repaint();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
