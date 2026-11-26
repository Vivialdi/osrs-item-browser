package com.osr.jei.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;

/**
 * Equal icon cells that fill the current width. Extra space becomes the gap
 * between columns, so a row does not leave a dead strip on the right.
 */
public final class IconGridLayout implements LayoutManager {

    private final int cell;
    private final int gap;

    public IconGridLayout(int cell, int gap) {
        this.cell = Math.max(1, cell);
        this.gap = Math.max(0, gap);
    }

    public static int columns(int innerWidth, int cell, int gap) {
        int unit = Math.max(1, cell + Math.max(0, gap));
        int width = Math.max(cell, innerWidth);
        return Math.max(1, (width + Math.max(0, gap)) / unit);
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
    }

    @Override
    public void removeLayoutComponent(Component comp) {
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        return size(parent, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        Insets insets = parent.getInsets();
        return new Dimension(cell + insets.left + insets.right, cell + insets.top + insets.bottom);
    }

    @Override
    public void layoutContainer(Container parent) {
        synchronized (parent.getTreeLock()) {
            Insets insets = parent.getInsets();
            int inner = Math.max(cell, parent.getWidth() - insets.left - insets.right);
            int columns = columns(inner, cell, gap);
            int count = visibleCount(parent);
            if (count == 0) {
                return;
            }
            int hgap = horizontalGap(inner, columns);
            int index = 0;
            for (Component child : parent.getComponents()) {
                if (!child.isVisible()) {
                    continue;
                }
                int column = index % columns;
                int row = index / columns;
                int x = insets.left + column * (cell + hgap);
                if (columns == 1) {
                    x = insets.left + Math.max(0, (inner - cell) / 2);
                }
                int y = insets.top + row * (cell + gap);
                child.setBounds(x, y, cell, cell);
                index++;
            }
        }
    }

    private Dimension size(Container parent, boolean preferred) {
        Insets insets = parent.getInsets();
        int inner = parent.getWidth() - insets.left - insets.right;
        if (inner < cell && parent.getParent() != null) {
            Insets outer = parent.getParent().getInsets();
            inner = parent.getParent().getWidth() - outer.left - outer.right;
        }
        if (inner < cell) {
            inner = net.runelite.client.ui.PluginPanel.PANEL_WIDTH - 16;
        }
        int count = visibleCount(parent);
        int columns = columns(inner, cell, gap);
        int rows = count == 0 ? 0 : (count + columns - 1) / columns;
        int height = rows == 0 ? 0 : rows * cell + Math.max(0, rows - 1) * gap;
        int width = Math.max(inner, cell);
        return new Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom);
    }

    private int horizontalGap(int inner, int columns) {
        if (columns <= 1) {
            return 0;
        }
        int leftover = inner - columns * cell;
        return Math.max(gap, leftover / (columns - 1));
    }

    private static int visibleCount(Container parent) {
        int count = 0;
        for (Component child : parent.getComponents()) {
            if (child.isVisible()) {
                count++;
            }
        }
        return count;
    }
}
