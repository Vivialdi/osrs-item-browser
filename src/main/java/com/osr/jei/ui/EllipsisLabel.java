package com.osr.jei.ui;

import javax.swing.JLabel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;

/**
 * Single-line label for an icon row. Text that does not fit the current
 * sidebar width is cut with an ellipsis; the tooltip keeps the full line.
 */
public class EllipsisLabel extends JLabel {

    private String name = "";
    private Color nameColor = Color.WHITE;
    private String suffix = "";
    private Color suffixColor = Color.LIGHT_GRAY;

    public EllipsisLabel() {
        setAlignmentX(LEFT_ALIGNMENT);
    }

    public void setPieces(String name, Color nameColor, String suffix, Color suffixColor) {
        this.name = name == null ? "" : name;
        this.nameColor = nameColor == null ? getForeground() : nameColor;
        this.suffix = suffix == null ? "" : suffix;
        this.suffixColor = suffixColor == null ? this.nameColor : suffixColor;
        setForeground(this.nameColor);
        setToolTipText(fullText());
        revalidate();
        repaint();
    }

    @Override
    public String getText() {
        return fullText();
    }

    @Override
    public void setText(String text) {
        setPieces(text, nameColor == null ? getForeground() : nameColor, "", null);
    }

    private String fullText() {
        if (suffix == null || suffix.isEmpty()) {
            return name == null ? "" : name;
        }
        return name + " \u00b7 " + suffix;
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics metrics = getFontMetrics(getFont());
        Insets insets = getInsets();
        int textWidth = metrics.stringWidth(fullText());
        int available = WidthLimit.of(this);
        int width = Math.min(textWidth, Math.max(0, available));
        int height = metrics.getHeight() + insets.top + insets.bottom;
        return new Dimension(Math.max(0, width), Math.max(1, height));
    }

    @Override
    public Dimension getMinimumSize() {
        Dimension preferred = getPreferredSize();
        return new Dimension(0, preferred.height);
    }

    @Override
    public Dimension getMaximumSize() {
        Dimension preferred = getPreferredSize();
        return new Dimension(Integer.MAX_VALUE, preferred.height);
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (isOpaque()) {
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
        }
        FontMetrics metrics = g.getFontMetrics(getFont());
        Insets insets = getInsets();
        int available = getWidth() - insets.left - insets.right;
        if (available <= 0) {
            return;
        }
        int y = insets.top + metrics.getAscent();
        String shownName = name == null ? "" : name;
        String shownSuffix = suffix == null || suffix.isEmpty() ? "" : " \u00b7 " + suffix;
        int nameWidth = metrics.stringWidth(shownName);
        int suffixWidth = metrics.stringWidth(shownSuffix);
        if (nameWidth + suffixWidth <= available) {
            g.setColor(nameColor);
            g.drawString(shownName, insets.left, y);
            if (!shownSuffix.isEmpty()) {
                g.setColor(suffixColor);
                g.drawString(shownSuffix, insets.left + nameWidth, y);
            }
            return;
        }
        g.setColor(nameColor);
        g.drawString(clip(metrics, shownName + shownSuffix, available), insets.left, y);
    }

    private static String clip(FontMetrics metrics, String text, int available) {
        String ellipsis = "\u2026";
        int ellipsisWidth = metrics.stringWidth(ellipsis);
        if (available <= ellipsisWidth) {
            return ellipsis;
        }
        int end = text.length();
        while (end > 0 && metrics.stringWidth(text.substring(0, end)) + ellipsisWidth > available) {
            end--;
        }
        return text.substring(0, end) + ellipsis;
    }
}
