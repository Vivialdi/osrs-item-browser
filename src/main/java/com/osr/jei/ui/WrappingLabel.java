package com.osr.jei.ui;

import net.runelite.client.ui.PluginPanel;

import javax.swing.JLabel;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import java.awt.Dimension;
import java.awt.Insets;

/**
 * HTML label whose preferred height follows the current sidebar width,
 * so names and shop lines wrap instead of clipping when the sidebar is resized.
 */
public class WrappingLabel extends JLabel {

    private int laidOutWidth = -1;
    private int settledWidth = -1;
    private View sizedView;
    private String innerHtml = "";
    private boolean htmlMode;

    public WrappingLabel() {
        setAlignmentX(LEFT_ALIGNMENT);
    }

    public void setHtml(String innerHtml) {
        htmlMode = true;
        this.innerHtml = innerHtml == null ? "" : innerHtml;
        publish();
    }

    @Override
    public void setText(String text) {
        super.setText(text);
        laidOutWidth = -1;
        sizedView = null;
        settledWidth = -1;
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
        if (!htmlMode || width <= 0 || width == settledWidth) {
            return;
        }
        int inner = innerWidth(width);
        if (inner <= 20) {
            return;
        }
        int needed = wrappedHeight(inner);
        settledWidth = width;
        if (Math.abs(needed - height) > 2) {
            revalidate();
        }
    }

    @Override
    public void setFont(java.awt.Font font) {
        super.setFont(font);
        laidOutWidth = -1;
        sizedView = null;
        if (htmlMode) {
            publish();
        }
    }

    private void publish() {
        String body = wraps() ? innerHtml : "<nobr>" + innerHtml + "</nobr>";
        setText("<html>" + body + "</html>");
    }

    private boolean wraps() {
        return !Boolean.FALSE.equals(getClientProperty(UiStyle.WRAP));
    }

    @Override
    public Dimension getPreferredSize() {
        int width = innerWidth(getWidth());
        if (width <= 20) {
            width = WidthLimit.of(this);
        }
        if (width <= 0) {
            width = PluginPanel.PANEL_WIDTH - 24;
        }
        View view = (View) getClientProperty(BasicHTML.propertyKey);
        if (view == null) {
            Dimension plain = super.getPreferredSize();
            return new Dimension(Math.min(plain.width, width), plain.height);
        }
        int layoutWidth = width;
        if (!wraps()) {
            layoutWidth = Math.max(width, (int) Math.ceil(view.getPreferredSpan(View.X_AXIS)));
        }
        if (view != sizedView || layoutWidth != laidOutWidth) {
            view.setSize(layoutWidth, 0);
            sizedView = view;
            laidOutWidth = layoutWidth;
        }
        int height = (int) Math.ceil(view.getPreferredSpan(View.Y_AXIS));
        return new Dimension(wraps() ? width : Math.min(layoutWidth, width), Math.max(1, height));
    }

    private int wrappedHeight(int innerWidth) {
        View view = (View) getClientProperty(BasicHTML.propertyKey);
        if (view == null) {
            return Math.max(1, getFontMetrics(getFont()).getHeight());
        }
        int layoutWidth = innerWidth;
        if (!wraps()) {
            layoutWidth = Math.max(innerWidth, (int) Math.ceil(view.getPreferredSpan(View.X_AXIS)));
        }
        view.setSize(layoutWidth, 0);
        sizedView = view;
        laidOutWidth = layoutWidth;
        return Math.max(1, (int) Math.ceil(view.getPreferredSpan(View.Y_AXIS)));
    }

    private int innerWidth(int width) {
        if (width <= 0) {
            return 0;
        }
        Insets insets = getInsets();
        if (insets == null) {
            return width;
        }
        return Math.max(0, width - insets.left - insets.right);
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
