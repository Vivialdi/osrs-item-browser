package com.osr.jei.ui;

import javax.swing.JButton;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import java.awt.Dimension;

/**
 * Link-styled button whose label wraps to the sidebar width.
 * {@link #getText()} stays the plain label so callers can still read it.
 */
public class WrappingButton extends JButton {

    private String plain = "";
    private int laidOutWidth = -1;
    private View sizedView;

    public WrappingButton(String text) {
        super();
        setAlignmentX(LEFT_ALIGNMENT);
        setText(text);
    }

    @Override
    public void setText(String text) {
        String next = text == null ? "" : text;
        if (next.startsWith("<html>")) {
            plain = next;
            laidOutWidth = -1;
            sizedView = null;
            super.setText(next);
            return;
        }
        plain = next;
        laidOutWidth = -1;
        sizedView = null;
        super.setText("<html>" + escape(next) + "</html>");
    }

    /** Plain label, without the HTML wrapper used for wrapping. */
    public String plainText() {
        return plain;
    }

    @Override
    public void setFont(java.awt.Font font) {
        super.setFont(font);
        laidOutWidth = -1;
        sizedView = null;
    }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) {
            return super.getPreferredSize();
        }
        int limit = WidthLimit.of(this);
        View view = (View) getClientProperty(BasicHTML.propertyKey);
        if (view == null) {
            Dimension plainSize = super.getPreferredSize();
            return new Dimension(Math.min(plainSize.width, limit), plainSize.height);
        }
        int unwrapped = (int) Math.ceil(view.getPreferredSpan(View.X_AXIS));
        int width = Math.max(1, Math.min(unwrapped, limit));
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

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
