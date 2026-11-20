package com.osr.jei.ui;

import javax.swing.Box;
import java.awt.BorderLayout;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;

/**
 * One filter per row: a short label on the left and a combo that takes the rest.
 * At sidebar width the selected value stays readable, unlike a 2×2 grid.
 */
public final class LabeledFilters {

    private LabeledFilters() {
    }

    public static JPanel column(Font labelFont, Color labelColor, String[] labels, JComponent[] fields) {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setOpaque(false);
        box.setAlignmentX(0f);
        for (int i = 0; i < labels.length; i++) {
            if (i > 0) {
                box.add(Box.createRigidArea(new Dimension(0, 3)));
            }
            box.add(row(labelFont, labelColor, labels[i], fields[i]));
        }
        return box;
    }

    public static JPanel row(Font labelFont, Color labelColor, String label, JComponent field) {
        JLabel name = new JLabel(label);
        if (labelFont != null) {
            name.setFont(labelFont);
        }
        if (labelColor != null) {
            name.setForeground(labelColor);
        }
        int labelWidth = Math.max(36, name.getPreferredSize().width);
        name.setPreferredSize(new Dimension(labelWidth, name.getPreferredSize().height));
        name.setMinimumSize(new Dimension(labelWidth, name.getPreferredSize().height));
        JPanel line = new JPanel(new BorderLayout(6, 0)) {
            @Override
            public Dimension getPreferredSize() {
                Dimension fieldSize = field.getPreferredSize();
                int height = Math.max(fieldSize.height, name.getPreferredSize().height);
                return new Dimension(labelWidth + 6 + Math.max(80, fieldSize.width), height);
            }

            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }

            @Override
            public Dimension getMinimumSize() {
                return new Dimension(0, getPreferredSize().height);
            }
        };
        line.setOpaque(false);
        line.setAlignmentX(0f);
        field.setMinimumSize(new Dimension(0, field.getPreferredSize().height));
        line.add(name, BorderLayout.WEST);
        line.add(field, BorderLayout.CENTER);
        return line;
    }
}
