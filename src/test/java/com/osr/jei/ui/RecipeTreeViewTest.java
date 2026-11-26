package com.osr.jei.ui;

import com.osr.jei.crafting.BuildExpander;
import com.osr.jei.model.RecipeInfo;
import com.osr.jei.model.RecipeIngredient;
import com.osr.jei.owned.OwnedText;
import com.osr.jei.requirements.Mark;
import com.osr.jei.requirements.RequirementJudge;
import com.osr.jei.service.ItemDetails;
import org.junit.Test;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Font;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RecipeTreeViewTest {

    @Test
    public void loopedIngredientIsNamedOnTheRecipeLine() throws Exception {
        RecipeInfo poison = new RecipeInfo(
            "", 0, "", 0,
            Arrays.asList(new RecipeIngredient("Iron dagger", 1), new RecipeIngredient("Weapon poison(+)", 1)),
            1, "");
        ItemDetails dagger = new ItemDetails(
            Collections.singletonList(poison),
            java.util.Optional.empty(),
            Double.NaN,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList());
        BuildExpander.Result tree = BuildExpander.expandSelected(
            "Iron dagger", null, dagger, new NoPages(), Collections.emptySet());
        assertTrue(tree.recipes.get(0).ingredients.get(0).cycle);
        assertFalse(tree.recipes.get(0).ingredients.get(1).cycle);

        SwingUtilities.invokeAndWait(() -> {
            JPanel body = new JPanel();
            RecipeTreeView.fill(body, UiStyle.from(12, FontStyleChoice.REGULAR, FontFamilyChoice.SANS_SERIF),
                tree, false, idleActions(), null, null, false);
            String text = collect(body);
            assertFalse(text.contains("(loop)"));
            assertTrue(text.contains("Iron dagger"));
            assertTrue(text.contains("Weapon poison(+)"));
            assertEquals(text.indexOf("Iron dagger"), text.lastIndexOf("Iron dagger"));
        });
    }

    @Test
    public void treeUsesTheConfiguredFontAndKeepsToggleSeparateFromOpen() throws Exception {
        RecipeInfo recipe = new RecipeInfo(
            "Smithing", 99, "Anvil", 375,
            Collections.singletonList(new RecipeIngredient("Rune bar", 5)),
            1, "");
        ItemDetails root = new ItemDetails(
            Collections.singletonList(recipe),
            java.util.Optional.empty(),
            Double.NaN,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList());
        BuildExpander.Result tree = BuildExpander.expandSelected(
            "Rune platebody", 1127, root, new NoPages(), Collections.emptySet());
        UiStyle style = UiStyle.from(16, FontStyleChoice.BOLD, FontFamilyChoice.SANS_SERIF);
        AtomicReference<String> opened = new AtomicReference<>();
        AtomicReference<String> toggled = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            JPanel body = new JPanel();
            body.setSize(180, 400);
            RecipeTreeView.fill(body, style, tree, false, new RecipeTreeView.Actions() {
                @Override
                public void toggle(String path, String itemName) {
                    toggled.set(itemName);
                }

                @Override
                public void openItem(String itemName) {
                    opened.set(itemName);
                }

                @Override
                public boolean canOpen(String itemName) {
                    return "Rune bar".equals(itemName);
                }

                @Override
                public void expandAll() {
                }

                @Override
                public void collapseAll() {
                }

                @Override
                public void icon(JLabel label, String itemName) {
                }
            });
            assertTrue(body.getComponentCount() > 2);
            assertEquals(Font.SANS_SERIF, body.getComponent(0).getFont().getName());
            assertEquals(Font.BOLD, body.getComponent(0).getFont().getStyle());
            assertEquals(16, body.getComponent(0).getFont().getSize());
            String text = collect(body);
            assertTrue(text.contains("Crafting / Build path"));
            assertFalse(text.contains("Expand tree"));
            assertFalse(text.contains("Collapse tree"));
            assertTrue(text.contains("Rune bar"));
            assertTrue(text.contains("Smithing"));
            assertFalse(text.contains("have "));
            assertFalse(text.contains("still needed"));
        });
        assertEquals(null, opened.get());
        assertEquals(null, toggled.get());
    }

    @Test
    public void unmetRecipeIsGreyAndLoggedOutIsNotJudged() throws Exception {
        RecipeInfo recipe = new RecipeInfo(
            "Smithing", 99, "Anvil", 375,
            Collections.singletonList(new RecipeIngredient("Rune bar", 5)),
            1, "");
        ItemDetails root = new ItemDetails(
            Collections.singletonList(recipe),
            java.util.Optional.empty(),
            Double.NaN,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList());
        BuildExpander.Result tree = BuildExpander.expandSelected(
            "Rune platebody", 1127, root, new NoPages(), Collections.emptySet());
        UiStyle style = UiStyle.from(12, FontStyleChoice.REGULAR, FontFamilyChoice.SANS_SERIF);

        SwingUtilities.invokeAndWait(() -> {
            JPanel unmet = new JPanel();
            unmet.setSize(180, 400);
            RecipeTreeView.fill(unmet, style, tree, false, idleActions(), null, ignored -> Mark.UNMET);
            String unmetText = collect(unmet);
            assertTrue(unmetText.contains("&#10007;") || unmetText.contains("\u2717"));
            assertTrue(unmetText.contains("Smithing"));
            assertTrue(hasForeground(unmet, new java.awt.Color(130, 130, 130)));

            JPanel open = new JPanel();
            open.setSize(180, 400);
            RecipeTreeView.fill(open, style, tree, false, idleActions(), null, recipeInfo -> Mark.NEUTRAL);
            String neutral = collect(open);
            assertFalse(neutral.contains(RequirementJudge.htmlPrefix(Mark.UNMET).trim()));
            assertFalse(neutral.contains("&#10007;"));
        });
    }

    @Test
    public void treeShowsHaveNeedAndStillNeeded() throws Exception {
        RecipeInfo recipe = new RecipeInfo(
            "Smithing", 99, "Anvil", 375,
            Collections.singletonList(new RecipeIngredient("Rune bar", 5)),
            1, "");
        ItemDetails root = new ItemDetails(
            Collections.singletonList(recipe),
            java.util.Optional.empty(),
            Double.NaN,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList());
        Set<String> open = new LinkedHashSet<>();
        open.add("0" + BuildExpander.SEP + "Rune bar");
        BuildExpander.Result tree = BuildExpander.expandSelected(
            "Rune platebody", 1127, root, new NoPages(), open);
        UiStyle style = UiStyle.from(12, FontStyleChoice.REGULAR, FontFamilyChoice.SANS_SERIF);
        RecipeTreeView.Counts shortBank = counts(2L, true);
        RecipeTreeView.Counts bankClosed = counts(2L, false);

        SwingUtilities.invokeAndWait(() -> {
            JPanel known = new JPanel();
            known.setSize(180, 400);
            RecipeTreeView.fill(known, style, tree, true, idleActions(), shortBank);
            String knownText = collect(known);
            assertTrue(knownText.contains("have 2 / need 5"));
            assertTrue(knownText.contains(OwnedText.SHORT_COLOR));
            assertTrue(knownText.contains("still needed 3"));
            assertFalse(knownText.contains("+"));
            assertFalse(knownText.contains("bank not checked"));

            JPanel unknown = new JPanel();
            unknown.setSize(180, 400);
            RecipeTreeView.fill(unknown, style, tree, true, idleActions(), bankClosed);
            String unknownText = collect(unknown);
            assertTrue(unknownText.contains("have 2 (bank not checked) / need 5"));
            assertTrue(unknownText.contains("Base materials (bank not checked)"));
            assertFalse(unknownText.contains("still needed 3 (bank not checked)"));
            assertFalse(unknownText.contains("0+"));
            assertFalse(unknownText.contains("2+"));
            assertFalse(unknownText.contains("3+"));
            assertTrue(tooltipContains(unknown, OwnedText.BANK_UNKNOWN_TIP));
            assertTrue(labelForeground(unknown, "bank not checked", new java.awt.Color(0xC8, 0xC8, 0xC8)));
        });
    }

    @Test
    public void leavesKeepTheArrowSlotButDoNotShowIt() throws Exception {
        RecipeInfo nested = new RecipeInfo(
            "Runecraft", 1, "Altar", 1,
            Collections.singletonList(new RecipeIngredient("Rune essence", 1)),
            1, "");
        ItemDetails craftable = new ItemDetails(
            Collections.singletonList(nested),
            java.util.Optional.empty(),
            Double.NaN,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList());
        RecipeInfo rootRecipe = new RecipeInfo(
            "Runecraft", 9, "Earth Altar", 6.5,
            Arrays.asList(
                new RecipeIngredient("Rune essence", 100),
                new RecipeIngredient("Bronze bar", 1),
                new RecipeIngredient("Earth talisman", 1)
            ),
            1, "");
        ItemDetails root = new ItemDetails(
            Collections.singletonList(rootRecipe),
            java.util.Optional.empty(),
            Double.NaN,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList());
        com.osr.jei.crafting.RecipePages pages = new com.osr.jei.crafting.RecipePages() {
            @Override
            public ItemDetails cached(String itemName) {
                if ("Rune essence".equals(itemName)) {
                    return ItemDetails.EMPTY;
                }
                if ("Earth talisman".equals(itemName)) {
                    return craftable;
                }
                return null;
            }

            @Override
            public ItemDetails load(String itemName) {
                throw new AssertionError("collapsed rows must not fetch");
            }
        };
        BuildExpander.Result tree = BuildExpander.expandSelected("Earth rune", 557, root, pages, Collections.emptySet());
        BuildExpander.BuildNode essence = tree.recipes.get(0).ingredients.get(0);
        BuildExpander.BuildNode bar = tree.recipes.get(0).ingredients.get(1);
        BuildExpander.BuildNode talisman = tree.recipes.get(0).ingredients.get(2);
        assertFalse(essence.showExpander);
        assertTrue(bar.showExpander);
        assertTrue(talisman.showExpander);

        Set<String> opened = new LinkedHashSet<>();
        opened.add(essence.path);
        BuildExpander.Result after = BuildExpander.expandSelected("Earth rune", 557, root, pages, opened);
        assertFalse(after.recipes.get(0).ingredients.get(0).showExpander);
        assertTrue(after.recipes.get(0).ingredients.get(0).children.isEmpty());

        UiStyle style = UiStyle.from(12, FontStyleChoice.REGULAR, FontFamilyChoice.SANS_SERIF);
        SwingUtilities.invokeAndWait(() -> {
            JPanel body = new JPanel();
            body.setSize(220, 400);
            RecipeTreeView.fill(body, style, tree, false, idleActions(), null, null, false);
            body.doLayout();
            layout(body);
            JLabel essenceIcon = iconInRow(body, "Rune essence");
            JLabel barIcon = iconInRow(body, "Bronze bar");
            assertTrue(essenceIcon != null && barIcon != null);
            java.awt.Point essenceAt = SwingUtilities.convertPoint(
                essenceIcon.getParent(), essenceIcon.getX(), essenceIcon.getY(), body);
            java.awt.Point barAt = SwingUtilities.convertPoint(
                barIcon.getParent(), barIcon.getX(), barIcon.getY(), body);
            assertEquals(barAt.x, essenceAt.x);
            assertFalse(rowHasArrow(body, "Rune essence"));
            assertTrue(rowHasArrow(body, "Bronze bar"));
            assertTrue(rowHasArrow(body, "Earth talisman"));
        });
    }

    private static void layout(Component component) {
        if (component instanceof java.awt.Container) {
            java.awt.Container container = (java.awt.Container) component;
            container.doLayout();
            for (Component child : container.getComponents()) {
                layout(child);
            }
        }
    }

    private static JLabel iconInRow(Component component, String name) {
        JLabel label = textLabel(component, name);
        if (label == null) {
            return null;
        }
        Component row = label;
        while (row != null && !(row instanceof JComponent && "tree-toggle".equals(((JComponent) row).getClientProperty("jei.role")))) {
            if (row.getParent() == null) {
                break;
            }
            for (Component sibling : row.getParent().getComponents()) {
                if (sibling instanceof JComponent && "tree-icon".equals(((JComponent) sibling).getClientProperty("jei.role"))) {
                    return (JLabel) sibling;
                }
            }
            row = row.getParent();
        }
        return null;
    }

    private static boolean rowHasArrow(Component component, String name) {
        JLabel label = textLabel(component, name);
        Component cursor = label;
        while (cursor != null && cursor.getParent() != null) {
            for (Component sibling : cursor.getParent().getComponents()) {
                if (sibling instanceof JComponent
                    && "tree-toggle".equals(((JComponent) sibling).getClientProperty("jei.role"))) {
                    return containsArrow(sibling);
                }
            }
            cursor = cursor.getParent();
        }
        return false;
    }

    private static boolean containsArrow(Component component) {
        if (component instanceof JButton) {
            String text = ((JButton) component).getText();
            return text != null && (text.contains("\u25b8") || text.contains("\u25be"));
        }
        if (component instanceof java.awt.Container) {
            for (Component child : ((java.awt.Container) component).getComponents()) {
                if (containsArrow(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static JLabel textLabel(Component component, String snippet) {
        if (component instanceof JLabel) {
            String text = ((JLabel) component).getText();
            if (text != null && text.contains(snippet) && !text.contains("need")) {
                return (JLabel) component;
            }
        }
        if (component instanceof java.awt.Container) {
            for (Component child : ((java.awt.Container) component).getComponents()) {
                JLabel found = textLabel(child, snippet);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static RecipeTreeView.Counts counts(long have, boolean bankKnown) {
        return new RecipeTreeView.Counts() {
            @Override
            public boolean show() {
                return true;
            }

            @Override
            public boolean inventoryKnown() {
                return true;
            }

            @Override
            public boolean bankKnown() {
                return bankKnown;
            }

            @Override
            public Long have(String itemName) {
                return "Rune bar".equals(itemName) ? have : null;
            }
        };
    }

    private static RecipeTreeView.Actions idleActions() {
        return new RecipeTreeView.Actions() {
            @Override
            public void toggle(String path, String itemName) {
            }

            @Override
            public void openItem(String itemName) {
            }

            @Override
            public boolean canOpen(String itemName) {
                return false;
            }

            @Override
            public void expandAll() {
            }

            @Override
            public void collapseAll() {
            }

            @Override
            public void icon(JLabel label, String itemName) {
            }
        };
    }

    private static boolean hasForeground(java.awt.Component component, java.awt.Color color) {
        if (component instanceof JLabel && color.equals(component.getForeground()) && ((JLabel) component).getText().contains("Rune bar")) {
            return true;
        }
        if (component instanceof java.awt.Container) {
            for (java.awt.Component child : ((java.awt.Container) component).getComponents()) {
                if (hasForeground(child, color)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean labelForeground(Component component, String snippet, java.awt.Color color) {
        if (component instanceof JLabel) {
            String text = ((JLabel) component).getText();
            if (text != null && text.contains(snippet) && color.equals(component.getForeground())) {
                return true;
            }
        }
        if (component instanceof java.awt.Container) {
            for (Component child : ((java.awt.Container) component).getComponents()) {
                if (labelForeground(child, snippet, color)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean tooltipContains(Component component, String snippet) {
        if (component instanceof JComponent) {
            String tip = ((JComponent) component).getToolTipText();
            if (tip != null && tip.contains(snippet)) {
                return true;
            }
        }
        if (component instanceof java.awt.Container) {
            for (Component child : ((java.awt.Container) component).getComponents()) {
                if (tooltipContains(child, snippet)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String collect(java.awt.Component component) {
        StringBuilder text = new StringBuilder();
        if (component instanceof JLabel) {
            text.append(((JLabel) component).getText());
        } else if (component instanceof javax.swing.AbstractButton) {
            text.append(((javax.swing.AbstractButton) component).getText());
        }
        if (component instanceof java.awt.Container) {
            for (java.awt.Component child : ((java.awt.Container) component).getComponents()) {
                text.append(collect(child));
            }
        }
        return text.toString();
    }

    private static final class NoPages implements com.osr.jei.crafting.RecipePages {
        @Override
        public ItemDetails cached(String itemName) {
            return null;
        }

        @Override
        public ItemDetails load(String itemName) {
            throw new AssertionError("view must not fetch");
        }
    }
}
