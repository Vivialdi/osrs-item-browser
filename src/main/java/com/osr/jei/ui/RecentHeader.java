package com.osr.jei.ui;

import net.runelite.client.ui.ColorScheme;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * The Recent title with Clear on the same line, at the right edge.
 */
public final class RecentHeader {

    private static final Color MUTED = new Color(150, 150, 150);
    private static final String CLEAR = "clear";

    private RecentHeader() {
    }

    public static JPanel create(UiStyle style, Runnable onClear) {
        JPanel row = new JPanel(new BorderLayout()) {
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
        row.setAlignmentX(JPanel.LEFT_ALIGNMENT);

        JLabel title = text(style, "Recent");
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));

        JLabel clear = text(style, "Clear");
        clear.setBorder(BorderFactory.createEmptyBorder(0, 6, 2, 0));
        clear.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clear.setOpaque(true);
        clear.setBackground(ColorScheme.DARK_GRAY_COLOR);
        clear.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (!clear.isVisible() || event.getButton() != MouseEvent.BUTTON1 || onClear == null) {
                    return;
                }
                onClear.run();
            }

            @Override
            public void mouseEntered(MouseEvent event) {
                clear.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
                clear.repaint();
            }

            @Override
            public void mouseExited(MouseEvent event) {
                clear.setBackground(ColorScheme.DARK_GRAY_COLOR);
                clear.repaint();
            }
        });
        row.putClientProperty(CLEAR, clear);
        row.add(title, BorderLayout.WEST);
        row.add(clear, BorderLayout.EAST);
        return row;
    }

    public static JLabel clear(JPanel header) {
        Object value = header.getClientProperty(CLEAR);
        return value instanceof JLabel ? (JLabel) value : null;
    }

    private static JLabel text(UiStyle style, String words) {
        JLabel label = new JLabel(words);
        if (style != null) {
            style.apply(label, "small");
        }
        label.setForeground(MUTED);
        return label;
    }
}
