package com.osr.jei.ui;

import com.osr.jei.crafting.BuildExpander;
import com.osr.jei.ironman.TradeCopy;
import com.osr.jei.model.RecipeInfo;
import com.osr.jei.model.RecipeIngredient;
import com.osr.jei.model.ShopEntry;
import com.osr.jei.pricing.MarketQuote;
import com.osr.jei.pricing.PriceQuote;
import com.osr.jei.search.SearchCriteria;
import com.osr.jei.service.ItemDetails;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Headless render of the detail components at the default sidebar width and a wider one.
 */
public class DetailPaneLayoutTest {

    private static final Color GOLD = new Color(220, 185, 75);
    private static final Color MUTED = new Color(150, 150, 150);
    private static final Color NEUTRAL = new Color(190, 190, 190);
    private static final Color BAD = new Color(220, 100, 100);

    @Test
    public void toggleShowsTheSameBodyAndDoesNotNeedAReload() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            UiStyle style = UiStyle.from(12, FontStyleChoice.REGULAR, FontFamilyChoice.SANS_SERIF);
            SectionState state = new SectionState();
            CollapsibleSection section = new CollapsibleSection(SectionState.SHOPS, state, style);
            section.setTitle("Shop sources (3) \u00b7 cheapest 4 gp");
            JLabel row = new JLabel("Aubury's Rune Shop");
            section.body().add(row);
            AtomicInteger reloads = new AtomicInteger();
            section.setOnToggle(reloads::incrementAndGet);
            assertFalse(section.isExpanded());
            assertFalse(section.body().isVisible());
            assertTrue(section.getTitle().contains("cheapest 4 gp"));
            section.toggle();
            assertTrue(section.isExpanded());
            assertTrue(section.body().isVisible());
            assertSame(row, section.body().getComponent(0));
            assertEquals(1, reloads.get());
            section.toggle();
            assertFalse(section.body().isVisible());
            assertSame(row, section.body().getComponent(0));
            assertEquals(2, reloads.get());
        });
    }

    @Test
    public void airRuneDetailFitsTheSidebar() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                Render narrow = render(PluginPanel.PANEL_WIDTH, false);
                Render wide = render(350, false);
                Render open = render(PluginPanel.PANEL_WIDTH, true);
                assertTrue(narrow.detail.getHeight() < open.detail.getHeight());
                assertNoOverflow(narrow);
                assertNoOverflow(wide);
                assertNoOverflow(open);
                assertTrue(visibleText(narrow.detail).contains("Shop sources"));
                assertTrue(visibleText(narrow.detail).contains("cheapest"));
                assertFalse(visibleText(narrow.detail).contains("Magic Guild Store"));
                assertTrue(visibleText(open.detail).contains("Magic Guild Store"));
                assertFalse(visibleText(open.detail).contains("\u2014"));
                assertTrue(visibleText(open.detail).contains("Show on map"));
                assertFalse(visibleText(narrow.detail).contains("Instant sell"));
                assertTrue(visibleText(narrow.detail).contains("High Alch"));
                assertFalse(joinedVisibleLabels(narrow.detail).contains("Instant sell")
                    && joinedVisibleLabels(narrow.detail).contains("after tax")
                    && joinedVisibleLabels(narrow.detail).contains("Per 4h")
                    && oneLabelHasAllGeFields(narrow.detail));
                assertTrue(oneLabelHasAllGeFields(narrow.detail) == false);
                assertWrapped("Magic Guild Store", open.detail);
                assertWrapped("Air tiara", open.detail);
                write(narrow.image, "air-rune-225.png");
                write(wide.image, "air-rune-350.png");
                write(open.image, "air-rune-225-open.png");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void sidebarFillsWidthAndDropsTheEmptyResultsGap() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                Render empty = sidebar(230, false, true);
                Render noFav = sidebar(230, false, false);
                Render wideEmpty = sidebar(300, false, true);
                Render results = sidebar(230, true, true);
                Render wideResults = sidebar(300, true, false);
                assertFiltersReadable(empty.detail);
                assertFiltersReadable(noFav.detail);
                assertFiltersReadable(wideEmpty.detail);
                assertTrue(empty.detail.getHeight() < 420);
                assertTrue(wideEmpty.detail.getHeight() < 420);
                assertNoOverflow(empty);
                assertNoOverflow(results);
                assertNoOverflow(wideResults);
                IconGridPanel resultGrid = findGrid(results.detail, 14);
                assertTrue(resultGrid != null);
                int right = 0;
                for (Component child : resultGrid.getComponents()) {
                    right = Math.max(right, child.getX() + child.getWidth());
                }
                assertTrue("icons should fill the row, right=" + right + " width=" + resultGrid.getWidth(),
                    right > resultGrid.getWidth() - 40);
                write(empty.image, "sidebar-empty-230.png");
                write(noFav.image, "sidebar-empty-230-nofav.png");
                write(wideEmpty.image, "sidebar-empty-300.png");
                write(results.image, "sidebar-results-230.png");
                write(wideResults.image, "sidebar-results-300.png");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void indentedTreeNodesWrapInsteadOfClipping() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                Render tree = treePane(PluginPanel.PANEL_WIDTH);
                assertNoOverflow(tree);
                JLabel have = findText(tree.detail, "have 0+ / need 1");
                JLabel name = findText(tree.detail, "Daeyalt essence");
                assertTrue(have != null && name != null);
                assertTrue(have != name);
                Point haveAt = SwingUtilities.convertPoint(have.getParent(), have.getX(), have.getY(), tree.detail);
                Point nameAt = SwingUtilities.convertPoint(name.getParent(), name.getX(), name.getY(), tree.detail);
                assertTrue(haveAt.y > nameAt.y);
                assertTrue(nameAt.x > 24);
                write(tree.image, "air-rune-tree-225.png");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static boolean oneLabelHasAllGeFields(Component component) {
        if (component instanceof JLabel) {
            String text = ((JLabel) component).getText();
            if (text != null && text.contains("Instant sell") && text.contains("after tax") && text.contains("Per 4h")) {
                return true;
            }
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                if (oneLabelHasAllGeFields(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void assertWrapped(String snippet, Component component) {
        JLabel label = findLabel(component, snippet);
        if (label == null) {
            fail("Missing label containing " + snippet);
        }
        int line = label.getFontMetrics(label.getFont()).getHeight();
        assertTrue(snippet + " should wrap (" + label.getHeight() + " vs line " + line + ")",
            label.getHeight() > line + 2);
    }

    private static JLabel findText(Component component, String snippet) {
        if (!component.isVisible()) {
            return null;
        }
        if (component instanceof JLabel) {
            String text = ((JLabel) component).getText();
            if (text != null && text.contains(snippet)) {
                return (JLabel) component;
            }
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                JLabel found = findText(child, snippet);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static JLabel findLabel(Component component, String snippet) {
        if (!component.isVisible()) {
            return null;
        }
        if (component instanceof JLabel) {
            String text = ((JLabel) component).getText();
            String tip = ((JLabel) component).getToolTipText();
            if ((text != null && text.contains(snippet)) || (tip != null && tip.contains(snippet))) {
                return (JLabel) component;
            }
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                JLabel found = findLabel(child, snippet);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static IconGridPanel findGrid(Component component, int count) {
        if (component instanceof IconGridPanel && ((IconGridPanel) component).getComponentCount() == count) {
            return (IconGridPanel) component;
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                IconGridPanel found = findGrid(child, count);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void assertFiltersReadable(JComponent root) {
        int combos = 0;
        for (JComboBox<?> combo : findCombos(root)) {
            combos++;
            Object selected = combo.getSelectedItem();
            String text = selected == null ? "" : selected.toString();
            int textWidth = combo.getFontMetrics(combo.getFont()).stringWidth(text);
            assertTrue(text + " is clipped in a " + combo.getWidth() + "px combo",
                combo.getWidth() >= textWidth + 28);
            Point at = SwingUtilities.convertPoint(combo.getParent(), combo.getX(), combo.getY(), root);
            assertTrue("filter sits away from the left edge at x=" + at.x, at.x < 80);
            assertTrue("filter does not reach the row", at.x + combo.getWidth() > root.getWidth() - 24);
        }
        assertEquals(4, combos);
    }

    private static java.util.List<JComboBox<?>> findCombos(Component component) {
        java.util.List<JComboBox<?>> found = new ArrayList<>();
        if (component instanceof JComboBox) {
            found.add((JComboBox<?>) component);
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                found.addAll(findCombos(child));
            }
        }
        return found;
    }

    private static Render sidebar(int width, boolean results, boolean favourites) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel search = new JPanel(new java.awt.BorderLayout()) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        search.setOpaque(false);
        search.setAlignmentX(0f);
        JLabel field = new JLabel(results ? "  air rune" : "  Search");
        field.setOpaque(true);
        field.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        field.setForeground(Color.WHITE);
        field.setPreferredSize(new Dimension(40, 28));
        search.add(field, java.awt.BorderLayout.CENTER);
        panel.add(search);
        panel.add(Box.createRigidArea(new Dimension(0, 4)));
        JComboBox<SearchCriteria.Trade> trade = new JComboBox<>(SearchCriteria.Trade.values());
        JComboBox<SearchCriteria.Members> world = new JComboBox<>(SearchCriteria.Members.values());
        JComboBox<SearchCriteria.Equip> item = new JComboBox<>(SearchCriteria.Equip.values());
        JComboBox<SearchCriteria.SlotChoice> slot = new JComboBox<>(SearchCriteria.SlotChoice.values());
        JPanel filters = LabeledFilters.column(
            null,
            MUTED,
            new String[]{"Trade", "World", "Item", "Slot"},
            new JComponent[]{trade, world, item, slot}
        );
        JPanel filterRow = new JPanel(new java.awt.BorderLayout()) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        filterRow.setOpaque(false);
        filterRow.setAlignmentX(0f);
        filterRow.add(filters, java.awt.BorderLayout.CENTER);
        panel.add(filterRow);
        if (favourites) {
            panel.add(iconStrip("Favourites", 4));
        }
        panel.add(iconStrip("Recent", 8));
        if (results) {
            panel.add(Box.createRigidArea(new Dimension(0, 6)));
            panel.add(iconStrip("Results", 14));
        }
        panel.setSize(width, 20);
        panel.setSize(width, panel.getPreferredSize().height);
        layoutDeep(panel);
        panel.setSize(width, panel.getPreferredSize().height);
        layoutDeep(panel);
        BufferedImage image = new BufferedImage(width, Math.max(40, panel.getHeight()), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(ColorScheme.DARK_GRAY_COLOR);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        panel.paint(graphics);
        graphics.dispose();
        return new Render(panel, image);
    }

    private static JPanel iconStrip(String title, int count) {
        JPanel strip = new JPanel();
        strip.setLayout(new BoxLayout(strip, BoxLayout.Y_AXIS));
        strip.setOpaque(false);
        strip.setAlignmentX(0f);
        JLabel label = new JLabel(title);
        label.setForeground(MUTED);
        strip.add(label);
        IconGridPanel icons = new IconGridPanel(32, 4);
        icons.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        for (int i = 0; i < count; i++) {
            JPanel cell = new JPanel();
            cell.setBackground(new Color(80 + i * 8, 70, 40));
            cell.setPreferredSize(new Dimension(32, 32));
            icons.add(cell);
        }
        strip.add(icons);
        strip.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        return strip;
    }

    private static Render treePane(int width) {
        UiStyle style = UiStyle.from(12, FontStyleChoice.REGULAR, FontFamilyChoice.SANS_SERIF);
        RecipeInfo nested = new RecipeInfo(
            "Runecraft", 27, "Air Altar", 7.5,
            Collections.singletonList(new RecipeIngredient("Daeyalt essence", 1)),
            1, "");
        ItemDetails child = new ItemDetails(
            Collections.singletonList(nested),
            java.util.Optional.empty(),
            0,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList());
        RecipeInfo rootRecipe = new RecipeInfo(
            "Runecraft", 1, "Air tiara, Air talisman + Air Altar", 7.5,
            Collections.singletonList(new RecipeIngredient("Pure essence", 1)),
            1, "");
        ItemDetails root = new ItemDetails(
            Collections.singletonList(rootRecipe),
            java.util.Optional.empty(),
            0,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList());
        Set<String> open = new LinkedHashSet<>();
        open.add("0" + BuildExpander.SEP + "Pure essence");
        BuildExpander.Result tree = BuildExpander.expandSelected("Air rune", 556, root, new com.osr.jei.crafting.RecipePages() {
            @Override
            public ItemDetails cached(String itemName) {
                return "Pure essence".equals(itemName) ? child : null;
            }

            @Override
            public ItemDetails load(String itemName) {
                throw new AssertionError("tree view must not fetch");
            }

            @Override
            public Integer idFor(String itemName) {
                return null;
            }
        }, open);
        ScrollWidthPanel detail = new ScrollWidthPanel();
        detail.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        detail.setOpaque(true);
        detail.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        RecipeTreeView.Counts counts = new RecipeTreeView.Counts() {
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
                return "Daeyalt essence".equals(itemName) ? Long.valueOf(0) : null;
            }
        };
        RecipeTreeView.fill(detail, style, tree, true, idleActions(), counts, null, false);
        return paintPanel(detail, width);
    }

    private static Render paintPanel(JComponent detail, int width) {
        JScrollPane scroll = new JScrollPane(detail);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setSize(width, 720);
        scroll.doLayout();
        int viewWidth = Math.max(1, scroll.getViewport().getWidth());
        detail.setSize(viewWidth, Math.max(40, detail.getPreferredSize().height));
        layoutDeep(detail);
        detail.setSize(viewWidth, Math.max(40, detail.getPreferredSize().height));
        layoutDeep(detail);
        BufferedImage image = new BufferedImage(width, Math.max(detail.getHeight(), 40), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(ColorScheme.DARKER_GRAY_COLOR);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        detail.paint(graphics);
        graphics.dispose();
        return new Render(detail, image);
    }

    private static Render render(int width, boolean openLists) {
        UiStyle style = UiStyle.from(12, FontStyleChoice.REGULAR, FontFamilyChoice.SANS_SERIF);
        SectionState state = new SectionState();
        if (openLists) {
            state.setExpanded(SectionState.SHOPS, true);
            state.setExpanded(SectionState.CRAFTING, true);
        }
        ScrollWidthPanel detail = sample(style, state);
        JScrollPane scroll = new JScrollPane(detail);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setSize(width, 720);
        scroll.doLayout();
        detail.invalidate();
        Dimension preferred = detail.getPreferredSize();
        int viewWidth = Math.max(1, scroll.getViewport().getWidth());
        detail.setSize(viewWidth, Math.max(40, preferred.height));
        layoutDeep(detail);
        detail.invalidate();
        preferred = detail.getPreferredSize();
        detail.setSize(viewWidth, Math.max(40, preferred.height));
        layoutDeep(detail);
        assertTrue(detail.getWidth() <= width);
        assertFalse(scroll.getHorizontalScrollBar().isVisible());
        BufferedImage image = new BufferedImage(width, Math.max(detail.getHeight(), 40), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(ColorScheme.DARKER_GRAY_COLOR);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        detail.paint(graphics);
        graphics.dispose();
        return new Render(detail, image);
    }

    private static ScrollWidthPanel sample(UiStyle style, SectionState state) {
        ScrollWidthPanel detail = new ScrollWidthPanel();
        detail.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        detail.setOpaque(true);
        detail.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        WrappingLabel name = new WrappingLabel();
        style.apply(name, "title");
        name.setForeground(Color.WHITE);
        name.setHtml("Air rune");
        detail.add(name);

        WrappingLabel meta = new WrappingLabel();
        style.apply(meta, "small");
        meta.setForeground(MUTED);
        meta.setHtml("F2P \u00b7 0 kg");
        detail.add(meta);

        WrappingLabel owned = new WrappingLabel();
        style.apply(owned, "small");
        owned.setForeground(NEUTRAL);
        owned.setHtml("Owned: 5,776");
        detail.add(owned);
        detail.add(Box.createRigidArea(new Dimension(0, 6)));

        PriceQuote quote = new PriceQuote(6, 5, 5);
        MarketQuote market = MarketQuote.describe(false, 556, quote, 12_000, true);
        List<StatGrid.Row> rows = new ArrayList<>();
        List<StatGrid.Row> tradingRows = new ArrayList<>();
        rows.add(new StatGrid.Row("", TradeCopy.geLine(false, true, quote), GOLD));
        for (MarketQuote.Line line : market.lines) {
            StatGrid.Row row = new StatGrid.Row(line.key, line.value, line.tone < 0 ? BAD : NEUTRAL);
            if ("Limit".equals(line.key) || "Buy limit".equals(line.key)) {
                rows.add(row);
            } else if (!"Reference only".equals(line.value)) {
                tradingRows.add(row);
            }
        }
        StatGrid grid = new StatGrid(style);
        grid.setRows(rows);
        detail.add(grid);
        CollapsibleSection trading = new CollapsibleSection(SectionState.TRADING, state, style);
        trading.setTitle("Trading details");
        StatGrid tradingGrid = new StatGrid(style);
        tradingGrid.setRows(tradingRows);
        trading.body().add(tradingGrid);
        detail.add(trading);

        WrappingLabel alch = new WrappingLabel();
        style.apply(alch, "body");
        alch.setForeground(NEUTRAL);
        alch.setHtml("High Alch: 2 gp");
        detail.add(alch);

        CollapsibleSection shops = new CollapsibleSection(SectionState.SHOPS, state, style);
        shops.setTitle(SectionSummaries.shops(sampleShops()));
        for (ShopEntry shop : sampleShops()) {
            TradeCopy.ShopLine line = TradeCopy.shop(false, shop, 6);
            shops.body().add(ShopRows.row(style, shop, line, () -> { }, () -> { }));
        }
        detail.add(shops);

        CollapsibleSection crafting = new CollapsibleSection(SectionState.CRAFTING, state, style);
        List<RecipeInfo> recipes = sampleRecipes();
        crafting.setTitle(SectionSummaries.crafting(recipes));
        ItemDetails root = new ItemDetails(
            recipes,
            java.util.Optional.empty(),
            0,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList());
        BuildExpander.Result tree = BuildExpander.expandSelected(
            "Air rune", 556, root, new PeekOnly(), Collections.emptySet());
        RecipeTreeView.fill(crafting.body(), style, tree, false, idleActions(), null, null, false);
        detail.add(crafting);
        return detail;
    }

    private static List<ShopEntry> sampleShops() {
        return Arrays.asList(
            new ShopEntry("Aubury's Rune Shop", 4, 5_000, "gp", true),
            new ShopEntry("Betty's Magic Emporium", 4, 5_000, "gp", true),
            new ShopEntry("Magic Guild Store (Runes and Staves)", 4, 5_000, "gp", true),
            new ShopEntry("Baba Yaga's Magic Shop", 4, 5_000, "gp", true),
            new ShopEntry("Lundail's Arena-side Rune Shop", 4, 5_000, "gp", true),
            new ShopEntry("Void Knight Magic Store", 4, 5_000, "gp", true),
            new ShopEntry("Ali's Discount Wares", 5, 20, "gp", true),
            new ShopEntry("Dom Onion's Reward Shop", 25, ShopEntry.INFINITE, "gp", true)
        );
    }

    private static List<RecipeInfo> sampleRecipes() {
        RecipeInfo essence = new RecipeInfo(
            "Runecraft", 1, "Air tiara, Air talisman", 5,
            Collections.singletonList(new RecipeIngredient("Rune essence", 1)),
            1, "");
        RecipeInfo pure = new RecipeInfo(
            "Runecraft", 1, "Air tiara, Air talisman", 5,
            Collections.singletonList(new RecipeIngredient("Pure essence", 1)),
            1, "");
        return Arrays.asList(essence, pure);
    }

    private static void assertNoOverflow(Render render) {
        assertWithin(render.detail, render.detail, "detail");
        assertEquals(render.image.getWidth(), render.detail.getWidth() <= render.image.getWidth()
            ? render.image.getWidth()
            : render.detail.getWidth());
        assertTrue(render.detail.getWidth() <= render.image.getWidth());
    }

    private static void assertWithin(Component root, Component component, String path) {
        if (component != root && component.isVisible() && component.getWidth() > 0 && component.getParent() != null) {
            Point origin = SwingUtilities.convertPoint(component.getParent(), component.getX(), component.getY(), root);
            int right = origin.x + component.getWidth();
            if (right > root.getWidth() + 1) {
                fail(path + " ends at " + right + ", panel is " + root.getWidth()
                    + " (" + component.getClass().getSimpleName() + " " + textOf(component) + ")");
            }
        }
        if (component instanceof Container) {
            Component[] children = ((Container) component).getComponents();
            for (int i = 0; i < children.length; i++) {
                if (!component.isVisible()) {
                    continue;
                }
                assertWithin(root, children[i], path + "/" + i);
            }
        }
    }

    private static String textOf(Component component) {
        if (component instanceof JLabel) {
            String text = ((JLabel) component).getText();
            return text == null ? "" : text;
        }
        return "";
    }

    private static String visibleText(Component component) {
        if (!component.isVisible()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        if (component instanceof JLabel) {
            String value = ((JLabel) component).getText();
            if (value != null) {
                text.append(value);
            }
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                text.append(visibleText(child));
            }
        }
        return text.toString();
    }

    private static String joinedVisibleLabels(Component component) {
        return visibleText(component);
    }

    private static void layoutDeep(Component component) {
        if (component instanceof Container) {
            Container container = (Container) component;
            container.doLayout();
            for (Component child : container.getComponents()) {
                layoutDeep(child);
            }
        }
    }

    private static void write(BufferedImage image, String name) throws Exception {
        File buildDir = new File("build/detail-preview");
        if (!buildDir.isDirectory() && !buildDir.mkdirs()) {
            fail("Could not create " + buildDir.getAbsolutePath());
        }
        File file = new File(buildDir, name);
        assertTrue(ImageIO.write(image, "png", file));
        assertTrue(file.length() > 1000);
        File artifacts = new File("/opt/cursor/artifacts");
        if (artifacts.isDirectory() || artifacts.mkdirs()) {
            ImageIO.write(image, "png", new File(artifacts, name));
        }
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
            public void icon(javax.swing.JLabel label, String itemName) {
            }
        };
    }

    private static final class PeekOnly implements com.osr.jei.crafting.RecipePages {
        @Override
        public ItemDetails cached(String itemName) {
            return null;
        }

        @Override
        public ItemDetails load(String itemName) {
            throw new AssertionError("collapsed or expanded section must not fetch " + itemName);
        }
    }

    private static final class Render {
        private final JComponent detail;
        private final BufferedImage image;

        private Render(JComponent detail, BufferedImage image) {
            this.detail = detail;
            this.image = image;
        }
    }
}
