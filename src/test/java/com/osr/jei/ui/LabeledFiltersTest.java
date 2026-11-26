package com.osr.jei.ui;

import com.osr.jei.search.SearchCriteria;
import org.junit.Test;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LabeledFiltersTest {

    @Test
    public void combosShareOneColumnAtFont12And22() {
        align(12, 230);
        align(22, 230);
    }

    private static void align(int fontSize, int width) {
        UiStyle style = UiStyle.from(fontSize, FontStyleChoice.REGULAR, FontFamilyChoice.SANS_SERIF);
        JComboBox<?>[] combos = new JComboBox<?>[] {
            combo(style, SearchCriteria.Trade.values(), SearchCriteria.Trade.ANY),
            combo(style, SearchCriteria.Members.values(), SearchCriteria.Members.FREE),
            combo(style, SearchCriteria.Equip.values(), SearchCriteria.Equip.ANY),
            combo(style, SearchCriteria.SlotChoice.values(), SearchCriteria.SlotChoice.ANY)
        };
        JPanel filters = LabeledFilters.column(
            style,
            new java.awt.Color(150, 150, 150),
            new String[] {"Trade", "World", "Item", "Slot"},
            combos
        );
        filters.setSize(width, filters.getPreferredSize().height);
        filters.doLayout();
        layout(filters);
        filters.setSize(width, filters.getPreferredSize().height);
        filters.doLayout();
        layout(filters);

        List<JLabel> labels = labels(filters);
        assertEquals(4, labels.size());
        int labelWidth = labels.get(0).getWidth();
        int comboX = combos[0].getX();
        int comboWidth = combos[0].getWidth();
        assertTrue(labelWidth > 0);
        assertTrue(comboWidth > 40);
        for (int i = 0; i < 4; i++) {
            assertEquals("label width at font " + fontSize, labelWidth, labels.get(i).getWidth());
            assertEquals("combo x at font " + fontSize, comboX, combos[i].getX());
            assertEquals("combo width at font " + fontSize, comboWidth, combos[i].getWidth());
            String text = String.valueOf(combos[i].getSelectedItem());
            int textWidth = combos[i].getFontMetrics(combos[i].getFont()).stringWidth(text);
            assertTrue(text + " clips at font " + fontSize + " in " + comboWidth,
                comboWidth >= textWidth + 18);
            assertTrue(combos[i].getHeight() >= combos[i].getFont().getSize());
        }
        assertEquals(width, comboX + comboWidth);
        Font larger = style.forRole("small").deriveFont(style.forRole("small").getSize() + 4f);
        for (JLabel label : labels) {
            label.setFont(larger);
        }
        int grown = LabeledFilters.shareLabelColumn(labels);
        assertTrue(grown > labelWidth);
    }

    private static <E> JComboBox<E> combo(UiStyle style, E[] values, E selected) {
        JComboBox<E> combo = new JComboBox<>(values);
        style.apply(combo, "small");
        combo.setSelectedItem(selected);
        int line = combo.getFontMetrics(combo.getFont()).getHeight();
        int height = Math.max(24, line + 4);
        combo.setPreferredSize(new Dimension(40, height));
        combo.setMinimumSize(new Dimension(0, height));
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        return combo;
    }

    private static void layout(Component component) {
        if (component instanceof Container) {
            Container container = (Container) component;
            container.doLayout();
            for (Component child : container.getComponents()) {
                layout(child);
            }
        }
    }

    private static List<JLabel> labels(Component component) {
        List<JLabel> found = new ArrayList<>();
        if (component instanceof JLabel) {
            found.add((JLabel) component);
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                found.addAll(labels(child));
            }
        }
        return found;
    }
}
