package com.osr.jei.ui;

/**
 * Chevron and hint on a monster row in the drop list.
 * The hint is only on a collapsed row, so an open row stays one line shorter.
 */
public final class DropRowMarks {

    public static final String HINT = "Click for locations";
    public static final String CHEVRON_COLOR = "#64b4ff";

    private DropRowMarks() {
    }

    public static String chevron(boolean open) {
        return open ? "\u25be" : "\u25b8";
    }

    /** {@code name} and {@code detail} are already escaped. */
    public static String html(String name, String detail, boolean open) {
        StringBuilder html = new StringBuilder();
        html.append("<b>").append(name == null ? "" : name).append("</b>");
        html.append("<br><font color='#999999'>").append(detail == null ? "" : detail).append("</font>");
        if (!open) {
            html.append("<br><font color='#888888'>").append(HINT).append("</font>");
        }
        return html.toString();
    }
}
