package com.osr.jei;

import java.awt.*;

/**
 * A FlowLayout variant that correctly reports its preferred height to a JScrollPane
 * so that items wrap to the next row instead of scrolling horizontally.
 *
 * The standard FlowLayout always returns a single-row height to
 * preferredLayoutSize(), which makes JScrollPane think no vertical scrolling is
 * needed.  This class fixes that by walking each "virtual" row and summing heights.
 *
 * Based on Rob Camick's WrapLayout:
 * https://tips4java.wordpress.com/2008/11/06/wrap-layout/
 */
public class WrapLayout extends FlowLayout {

    public WrapLayout() {
        super(LEFT, 2, 2);
    }

    public WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension minimum = layoutSize(target, false);
        minimum.width -= (getHgap() + 1);
        return minimum;
    }

    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            // On the first pass the container has zero width — walk up until we find
            // a parent that already has a real size so wrapping is calculated correctly.
            int targetWidth = target.getSize().width;
            Container parent = target;
            while (targetWidth == 0 && parent.getParent() != null) {
                parent = parent.getParent();
                targetWidth = parent.getSize().width;
            }
            if (targetWidth == 0) {
                targetWidth = Integer.MAX_VALUE;
            }

            int hgap = getHgap();
            int vgap = getVgap();
            Insets insets = target.getInsets();
            int horizontalInsetsAndGap = insets.left + insets.right + (hgap * 2);
            int maxWidth = targetWidth - horizontalInsetsAndGap;

            Dimension dim = new Dimension(0, 0);
            int rowWidth  = 0;
            int rowHeight = 0;

            int count = target.getComponentCount();
            for (int i = 0; i < count; i++) {
                Component m = target.getComponent(i);
                if (!m.isVisible()) continue;

                Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();

                if (rowWidth + d.width > maxWidth) {
                    addRow(dim, rowWidth, rowHeight);
                    rowWidth  = 0;
                    rowHeight = 0;
                }

                if (rowWidth != 0) rowWidth += hgap;
                rowWidth  += d.width;
                rowHeight  = Math.max(rowHeight, d.height);
            }
            addRow(dim, rowWidth, rowHeight);

            dim.width  += horizontalInsetsAndGap;
            dim.height += insets.top + insets.bottom + vgap * 2;
            return dim;
        }
    }

    private void addRow(Dimension dim, int rowWidth, int rowHeight) {
        dim.width = Math.max(dim.width, rowWidth);
        if (dim.height > 0) dim.height += getVgap();
        dim.height += rowHeight;
    }
}
