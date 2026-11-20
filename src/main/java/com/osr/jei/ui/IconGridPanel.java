package com.osr.jei.ui;

import net.runelite.client.ui.PluginPanel;

import javax.swing.JPanel;
import javax.swing.Scrollable;
import java.awt.Dimension;
import java.awt.Rectangle;

/** Icon grid that stays as wide as the sidebar and reports the wrapped height. */
public class IconGridPanel extends JPanel implements Scrollable {

    public IconGridPanel(int cell, int gap) {
        super(new IconGridLayout(cell, gap));
        setAlignmentX(LEFT_ALIGNMENT);
    }

    @Override
    public Dimension getPreferredSize() {
        return getLayout().preferredLayoutSize(this);
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
    public Dimension getPreferredScrollableViewportSize() {
        return new Dimension(PluginPanel.PANEL_WIDTH, getPreferredSize().height);
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
