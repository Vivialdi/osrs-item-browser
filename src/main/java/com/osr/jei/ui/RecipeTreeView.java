package com.osr.jei.ui;

import com.osr.jei.crafting.BuildExpander;
import com.osr.jei.crafting.RecipeText;
import com.osr.jei.model.RecipeInfo;
import com.osr.jei.owned.OwnedText;
import com.osr.jei.requirements.Mark;
import com.osr.jei.requirements.RequirementJudge;
import net.runelite.client.ui.ColorScheme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Map;

/**
 * Crafting tree for the detail pane. Rows wrap to the sidebar and use {@link UiStyle}.
 * The expand control and the item name are separate clicks.
 */
public final class RecipeTreeView {

    private static final Color LINK = new Color(100, 180, 255);
    private static final Color MUTED = new Color(150, 150, 150);
    private static final Color GOOD = new Color(150, 220, 150);
    private static final Color UNMET = new Color(130, 130, 130);

    public interface Actions {
        void toggle(String path, String itemName);

        void openItem(String itemName);

        boolean canOpen(String itemName);

        void expandAll();

        void collapseAll();

        void icon(JLabel label, String itemName);
    }

    /**
     * Owned counts for the open item. {@link #have(String)} is null when the
     * name is not in the catalogue, so the row must not claim zero.
     */
    public interface Counts {
        boolean show();

        boolean inventoryKnown();

        boolean bankKnown();

        Long have(String itemName);
    }

    /** Requirement judgment for recipe rows. Null means show the requirement with no marker. */
    public interface Marks {
        Mark recipe(RecipeInfo recipe);
    }

    private RecipeTreeView() {
    }

    public static void fill(JPanel body, UiStyle style, BuildExpander.Result result, boolean anyOpen, Actions actions) {
        fill(body, style, result, anyOpen, actions, null);
    }

    public static void fill(
        JPanel body,
        UiStyle style,
        BuildExpander.Result result,
        boolean anyOpen,
        Actions actions,
        Counts counts
    ) {
        fill(body, style, result, anyOpen, actions, counts, null);
    }

    public static void fill(
        JPanel body,
        UiStyle style,
        BuildExpander.Result result,
        boolean anyOpen,
        Actions actions,
        Counts counts,
        Marks marks
    ) {
        body.removeAll();
        boolean ingredients = hasIngredients(result);
        JLabel header = label(style, ingredients
            ? "Crafting / Build path"
            : (result.recipes.size() == 1 ? "Crafting recipe" : "Crafting recipes"), "bodyBold", Color.WHITE);
        body.add(header);
        if (ingredients) {
            JButton expand = button(style, "Expand tree", "button");
            expand.setForeground(LINK);
            expand.addActionListener(e -> actions.expandAll());
            body.add(expand);
            if (anyOpen) {
                JButton collapse = button(style, "Collapse tree", "button");
                collapse.setForeground(LINK);
                collapse.addActionListener(e -> actions.collapseAll());
                body.add(collapse);
            }
        }
        body.add(Box.createRigidArea(new Dimension(0, 3)));
        for (int i = 0; i < result.recipes.size(); i++) {
            if (i > 0) {
                body.add(Box.createRigidArea(new Dimension(0, 8)));
            }
            BuildExpander.RecipeBranch branch = result.recipes.get(i);
            Mark mark = markOf(marks, branch.recipe);
            String summary = RecipeText.summary(branch.recipe);
            if (!summary.isEmpty()) {
                body.add(html(style, RequirementJudge.htmlPrefix(mark) + escape(summary), "small", mark == Mark.UNMET ? UNMET : GOOD));
            }
            for (BuildExpander.BuildNode ingredient : branch.ingredients) {
                addNode(body, style, ingredient, actions, counts, marks, mark == Mark.UNMET);
            }
            if (!branch.baseMaterials.isEmpty()) {
                body.add(Box.createRigidArea(new Dimension(0, 4)));
                String materials = "Base materials";
                if (counts != null && counts.show() && counts.inventoryKnown() && !counts.bankKnown()) {
                    materials = "Base materials \u00b7 bank not opened";
                }
                body.add(label(style, materials, "bodyBold", ColorScheme.LIGHT_GRAY_COLOR));
                for (Map.Entry<String, Long> material : branch.baseMaterials.entrySet()) {
                    String line = "\u00b7 " + escape(quantityText(material.getKey(), material.getValue()));
                    line = line + ownedSuffix(counts, material.getKey(), material.getValue(), true);
                    body.add(html(style, line, "small", MUTED));
                }
            }
        }
    }

