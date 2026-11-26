package com.osr.jei.ui;

import net.runelite.client.ui.FontManager;

import javax.swing.JComponent;
import javax.swing.UIManager;
import java.awt.Font;

/**
 * Fonts for the sidebar. The default family is RuneLite's UI font.
 * Every label reads its size from here instead of calling {@code deriveFont} ad hoc.
 */
public final class UiStyle {

    public static final String ROLE = "jei.font";
    public static final String WRAP = "jei.wrap";

    public final Font title;
    public final Font body;
    public final Font bodyBold;
    public final Font small;
    public final Font price;
    public final Font button;
    public final boolean wrap;

    public UiStyle(Font title, Font body, Font bodyBold, Font small, Font price, Font button) {
        this(title, body, bodyBold, small, price, button, true);
    }

    public UiStyle(Font title, Font body, Font bodyBold, Font small, Font price, Font button, boolean wrap) {
        this.title = title;
        this.body = body;
        this.bodyBold = bodyBold;
        this.small = small;
        this.price = price;
        this.button = button;
        this.wrap = wrap;
    }

    public UiStyle withWrap(boolean wrap) {
        return new UiStyle(title, body, bodyBold, small, price, button, wrap);
    }

    public static UiStyle from(int fontSize, FontStyleChoice styleChoice, FontFamilyChoice familyChoice) {
        int size = Math.max(10, Math.min(22, fontSize));
        int style = styleChoice == FontStyleChoice.BOLD ? Font.BOLD : Font.PLAIN;
        Font base = family(familyChoice, size, style);
        return new UiStyle(
            base.deriveFont(Font.BOLD, size + 1f),
            base.deriveFont(style, (float) size),
            base.deriveFont(Font.BOLD, (float) size),
            base.deriveFont(style, Math.max(9f, size - 1f)),
            base.deriveFont(Font.BOLD, (float) size),
            base.deriveFont(style, (float) size)
        );
    }

    public Font forRole(String role) {
        if (role == null) {
            return body;
        }
        switch (role) {
            case "title":
                return title;
            case "bodyBold":
                return bodyBold;
            case "small":
                return small;
            case "price":
                return price;
            case "button":
                return button;
            case "body":
            default:
                return body;
        }
    }

    public void apply(JComponent component, String role) {
        component.putClientProperty(ROLE, role);
        component.putClientProperty(WRAP, wrap);
        component.setFont(forRole(role));
    }

    public void applyTree(java.awt.Component root) {
        if (root instanceof JComponent) {
            JComponent component = (JComponent) root;
            Object role = component.getClientProperty(ROLE);
            if (role instanceof String) {
                component.putClientProperty(WRAP, wrap);
                root.setFont(forRole((String) role));
            }
        }
        if (root instanceof java.awt.Container) {
            for (java.awt.Component child : ((java.awt.Container) root).getComponents()) {
                applyTree(child);
            }
        }
    }

    private static Font family(FontFamilyChoice choice, int size, int style) {
        if (choice == null || choice == FontFamilyChoice.DEFAULT) {
            Font ui = FontManager.getDefaultFont();
            if (ui == null) {
                ui = UIManager.getFont("Label.font");
            }
            if (ui == null) {
                return new Font(Font.SANS_SERIF, style, size);
            }
            return ui.deriveFont(style, (float) size);
        }
        String name;
        switch (choice) {
            case SERIF:
                name = Font.SERIF;
                break;
            case MONOSPACED:
                name = Font.MONOSPACED;
                break;
            case SANS_SERIF:
            default:
                name = Font.SANS_SERIF;
                break;
        }
        return new Font(name, style, size);
    }
}
