package com.osr.jei.ui;

import net.runelite.client.ui.PluginPanel;

import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JViewport;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;

/**
 * Vertical stack that tracks the scroll viewport width. A long child cannot
 * make the scroll view wider than the sidebar, so text wraps instead of clipping.
 */
public class ScrollWidthPanel extends JPanel implements javax.swing.Scrollable {

    public ScrollWidthPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setAlignmentX(LEFT_ALIGNMENT);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension pref = super.getPreferredSize();
        int width = trackedWidth();
        if (width > 0) {
            return new Dimension(width, pref.height);
        }
        int capped = Math.min(Math.max(pref.width, 0), PluginPanel.PANEL_WIDTH);
        return new Dimension(capped, pref.height);
    }

    @Override
    public Dimension getMinimumSize() {
        Dimension min = super.getMinimumSize();
        return new Dimension(0, min.height);
    }

    @Override
    public Dimension getMaximumSize() {
        Dimension preferred = getPreferredSize();
        return new Dimension(Integer.MAX_VALUE, preferred.height);
    }

    private int trackedWidth() {
        Container parent = getParent();
        if (parent instanceof JViewport && parent.getWidth() > 0) {
            return parent.getWidth();
        }
        return -1;
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return new Dimension(PluginPanel.PANEL_WIDTH, 180);
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return Math.max(16, visibleRect == null ? 16 : visibleRect.height - 16);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}
