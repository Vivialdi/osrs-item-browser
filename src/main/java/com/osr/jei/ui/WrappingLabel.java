package com.osr.jei.ui;

import net.runelite.client.ui.PluginPanel;

import javax.swing.JLabel;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;

/**
 * HTML label whose preferred height follows the current parent width,
 * so names and shop lines wrap instead of clipping when the sidebar is resized.
 */
public class WrappingLabel extends JLabel {

    public WrappingLabel() {
        setAlignmentX(LEFT_ALIGNMENT);
    }

    public void setHtml(String innerHtml) {
        setText("<html>" + (innerHtml == null ? "" : innerHtml) + "</html>");
    }

    @Override
    public Dimension getPreferredSize() {
        int width = resolveWidth();
        if (width <= 0) {
            return super.getPreferredSize();
        }
        View view = (View) getClientProperty(BasicHTML.propertyKey);
        if (view == null) {
            Dimension plain = super.getPreferredSize();
            return new Dimension(Math.min(plain.width, width), plain.height);
        }
        view.setSize(width, 0);
        int height = (int) Math.ceil(view.getPreferredSpan(View.Y_AXIS));
        return new Dimension(width, Math.max(1, height));
    }

    @Override
    public Dimension getMaximumSize() {
        Dimension preferred = getPreferredSize();
        return new Dimension(Integer.MAX_VALUE, preferred.height);
    }

    private int resolveWidth() {
        int own = getWidth();
        if (own > 20) {
            return own;
        }
        Container parent = getParent();
        while (parent != null) {
            if (parent.getWidth() > 20) {
                Insets insets = parent.getInsets();
                int chrome = insets == null ? 0 : insets.left + insets.right;
                return Math.max(40, parent.getWidth() - chrome - 8);
            }
            parent = parent.getParent();
        }
        return PluginPanel.PANEL_WIDTH - 24;
    }
}
