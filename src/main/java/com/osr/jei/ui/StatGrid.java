package com.osr.jei.ui;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Short labelled rows for Grand Exchange figures. Keys share one width and
 * values wrap in the space that is left, so a narrow sidebar cannot clip them.
 */
public final class StatGrid extends ScrollWidthPanel {

    public static final class Row {
        public final String key;
        public final String value;
        public final Color color;

        public Row(String key, String value, Color color) {
            this.key = key == null ? "" : key;
            this.value = value == null ? "" : value;
            this.color = color == null ? Color.LIGHT_GRAY : color;
        }

        public boolean fullWidth() {
            return key.isEmpty();
        }
    }

    private UiStyle style;
    private List<Row> rows = Collections.emptyList();

    public StatGrid(UiStyle style) {
        this.style = style;
        setOpaque(false);
        setAlignmentX(LEFT_ALIGNMENT);
    }

    public void useStyle(UiStyle style) {
        if (style != null) {
            this.style = style;
        }
    }

    public void setRows(List<Row> rows) {
        this.rows = rows == null ? Collections.emptyList() : new ArrayList<>(rows);
        rebuild();
    }

    private void rebuild() {
        removeAll();
        if (rows.isEmpty()) {
            setVisible(false);
            return;
        }
        int keyWidth = keyColumnWidth();
        for (Row row : rows) {
            add(row.fullWidth() ? fullWidth(row) : keyed(row, keyWidth));
        }
        setVisible(true);
    }

    private int keyColumnWidth() {
        if (style == null) {
            return 0;
        }
        FontMetrics metrics = getFontMetrics(style.forRole("small"));
        int width = 0;
        for (Row row : rows) {
            if (!row.fullWidth()) {
                width = Math.max(width, metrics.stringWidth(row.key));
            }
        }
        return width;
    }

    private JPanel fullWidth(Row row) {
        JPanel line = rowPanel();
        WrappingLabel value = valueLabel(row.value, row.color);
        value.setToolTipText(row.value);
        line.add(value, BorderLayout.CENTER);
        return line;
    }

    private JPanel keyed(Row row, int keyWidth) {
        JPanel line = rowPanel();
        JLabel key = new JLabel(row.key);
        if (style != null) {
            style.apply(key, "small");
        }
        key.setForeground(new Color(150, 150, 150));
        Dimension keySize = new Dimension(keyWidth, key.getPreferredSize().height);
        key.setPreferredSize(keySize);
        key.setMinimumSize(keySize);
        WrappingLabel value = valueLabel(row.value, row.color);
        String tip = row.key + ": " + row.value;
        value.setToolTipText(tip);
        key.setToolTipText(tip);
        line.add(key, BorderLayout.WEST);
        line.add(value, BorderLayout.CENTER);
        return line;
    }

    private WrappingLabel valueLabel(String value, Color color) {
        WrappingLabel label = new WrappingLabel();
        if (style != null) {
            style.apply(label, "small");
        }
        label.setForeground(color);
        label.setHtml(escape(value));
        return label;
    }

    private static JPanel rowPanel() {
        JPanel line = new JPanel(new BorderLayout(6, 0)) {
            @Override
            public Dimension getMaximumSize() {
                Dimension preferred = getPreferredSize();
                return new Dimension(Integer.MAX_VALUE, preferred.height);
            }

            @Override
            public Dimension getMinimumSize() {
                Dimension preferred = getPreferredSize();
                return new Dimension(0, preferred.height);
            }
        };
        line.setOpaque(false);
        line.setAlignmentX(LEFT_ALIGNMENT);
        return line;
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