    private static boolean hasIngredients(BuildExpander.Result result) {
        for (BuildExpander.RecipeBranch branch : result.recipes) {
            if (!branch.ingredients.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static Mark markOf(Marks marks, RecipeInfo recipe) {
        if (marks == null || recipe == null) {
            return Mark.NEUTRAL;
        }
        Mark mark = marks.recipe(recipe);
        return mark == null ? Mark.NEUTRAL : mark;
    }

    private static void addNode(
        JPanel parent,
        UiStyle style,
        BuildExpander.BuildNode node,
        Actions actions,
        Counts counts,
        Marks marks,
        boolean parentUnmet
    ) {
        JPanel block = column();
        int inset = Math.min(Math.max(0, node.depth - 1), 6) * 8;
        if (inset > 0) {
            block.setBorder(BorderFactory.createEmptyBorder(0, inset, 0, 0));
        }
        block.add(row(style, node, actions, counts, parentUnmet));
        Mark childMark = Mark.NEUTRAL;
        if (node.expanded && node.recipe != null) {
            childMark = markOf(marks, node.recipe);
            String summary = RecipeText.summary(node.recipe);
            if (!summary.isEmpty()) {
                block.add(html(style, RequirementJudge.htmlPrefix(childMark) + escape(summary), "small", childMark == Mark.UNMET ? UNMET : GOOD));
            }
        }
        if (node.loading) {
            block.add(label(style, "Loading recipe\u2026", "small", MUTED));
        }
        if (node.otherRecipes > 0 && node.expanded) {
            String extra = node.otherRecipes == 1
                ? "1 other method on this page"
                : node.otherRecipes + " other methods on this page";
            block.add(label(style, extra, "small", MUTED));
        }
        boolean unmetHere = parentUnmet || childMark == Mark.UNMET;
        for (BuildExpander.BuildNode child : node.children) {
            addNode(block, style, child, actions, counts, marks, unmetHere);
        }
        parent.add(block);
    }

    private static JPanel row(UiStyle style, BuildExpander.BuildNode node, Actions actions, Counts counts, boolean unmet) {
        JPanel row = new JPanel(new BorderLayout(4, 0)) {
            @Override
            public Dimension getMaximumSize() {
                Dimension preferred = getPreferredSize();
                return new Dimension(Integer.MAX_VALUE, preferred.height);
            }
        };
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setAlignmentX(0f);
        boolean showToggle = !node.cycle && !node.capped && !(node.expanded && !node.loading && node.children.isEmpty() && node.recipe == null);
        if (showToggle) {
            JButton toggle = button(style, node.expanded ? "\u25be" : "\u25b8", "small");
            toggle.setForeground(LINK);
            toggle.setMargin(new java.awt.Insets(0, 0, 0, 0));
            String path = node.path;
            String itemName = node.name;
            toggle.addActionListener(e -> actions.toggle(path, itemName));
            toggle.setPreferredSize(new Dimension(18, 18));
            row.add(toggle, BorderLayout.WEST);
        }
        JLabel icon = new JLabel();
        icon.setPreferredSize(new Dimension(22, 22));
        actions.icon(icon, node.name);
        boolean open = actions.canOpen(node.name);
        String text = quantityText(node.name, node.quantity);
        if (node.cycle) {
            text = text + " (loop)";
        }
        String htmlText = escape(text) + ownedSuffix(counts, node.name, node.quantity, false);
        Color nameColor = unmet ? UNMET : (open ? LINK : ColorScheme.LIGHT_GRAY_COLOR);
        WrappingLabel name = html(style, htmlText, "small", nameColor);
        if (open) {
            String itemName = node.name;
            MouseAdapter opener = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    actions.openItem(itemName);
                }
            };
            name.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            icon.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            name.addMouseListener(opener);
            icon.addMouseListener(opener);
        }
        JPanel center = new JPanel(new BorderLayout(4, 0));
        center.setOpaque(false);
        center.setAlignmentX(0f);
        center.add(icon, BorderLayout.WEST);
        center.add(name, BorderLayout.CENTER);
        row.add(center, BorderLayout.CENTER);
        return row;
    }

    private static String ownedSuffix(Counts counts, String itemName, long need, boolean baseMaterial) {
        if (counts == null || !counts.show() || !counts.inventoryKnown()) {
            return "";
        }
        Long have = counts.have(itemName);
        if (have == null) {
            return "";
        }
        String fragment = baseMaterial
            ? OwnedText.stillNeededHtml(have, need, counts.bankKnown())
            : OwnedText.haveNeedHtml(have, need, counts.bankKnown());
        return " \u00b7 " + fragment;
    }

    static String quantityText(String name, long quantity) {
        if (quantity == 1) {
            return name;
        }
        return name + " \u00d7 " + String.format("%,d", quantity);
    }

    private static JPanel column() {
        JPanel panel = new JPanel() {
            @Override
            public Dimension getMaximumSize() {
                Dimension preferred = getPreferredSize();
                return new Dimension(Integer.MAX_VALUE, preferred.height);
            }
        };
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setAlignmentX(0f);
        return panel;
    }

    private static JLabel label(UiStyle style, String text, String role, Color color) {
        JLabel label = new JLabel(text);
        style.apply(label, role);
        label.setForeground(color);
        label.setAlignmentX(0f);
        return label;
    }

    private static WrappingLabel html(UiStyle style, String inner, String role, Color color) {
        WrappingLabel label = new WrappingLabel();
        style.apply(label, role);
        label.setForeground(color);
        label.setHtml(inner);
        label.setAlignmentX(0f);
        return label;
    }

    private static JButton button(UiStyle style, String text, String role) {
        JButton button = new JButton(text);
        style.apply(button, role);
        button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        button.setOpaque(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setAlignmentX(0f);
        button.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        return button;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
