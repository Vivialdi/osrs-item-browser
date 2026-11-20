package com.osr.jei.ui;

import net.runelite.client.ui.PluginPanel;

import javax.swing.JLabel;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import java.awt.Dimension;

/**
 * HTML label whose preferred height follows the current sidebar width,
 * so names and shop lines wrap instead of clipping when the sidebar is resized.
 */
public class WrappingLabel extends JLabel {

    private int laidOutWidth = -1;
    private View sizedView;

    public WrappingLabel() {
        setAlignmentX(LEFT_ALIGNMENT);
    }

    public void setHtml(String innerHtml) {
        setText("<html>" + (innerHtml == null ? "" : innerHtml) + "</html>");
    }

    @Override
    public void setText(String text) {
        super.setText(text);
        laidOutWidth = -1;
        sizedView = null;
    }

    @Override
    public void setFont(java.awt.Font font) {
        super.setFont(font);
        laidOutWidth = -1;
        sizedView = null;
    }

    @Override
    public Dimension getPreferredSize() {
        int width = WidthLimit.of(this);
        if (width <= 0) {
            width = PluginPanel.PANEL_WIDTH - 24;
        }
        View view = (View) getClientProperty(BasicHTML.propertyKey);
        if (view == null) {
            Dimension plain = super.getPreferredSize();
            return new Dimension(Math.min(plain.width, width), plain.height);
        }
        if (view != sizedView || width != laidOutWidth) {
            view.setSize(width, 0);
            sizedView = view;
            laidOutWidth = width;
        }
        int height = (int) Math.ceil(view.getPreferredSpan(View.Y_AXIS));
        return new Dimension(width, Math.max(1, height));
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
}
