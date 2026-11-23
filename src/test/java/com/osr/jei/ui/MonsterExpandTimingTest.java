package com.osr.jei.ui;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import org.junit.Test;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.Dimension;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Headless stand-in for Mathew's pane: an open item, a long Dropped-by list,
 * and one monster's locations arriving after the wiki call. The wiki time is
 * not included. This measures the EDT work.
 */
public class MonsterExpandTimingTest {

    private static final int DROPS = 40;
    private static final int PLACES = 8;

    @Test
    public void oneRowUpdateIsFasterThanRebuildingEveryDrop() throws Exception {
        long[] rebuild = new long[1];
        long[] oneRow = new long[1];
        SwingUtilities.invokeAndWait(() -> {
            UiStyle style = UiStyle.from(12, FontStyleChoice.REGULAR, FontFamilyChoice.SANS_SERIF);
            ScrollWidthPanel full = pane(style);
            JScrollPane scroll = scrolled(full);
            scroll.validate();
            long start = System.nanoTime();
            full.removeAll();
            for (int i = 0; i < DROPS; i++) {
                full.add(dropRow(style, "Monster " + i, i == 3));
            }
            scroll.validate();
            full.paint(new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB).createGraphics());
            rebuild[0] = (System.nanoTime() - start) / 1_000_000L;

            ScrollWidthPanel incremental = new ScrollWidthPanel();
            incremental.setOpaque(true);
            for (int i = 0; i < DROPS; i++) {
                incremental.add(dropRow(style, "Monster " + i, false));
            }
            JScrollPane second = scrolled(incremental);
            second.validate();
            ScrollWidthPanel block = (ScrollWidthPanel) incremental.getComponent(0);
            long again = System.nanoTime();
            block.add(locations(style));
            block.revalidate();
            second.validate();
            oneRow[0] = (System.nanoTime() - again) / 1_000_000L;
        });
        System.out.println("monster EDT rebuild-all " + rebuild[0] + " ms; one-row " + oneRow[0] + " ms");
        assertTrue("rebuilding every drop row took " + rebuild[0] + " ms", rebuild[0] < 2_000L);
        assertTrue("updating one monster row took " + oneRow[0] + " ms", oneRow[0] < 250L);
        assertTrue("one-row update should beat a full rebuild (" + oneRow[0] + " vs " + rebuild[0] + ")",
            oneRow[0] * 2 < rebuild[0] || oneRow[0] < 40L);
    }

    @Test
    public void baseMaterialFooterShowsHaveNeedWhenNothingIsShort() throws Exception {
        com.osr.jei.model.RecipeInfo recipe = new com.osr.jei.model.RecipeInfo(
            "Runecraft", 1, "Air altar", 5,
            java.util.Collections.singletonList(new com.osr.jei.model.RecipeIngredient("Rune essence", 1)),
            1, "");
        com.osr.jei.service.ItemDetails root = new com.osr.jei.service.ItemDetails(
            java.util.Collections.singletonList(recipe),
            java.util.Optional.empty(),
            0,
            java.util.Collections.emptyList(),
            java.util.Collections.emptyList(),
            java.util.Collections.emptyList());
        com.osr.jei.crafting.BuildExpander.Result tree = com.osr.jei.crafting.BuildExpander.expandSelected(
            "Air rune", 556, root, new com.osr.jei.crafting.RecipePages() {
                @Override
                public com.osr.jei.service.ItemDetails cached(String itemName) {
                    return null;
                }

                @Override
                public com.osr.jei.service.ItemDetails load(String itemName) {
                    throw new AssertionError("base materials must not fetch");
                }
            }, java.util.Collections.emptySet());
        assertFalse(tree.recipes.get(0).ingredients.get(0).showExpander);
        assertTrue(tree.recipes.get(0).baseMaterials.containsKey("Rune essence"));
        SwingUtilities.invokeAndWait(() -> {
            javax.swing.JPanel body = new javax.swing.JPanel();
            RecipeTreeView.fill(body, UiStyle.from(12, FontStyleChoice.REGULAR, FontFamilyChoice.SANS_SERIF),
                tree, false, idle(), counts(93L, true), null, false);
            String text = collect(body);
            assertTrue(text.contains("have 93 / need 1"));
            assertTrue(text.contains(com.osr.jei.owned.OwnedText.OK_COLOR));
            assertFalse(text.contains("still needed 0"));
            assertFalse(text.contains("\u25b8"));
        });
    }

    @Test
    public void aStaleMonsterGenerationIsIgnored() {
        assertFalse(com.osr.jei.JEIPanel.acceptMonsterResult(1, 2, false));
        assertFalse(com.osr.jei.JEIPanel.acceptMonsterResult(3, 3, true));
        assertTrue(com.osr.jei.JEIPanel.acceptMonsterResult(4, 4, false));
    }

    private static ScrollWidthPanel pane(UiStyle style) {
        ScrollWidthPanel panel = new ScrollWidthPanel();
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setOpaque(true);
        for (int i = 0; i < 12; i++) {
            WrappingLabel shop = new WrappingLabel();
            style.apply(shop, "small");
            shop.setHtml("Shop " + i + " (5,000)  4 gp");
            panel.add(shop);
        }
        return panel;
    }

    private static ScrollWidthPanel dropRow(UiStyle style, String name, boolean open) {
        ScrollWidthPanel block = new ScrollWidthPanel();
        block.setOpaque(false);
        WrappingLabel label = new WrappingLabel();
        style.apply(label, "small");
        label.setHtml("<b>" + name + "</b><br><font color='#999999'>Lv. 9  ·  1/128</font>");
        block.add(label);
        if (open) {
            block.add(locations(style));
        }
        return block;
    }

    private static ScrollWidthPanel locations(UiStyle style) {
        ScrollWidthPanel box = new ScrollWidthPanel();
        box.setOpaque(false);
        for (int i = 0; i < PLACES; i++) {
            WrappingLabel line = new WrappingLabel();
            style.apply(line, "small");
            line.setHtml("· Ourania Cave passage " + i);
            box.add(line);
        }
        return box;
    }

    private static JScrollPane scrolled(ScrollWidthPanel panel) {
        JScrollPane scroll = new JScrollPane(panel);
        scroll.setSize(PluginPanel.PANEL_WIDTH, 600);
        scroll.getViewport().setSize(PluginPanel.PANEL_WIDTH - 4, 600);
        return scroll;
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
                return have;
            }
        };
    }

    private static RecipeTreeView.Actions idle() {
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
            public void icon(javax.swing.JLabel label, String itemName) {
            }
        };
    }

    private static String collect(java.awt.Component component) {
        StringBuilder text = new StringBuilder();
        if (component instanceof javax.swing.JLabel) {
            String value = ((javax.swing.JLabel) component).getText();
            if (value != null) {
                text.append(value);
            }
        }
        if (component instanceof java.awt.Container) {
            for (java.awt.Component child : ((java.awt.Container) component).getComponents()) {
                text.append(collect(child));
            }
        }
        return text.toString();
    }
}
