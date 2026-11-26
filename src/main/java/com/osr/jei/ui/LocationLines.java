package com.osr.jei.ui;

import com.osr.jei.wiki.LocationNames;

import javax.swing.JComponent;
import java.awt.Color;
import java.util.List;

/**
 * Monster and item location blocks. One place is a single line. Several places
 * are a count header and the full list, with no place name repeated in the header.
 */
public final class LocationLines {

    private static final Color ROW = new Color(200, 200, 200);

    private LocationLines() {
    }

    public static JComponent create(UiStyle style, SectionState state, List<String> places) {
        List<String> shown = LocationNames.distinct(places);
        if (shown.size() == 1) {
            WrappingLabel line = label(style, SectionSummaries.locations(shown), "bodyBold");
            line.setForeground(Color.WHITE);
            return line;
        }
        CollapsibleSection section = new CollapsibleSection(
            SectionState.LOCATIONS, state == null ? new SectionState() : state, style);
        section.setTitle(SectionSummaries.locations(shown));
        for (String place : shown) {
            WrappingLabel row = label(style, "\u00b7 " + place, "small");
            row.setForeground(ROW);
            row.setToolTipText(place);
            section.body().add(row);
        }
        return section;
    }

    private static WrappingLabel label(UiStyle style, String text, String role) {
        WrappingLabel label = new WrappingLabel();
        if (style != null) {
            style.apply(label, role);
        }
        label.setHtml(escape(text));
        label.setAlignmentX(WrappingLabel.LEFT_ALIGNMENT);
        return label;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
