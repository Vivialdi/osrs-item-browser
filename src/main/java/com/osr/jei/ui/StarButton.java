package com.osr.jei.ui;

import javax.swing.JToggleButton;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * 24px favourite control. It stays in the tab order and toggles with Space.
 */
public class StarButton extends JToggleButton {

    public static final int SIZE = 24;

    private static final Color GOLD = new Color(220, 185, 75);
    private static final Color HOVER = new Color(70, 62, 32);
    private static final Color FOCUS = new Color(255, 152, 31);

    public StarButton(String tooltip) {
        setToolTipText(tooltip);
        setPreferredSize(new Dimension(SIZE, SIZE));
        setMinimumSize(new Dimension(SIZE, SIZE));
        setMaximumSize(new Dimension(SIZE, SIZE));
        setFocusable(true);
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setRolloverEnabled(true);
        setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(SIZE, SIZE);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (getModel().isRollover() || getModel().isPressed() || isSelected()) {
            g.setColor(HOVER);
            g.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 6, 6);
        }
        if (isFocusOwner()) {
            g.setColor(FOCUS);
            g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 6, 6);
        }
        int[] xs = starXs(getWidth() / 2, 9);
        int[] ys = starYs(getHeight() / 2, 9);
        if (isSelected()) {
            g.setColor(GOLD);
            g.fillPolygon(xs, ys, xs.length);
        } else {
            g.setColor(GOLD);
            g.drawPolygon(xs, ys, xs.length);
        }
        g.dispose();
    }

    private static int[] starXs(int cx, int r) {
        int[] xs = new int[10];
        for (int i = 0; i < 10; i++) {
            double angle = Math.toRadians(-90 + i * 36);
            int radius = (i % 2 == 0) ? r : r / 2;
            xs[i] = cx + (int) Math.round(Math.cos(angle) * radius);
        }
        return xs;
    }

    private static int[] starYs(int cy, int r) {
        int[] ys = new int[10];
        for (int i = 0; i < 10; i++) {
            double angle = Math.toRadians(-90 + i * 36);
            int radius = (i % 2 == 0) ? r : r / 2;
            ys[i] = cy + (int) Math.round(Math.sin(angle) * radius);
        }
        return ys;
    }
}
