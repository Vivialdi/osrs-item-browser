package com.osr.jei.ui;

import org.junit.Test;

import javax.swing.JComponent;
import javax.swing.JLabel;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LocationLinesTest {

    @Test
    public void onePlaceIsASingleLine() {
        UiStyle style = UiStyle.from(12, FontStyleChoice.REGULAR, FontFamilyChoice.SANS_SERIF);
        JComponent block = LocationLines.create(
            style, new SectionState(), Collections.singletonList("Rellekka"));
        assertFalse(block instanceof CollapsibleSection);
        String text = visible(block);
        assertEquals("Location: Rellekka", text.replaceAll("<[^>]+>", ""));
        assertEquals(1, count(text, "Rellekka"));
    }

    @Test
    public void severalPlacesUseACountHeaderAndTheFullList() {
        UiStyle style = UiStyle.from(12, FontStyleChoice.REGULAR, FontFamilyChoice.SANS_SERIF);
        List<String> places = Arrays.asList(
            "Lovakengj",
            "Ice Mountain",
            "Lovakengj (uk=1 buildings)",
            "Falador Mining Guild (uk=1)",
            "Black Knights' Base (jail \u2013 jail key required)");
        JComponent block = LocationLines.create(style, new SectionState(), places);
        assertTrue(block instanceof CollapsibleSection);
        CollapsibleSection section = (CollapsibleSection) block;
        assertEquals("Locations (5)", section.getTitle());
        assertFalse(section.getTitle().contains("Lovakengj"));
        assertFalse(section.getTitle().contains("Ice"));
        List<String> rows = new ArrayList<>();
        for (Component child : section.body().getComponents()) {
            if (child instanceof JLabel) {
                rows.add(((JLabel) child).getText().replaceAll("<[^>]+>", ""));
            }
        }
        assertEquals(Arrays.asList(
            "\u00b7 Lovakengj",
            "\u00b7 Ice Mountain",
            "\u00b7 Lovakengj (buildings)",
            "\u00b7 Falador Mining Guild",
            "\u00b7 Black Knights' Base (jail - jail key required)"
        ), rows);
    }

    private static String visible(Component component) {
        if (component instanceof JLabel && ((JLabel) component).getText() != null) {
            return ((JLabel) component).getText();
        }
        if (component instanceof Container) {
            StringBuilder builder = new StringBuilder();
            for (Component child : ((Container) component).getComponents()) {
                builder.append(visible(child));
            }
            return builder.toString();
        }
        return "";
    }

    private static int count(String text, String piece) {
        int found = 0;
        int from = 0;
        while (from >= 0) {
            from = text.indexOf(piece, from);
            if (from < 0) {
                break;
            }
            found++;
            from += piece.length();
        }
        return found;
    }
}
