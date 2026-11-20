package com.osr.jei.ui;

import net.runelite.client.ui.PluginPanel;

import java.awt.Component;
import java.awt.Container;
import java.awt.Insets;
import javax.swing.JViewport;
import java.awt.BorderLayout;
import java.awt.LayoutManager;

/**
 * Width a label should wrap or ellipsize to. Walks up to the scroll viewport
 * and subtracts borders plus any west/east siblings, so a resize of the
 * sidebar changes the limit on the next layout without a second measurement pass.
 */
public final class WidthLimit {

    private static final int FALLBACK = PluginPanel.PANEL_WIDTH - 24;
    private static final int SAFETY = 4;

    private WidthLimit() {
    }

    public static int of(Component component) {
        if (component == null) {
            return FALLBACK;
        }
        int fromViewport = fromViewport(component);
        if (fromViewport > 20) {
            return fromViewport;
        }
        int own = component.getWidth();
        Insets insets = insetsOf(component);
        int inner = own - insets.left - insets.right;
        if (inner > 20) {
            return inner;
        }
        return FALLBACK;
    }

    private static int fromViewport(Component component) {
        int chrome = 0;
        Component child = component;
        Container parent = component.getParent();
        Insets self = insetsOf(component);
        chrome += self.left + self.right;
        int guard = 0;
        while (parent != null && guard++ < 24) {
            if (parent instanceof JViewport) {
                int width = parent.getWidth();
                if (width <= 20) {
                    return -1;
                }
                return Math.max(40, width - chrome - SAFETY);
            }
            chrome += horizontalInsets(parent);
            chrome += siblingBreadth(parent, child);
            child = parent;
            parent = parent.getParent();
        }
        return -1;
    }

    private static int siblingBreadth(Container parent, Component child) {
        LayoutManager layout = parent.getLayout();
        if (!(layout instanceof BorderLayout)) {
            return 0;
        }
        BorderLayout border = (BorderLayout) layout;
        int extra = 0;
        Component west = border.getLayoutComponent(parent, BorderLayout.WEST);
        Component east = border.getLayoutComponent(parent, BorderLayout.EAST);
        if (west != null && west != child && west.isVisible()) {
            extra += west.getPreferredSize().width + border.getHgap();
        }
        if (east != null && east != child && east.isVisible()) {
            extra += east.getPreferredSize().width + border.getHgap();
        }
        return extra;
    }

    private static int horizontalInsets(Container container) {
        Insets insets = container.getInsets();
        if (insets == null) {
            return 0;
        }
        return insets.left + insets.right;
    }

    private static Insets insetsOf(Component component) {
        if (component instanceof Container) {
            Insets insets = ((Container) component).getInsets();
            if (insets != null) {
                return insets;
            }
        }
        return new Insets(0, 0, 0, 0);
    }
}
