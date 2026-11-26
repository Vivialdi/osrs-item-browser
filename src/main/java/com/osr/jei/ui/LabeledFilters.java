package com.osr.jei.ui;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

/**
 * One filter per row. Every label uses the same column width, the widest
 * label at the current font, so the combos start on one line and share the
 * leftover sidebar width.
 */
public final class LabeledFilters {

    private static final int GAP = 8;

    private LabeledFilters() {
    }

    public static JPanel column(UiStyle style, Color labelColor, String[] labels, JComponent[] fields) {
        return column(style, style == null ? null : style.forRole("small"), labelColor, labels, fields);
    }

    public static JPanel column(Font labelFont, Color labelColor, String[] labels, JComponent[] fields) {
        return column(null, labelFont, labelColor, labels, fields);
    }

    private static JPanel column(
        UiStyle style,
        Font labelFont,
        Color labelColor,
        String[] labels,
        JComponent[] fields
    ) {
        List<JLabel> names = new ArrayList<>();
        JPanel box = new JPanel() {
            @Override
            public Dimension getPreferredSize() {
                shareLabelColumn(names);
                return super.getPreferredSize();
            }

            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }

            @Override
            public Dimension getMinimumSize() {
                return new Dimension(0, getPreferredSize().height);
            }

            @Override
            public void doLayout() {
                shareLabelColumn(names);
                super.doLayout();
            }
        };
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setOpaque(false);
        box.setAlignmentX(0f);
        for (int i = 0; i < labels.length; i++) {
            if (i > 0) {
                box.add(Box.createRigidArea(new Dimension(0, 3)));
            }
            JLabel name = nameLabel(style, labelFont, labelColor, labels[i]);
            names.add(name);
            box.add(row(name, fields[i]));
        }
        shareLabelColumn(names);
        return box;
    }

    /** Widest label at the fonts they have now. Called again after a font-size change. */
    static int shareLabelColumn(List<JLabel> names) {
        int column = 0;
        for (JLabel name : names) {
            column = Math.max(column, textWidth(name));
        }
        for (JLabel name : names) {
            int height = Math.max(1, name.getFontMetrics(name.getFont()).getHeight());
            Dimension size = new Dimension(column, height);
            name.setPreferredSize(size);
            name.setMinimumSize(size);
            name.setMaximumSize(size);
        }
        return column;
    }

    private static int textWidth(JLabel name) {
        String text = name.getText() == null ? "" : name.getText();
        FontMetrics metrics = name.getFontMetrics(name.getFont());
        Insets insets = name.getInsets();
        int pad = insets == null ? 0 : insets.left + insets.right;
        return metrics.stringWidth(text) + pad;
    }

    private static JLabel nameLabel(UiStyle style, Font labelFont, Color labelColor, String text) {
        JLabel name = new JLabel(text);
        if (style != null) {
            style.apply(name, "small");
        } else if (labelFont != null) {
            name.setFont(labelFont);
            name.putClientProperty(UiStyle.ROLE, "small");
        } else {
            name.putClientProperty(UiStyle.ROLE, "small");
        }
        if (labelColor != null) {
            name.setForeground(labelColor);
        }
        name.setHorizontalAlignment(JLabel.LEFT);
        return name;
    }

    private static JPanel row(JLabel name, JComponent field) {
        JPanel line = new JPanel(new GridBagLayout()) {
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
        GridBagConstraints label = new GridBagConstraints();
        label.gridx = 0;
        label.gridy = 0;
        label.anchor = GridBagConstraints.LINE_START;
        label.fill = GridBagConstraints.NONE;
        label.insets = new Insets(0, 0, 0, GAP);
        label.weightx = 0;
        GridBagConstraints value = new GridBagConstraints();
        value.gridx = 1;
        value.gridy = 0;
        value.anchor = GridBagConstraints.LINE_START;
        value.fill = GridBagConstraints.HORIZONTAL;
        value.weightx = 1;
        field.setMinimumSize(new Dimension(0, field.getMinimumSize().height));
        line.add(name, label);
        line.add(field, value);
        return line;
    }
}
