package com.osr.jei.ui;

import com.osr.jei.crafting.BuildExpander;
import com.osr.jei.crafting.RecipePages;
import com.osr.jei.model.RecipeInfo;
import com.osr.jei.model.RecipeIngredient;
import com.osr.jei.service.ItemDetails;
import net.runelite.client.ui.ColorScheme;
import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Headless Air rune crafting section at the narrow sidebar width. */
public class AirRuneCraftRenderTest {

    @Test
    public void paintAirRuneCrafting() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                paint("air-rune-craft-225.png");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    static void paint(String name) throws Exception {
        RecipeInfo rune = recipe(1, "Rune essence");
        RecipeInfo pure = recipe(1, "Pure essence");
        RecipeInfo daeyalt = recipe(14, "Daeyalt essence");
        RecipeInfo guardian = recipe(27, "Guardian essence");
        RecipeInfo warped = new RecipeInfo(
            "Runecraft", 60, "Air Altar", 9,
            Arrays.asList(new RecipeIngredient("Pure essence", 1), new RecipeIngredient("Warped extract", 1)),
            253, "");
        ItemDetails root = new ItemDetails(
            Arrays.asList(rune, pure, daeyalt, guardian, warped),
            Optional.empty(), 0,
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        BuildExpander.Result tree = BuildExpander.expandSelected(
            "Air rune", 556, root, new RecipePages() {
                @Override
                public ItemDetails cached(String itemName) {
                    return null;
                }

                @Override
                public ItemDetails load(String itemName) {
                    return null;
                }
            }, Collections.emptySet());
        ScrollWidthPanel detail = new ScrollWidthPanel();
        detail.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        detail.setOpaque(true);
        detail.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        RecipeTreeView.fill(detail, UiStyle.from(12, FontStyleChoice.REGULAR, FontFamilyChoice.SANS_SERIF),
            tree, false, idle(), counts(), null, false);
        String text = collect(detail);
        assertTrue(!text.contains("Expand tree"));
        assertTrue(!text.contains("Collapse tree"));
        int summaries = count(text, "Base materials");
        assertTrue("expected one base-materials block, saw " + summaries, summaries == 1);
        assertTrue(text.contains("Base materials (bank not checked)"));
        assertTrue(text.contains("Rune essence"));
        assertTrue(!text.contains("still needed"));
        JScrollPane scroll = new JScrollPane(detail);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setSize(225, 720);
        scroll.doLayout();
        int width = Math.max(1, scroll.getViewport().getWidth());
        detail.setSize(width, Math.max(40, detail.getPreferredSize().height));
        layout(detail);
        detail.setSize(width, Math.max(40, detail.getPreferredSize().height));
        layout(detail);
        assertNoClip(detail);
        assertFalse(rowHasArrow(detail, "Rune essence"));
        assertFalse(rowHasArrow(detail, "Pure essence"));
        assertFalse(rowHasArrow(detail, "Guardian essence"));
        assertTrue(rowHasArrow(detail, "Warped extract"));
        BufferedImage image = new BufferedImage(225, Math.max(detail.getHeight(), 40), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(ColorScheme.DARKER_GRAY_COLOR);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        detail.paint(graphics);
        graphics.dispose();
        File artifacts = new File("/opt/cursor/artifacts");
        if (artifacts.isDirectory() || artifacts.mkdirs()) {
            assertTrue(ImageIO.write(image, "png", new File(artifacts, name)));
            assertTrue(ImageIO.write(image, "png", new File(artifacts, "air-rune-craft-after-225.png")));
        }
    }

    private static RecipeInfo recipe(int level, String material) {
        return new RecipeInfo(
            "Runecraft", level, "Air Altar", 5,
            Collections.singletonList(new RecipeIngredient(material, 1)),
            1, "");
    }

    private static RecipeTreeView.Counts counts() {
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
                return false;
            }

            @Override
            public Long have(String itemName) {
                return "Rune essence".equals(itemName) ? Long.valueOf(93) : Long.valueOf(0);
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

    private static void assertNoClip(java.awt.Component root) {
        assertNoClip(root, root);
    }

    private static void assertNoClip(java.awt.Component root, java.awt.Component component) {
        if (component != root && component.isVisible() && component.getWidth() > 0 && component.getParent() != null) {
            Point origin = SwingUtilities.convertPoint(component.getParent(), component.getX(), component.getY(), root);
            int right = origin.x + component.getWidth();
            assertTrue("clipped at " + right + " of " + root.getWidth() + " " + textOf(component),
                right <= root.getWidth() + 1);
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                assertNoClip(root, child);
            }
        }
    }

    private static boolean rowHasArrow(Component component, String name) {
        JLabel label = textLabel(component, name);
        Component cursor = label;
        while (cursor != null && cursor.getParent() != null) {
            for (Component sibling : cursor.getParent().getComponents()) {
                if (sibling instanceof javax.swing.JComponent
                    && "tree-toggle".equals(((javax.swing.JComponent) sibling).getClientProperty("jei.role"))) {
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
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
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
            if (text != null && text.contains(snippet)) {
                return (JLabel) component;
            }
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                JLabel found = textLabel(child, snippet);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static String textOf(Component component) {
        if (component instanceof JLabel) {
            String text = ((JLabel) component).getText();
            return text == null ? "" : text;
        }
        return "";
    }

    private static String collect(Component component) {
        StringBuilder text = new StringBuilder();
        if (component instanceof JLabel) {
            String value = ((JLabel) component).getText();
            if (value != null) {
                text.append(value);
            }
        } else if (component instanceof JButton) {
            String value = ((JButton) component).getText();
            if (value != null) {
                text.append(value);
            }
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                text.append(collect(child));
            }
        }
        return text.toString();
    }

    private static int count(String text, String snippet) {
        int n = 0;
        int from = 0;
        while (true) {
            int at = text.indexOf(snippet, from);
            if (at < 0) {
                return n;
            }
            n++;
            from = at + snippet.length();
        }
    }

    private static void layout(java.awt.Component component) {
        if (component instanceof java.awt.Container) {
            java.awt.Container container = (java.awt.Container) component;
            container.doLayout();
            for (java.awt.Component child : container.getComponents()) {
                layout(child);
            }
        }
    }
}
