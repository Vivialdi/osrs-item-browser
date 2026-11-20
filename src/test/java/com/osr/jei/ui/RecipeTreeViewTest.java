package com.osr.jei.ui;

import com.osr.jei.crafting.BuildExpander;
import com.osr.jei.model.RecipeInfo;
import com.osr.jei.model.RecipeIngredient;
import com.osr.jei.owned.OwnedText;
import com.osr.jei.service.ItemDetails;
import org.junit.Test;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Font;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RecipeTreeViewTest {

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
            assertTrue(text.contains("Expand tree"));
            assertTrue(text.contains("Rune bar"));
            assertTrue(text.contains("Smithing"));
            assertFalse(text.contains("have "));
            assertFalse(text.contains("still needed"));
        });
        assertEquals(null, opened.get());
        assertEquals(null, toggled.get());
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
            assertFalse(knownText.contains("still needed 3+"));
            assertFalse(knownText.contains("bank not opened"));

            JPanel unknown = new JPanel();
            unknown.setSize(180, 400);
            RecipeTreeView.fill(unknown, style, tree, true, idleActions(), bankClosed);
            String unknownText = collect(unknown);
            assertTrue(unknownText.contains("have 2+ / need 5"));
            assertTrue(unknownText.contains("still needed 3+"));
            assertTrue(unknownText.contains("bank not opened"));
            assertTrue(unknownText.contains(OwnedText.UNKNOWN_COLOR));
        });
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
