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
        int width = WidthLimit.of(this);
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
