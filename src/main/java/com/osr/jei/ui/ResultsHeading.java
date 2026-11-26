package com.osr.jei.ui;

import net.runelite.client.ui.ColorScheme;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;

/**
 * Compact label between Recent and the search-result icons.
 * Same header style as Favourites and Recent: a small gap, then the word.
 */
public final class ResultsHeading {

    public static final String LABEL = "Results";
    private static final Color MUTED = new Color(150, 150, 150);

    private ResultsHeading() {
    }

    public static JPanel create(UiStyle style) {
        JPanel bar = new JPanel() {
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
        bar.setLayout(new BoxLayout(bar, BoxLayout.Y_AXIS));
        bar.setBackground(ColorScheme.DARK_GRAY_COLOR);
        bar.setOpaque(true);
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        bar.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

        WrappingLabel label = new WrappingLabel();
        label.setName("resultsLabel");
        if (style != null) {
            style.apply(label, "small");
        }
        label.setForeground(MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
        label.setHtml(LABEL);

        bar.add(label);
        return bar;
    }

    public static JLabel label(JPanel heading) {
        return (JLabel) heading.getComponent(0);
    }
}
