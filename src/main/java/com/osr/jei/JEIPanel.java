package com.osr.jei;

import com.osr.jei.model.DropEntry;
import com.osr.jei.model.EquipmentStats;
import com.osr.jei.model.MonsterInfo;
import com.osr.jei.model.RecipeInfo;
import com.osr.jei.model.RecipeIngredient;
import com.osr.jei.model.SearchItem;
import com.osr.jei.model.ShopEntry;
import com.osr.jei.service.EquipmentService;
import com.osr.jei.service.MonsterService;
import com.osr.jei.service.PriceService;
import com.osr.jei.service.RecipeService;
import com.osr.jei.service.ShopService;
import com.osr.jei.service.UsedInService;
import com.osr.jei.service.WikiService;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.AsyncBufferedImage;

import java.lang.reflect.Method;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.util.*;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * The sidebar panel that shows up inside RuneLite.
 *
 * Layout:
 *   [Search bar]  [★ fav toggle]
 *   [Item grid — icon grid, Minecraft JEI style]
 *   ──────────────────────────────────────────────────────
 *   [← Back]  ← shown only when navigating inside recipes
 *   Item name · [★]
 *   GE Price
 *   [Used to craft N items →]
 *   [Dropped by N monsters →]
 *   [Crafting Recipe section]  ← shown only when item has a recipe
 *     Skill (Lv.X) · Station · XP
 *     Clickable ingredient rows
 *   [Open Wiki page]
 */
@Slf4j
@Singleton
public class JEIPanel extends PluginPanel {

    /**
     * Tells PluginPanel NOT to wrap this panel in an outer JScrollPane.
     * Without this, PluginPanel(true) constrains the panel to its preferred
     * height (~245 px) and the sidebar shows only a sliver on first load.
     * With super(false) the sidebar container stretches us to fill all available space.
     */
    public JEIPanel() {
        super(false);
    }

    // ── Injected services ──────────────────────────────────────────────────────
    @Inject private ClientThread  clientThread;
    @Inject private ItemManager   itemManager;
    @Inject private WikiService   wikiService;
    @Inject private PriceService  priceService;
    @Inject private RecipeService recipeService;
    @Inject private UsedInService usedInService;
    @Inject private ConfigManager    configManager;
    @Inject private MonsterService   monsterService;
    @Inject private EquipmentService equipmentService;
    @Inject private ShopService      shopService;
    @Inject private PluginManager    pluginManager;
    @Inject private JEIConfig        config;

    /** Nature rune item ID — used to compute high-alch profit. */
    private static final int NATURE_RUNE_ID = 561;

    // Background thread for network fetches (price, drops, recipe, used-in)
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /**
     * Full item index built from the OSRS game cache.
     * Covers every item — not just GE-tradeable ones.
     * Written once on the client thread, then read-only.
     */
    private volatile List<SearchItem> allItems = Collections.emptyList();

    /**
     * O(1) lookup by lowercase name — built alongside {@link #allItems}.
     * Replaces the O(n) linear scan in {@link #findByName}.
     */
    private volatile Map<String, SearchItem> nameIndex = Collections.emptyMap();

    /**
     * O(1) lookup by item ID — built alongside {@link #allItems}.
     * Used by {@link #lookupItem} and {@link #buildFavouritesList}.
     */
    private volatile Map<Integer, SearchItem> idIndex = Collections.emptyMap();

    // ── UI components: grid ────────────────────────────────────────────────────
    private JPanel gridPanel;
    private JPanel selectedCell;

    // ── UI components: detail panel ────────────────────────────────────────────
    private JButton backButton;

    private JLabel  lblName;
    private JLabel  lblPrice;

    /** Star button shown next to the item name — adds/removes the item from favourites. */
    private JButton starButton;

    /** "Members  ·  1.8 kg" — populated immediately from game cache on item load. */
    private JLabel lblMembersWeight;

    /** High-alch value and profit/loss vs GE price. */
    private JLabel lblHaAlch;

    /** Equipment bonuses section — hidden for non-equipment items. */
    private JPanel equipmentSection;
    /** Inner content panel within equipmentSection — rebuilt per item. */
    private JPanel equipmentContent;

    /** Shop sources section — hidden when no shops sell the item. */
    private JPanel shopsSection;
    /** Inner content panel within shopsSection — rebuilt per item. */
    private JPanel shopsContent;

    /** Outer wrapper for the entire recipe block — hidden when item has no recipe. */
    private JPanel  recipeSection;
    private JLabel  lblRecipeInfo;        // skill / level / facility / xp summary
    private JPanel  recipeIngredientsPanel;

    /** Switches the top area between the icon grid and the used-to-craft expanded list. */
    private CardLayout gridCardLayout;
    private JPanel     gridCardPanel;

    /** Full-width scrollable list shown when the user expands "Used to craft" or "Dropped by". */
    private JPanel     usedInListPanel;

    /** Compact button in the detail panel that opens the expanded used-to-craft view. */
    private JButton    usedInButton;

    /** Cached results from the last fetchUsedIn() call. */
    private List<UsedInEntry> cachedUsedInEntries = new ArrayList<>();

    /** Compact button that opens the full drop-sources list in the top area. */
    private JButton dropsButton;

    /** Cached drop entries for the currently selected item. */
    private List<DropEntry> cachedDropEntries = new ArrayList<>();

    private JButton wikiButton;

    // ── Favourites ─────────────────────────────────────────────────────────────

    /** Item IDs the user has starred — persisted via ConfigManager between sessions. */
    private final Set<Integer> favouriteIds = new LinkedHashSet<>();

    /** Toggle in the search bar row: ☆ = show all, ★ = show favourites only. */
    private JToggleButton favToggle;

    // ── Recently viewed history ────────────────────────────────────────────────

    /** Maximum number of items kept in the recently-viewed row (2 rows of 6). */
    private static final int MAX_HISTORY = 12;

    /** Most-recent item first.  Never exceeds {@link #MAX_HISTORY} entries. */
    private final Deque<SearchItem> recentItems = new ArrayDeque<>(MAX_HISTORY + 1);

    /** Row of small item icons shown between the search bar and the grid. */
    private JPanel historyPanel;

    // ── State ──────────────────────────────────────────────────────────────────
    private String selectedItemName = "";

    /** Stores the last search query so we can re-run it once the item index is ready. */
    private volatile String lastSearchQuery = "";

    /**
     * Navigation history for clicking into recipe ingredients.
     * Each entry is the item whose detail we were viewing before navigating deeper.
     */
    private final Deque<SearchItem> navStack = new ArrayDeque<>();

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    /** Called by JEIPlugin.startUp() after Guice has injected all fields. */
    public void init() {
        setLayout(new BorderLayout(0, 4));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        loadFavourites();

        // ── North: search bar + recently-viewed row (stacked vertically) ───────
        historyPanel = buildHistoryBar();

        JPanel northPanel = new JPanel();
        northPanel.setLayout(new BoxLayout(northPanel, BoxLayout.Y_AXIS));
        northPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        northPanel.add(buildSearchBar());
        northPanel.add(historyPanel);

        add(northPanel,       BorderLayout.NORTH);
        add(buildContent(),   BorderLayout.CENTER);

        buildItemIndex();
    }

    // ── UI builders ────────────────────────────────────────────────────────────

    /**
     * Builds the search bar row: [IconTextField (CENTER)] [★ fav toggle (EAST)].
     * The fav toggle shows only starred items in the grid when active.
     */
    private JPanel buildSearchBar() {
        IconTextField searchBar = new IconTextField();
        searchBar.setIcon(IconTextField.Icon.SEARCH);
        searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        searchBar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);

        searchBar.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { onSearch(searchBar.getText()); }
            @Override public void removeUpdate(DocumentEvent e)  { onSearch(searchBar.getText()); }
            @Override public void changedUpdate(DocumentEvent e) { onSearch(searchBar.getText()); }
        });

        // ── Favourites toggle button ───────────────────────────────────────────
        favToggle = new JToggleButton("☆");
        favToggle.setForeground(new Color(220, 185, 75)); // gold
        favToggle.setFont(favToggle.getFont().deriveFont(Font.BOLD, 15f));
        favToggle.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        favToggle.setOpaque(false);
        favToggle.setBorderPainted(false);
        favToggle.setFocusPainted(false);
        favToggle.setContentAreaFilled(false);
        favToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        favToggle.setToolTipText("Show favourites");
        favToggle.setPreferredSize(new Dimension(28, 30));
        favToggle.addActionListener(e -> {
            if (favToggle.isSelected()) {
                favToggle.setText("★");
                favToggle.setToolTipText("Show all items");
                executor.submit(() -> {
                    List<SearchItem> favItems = buildFavouritesList();
                    SwingUtilities.invokeLater(() -> populateGrid(favItems));
                });
            } else {
                favToggle.setText("☆");
                favToggle.setToolTipText("Show favourites");
                onSearch(lastSearchQuery);
            }
        });

        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBackground(ColorScheme.DARK_GRAY_COLOR);
        row.add(searchBar,  BorderLayout.CENTER);
        row.add(favToggle,  BorderLayout.EAST);
        return row;
    }

    /**
     * Builds the main content area below the search bar.
     *
     * Layout — no JSplitPane (it has unreliable sizing when called before the panel
     * is shown and causes random movement during runtime):
     *
     *   BorderLayout.CENTER → gridScroll   (icon grid; expands to fill all spare height)
     *   BorderLayout.SOUTH  → detailScroll (item info; fixed preferred height, scrollable)
     *
     * The grid always grows/shrinks with the sidebar window — it naturally uses as
     * much vertical space as possible.  The detail panel is fixed at ~210 px and
     * scrolls internally so all recipe / drop info remains reachable.
     */
    private JPanel buildContent() {
        // ── Item grid ──────────────────────────────────────────────────────────
        gridPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 2, 2));
        gridPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JScrollPane gridScroll = new JScrollPane(gridPanel);
        gridScroll.setBorder(BorderFactory.createEmptyBorder());
        gridScroll.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        gridScroll.getVerticalScrollBar().setPreferredSize(new Dimension(6, 0));
        gridScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        // ── Used-to-craft / drops expanded list (occupies the same space as the grid) ─
        usedInListPanel = new JPanel();
        usedInListPanel.setLayout(new BoxLayout(usedInListPanel, BoxLayout.Y_AXIS));
        usedInListPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        usedInListPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JScrollPane usedInListScroll = new JScrollPane(usedInListPanel);
        usedInListScroll.setBorder(BorderFactory.createEmptyBorder());
        usedInListScroll.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        usedInListScroll.getVerticalScrollBar().setPreferredSize(new Dimension(6, 0));
        usedInListScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        // ── CardLayout toggles between grid and used-to-craft / drops list ────
        gridCardLayout = new CardLayout();
        gridCardPanel  = new JPanel(gridCardLayout);
        gridCardPanel.add(gridScroll,       "grid");
        gridCardPanel.add(usedInListScroll, "usedIn");

        // ── Detail panel ───────────────────────────────────────────────────────
        JScrollPane detailScroll = new JScrollPane(buildDetailPanel());
        detailScroll.setBorder(BorderFactory.createEmptyBorder());
        detailScroll.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        detailScroll.getVerticalScrollBar().setPreferredSize(new Dimension(6, 0));
        // Preferred height = how tall the detail section appears; user can scroll for more
        detailScroll.setPreferredSize(new Dimension(PANEL_WIDTH, 290));
        detailScroll.setMinimumSize(new Dimension(0, 120));

        // ── Assemble ───────────────────────────────────────────────────────────
        JPanel content = new JPanel(new BorderLayout(0, 3));
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);
        content.add(gridCardPanel, BorderLayout.CENTER); // expands with window
        content.add(detailScroll,  BorderLayout.SOUTH);  // pinned to bottom, fixed height
        return content;
    }

    private JPanel buildDetailPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // ── Back button (navigation) ───────────────────────────────────────────
        backButton = new JButton("← Back");
        backButton.setForeground(new Color(100, 180, 255));
        backButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        backButton.setOpaque(false);
        backButton.setBorderPainted(false);
        backButton.setFocusPainted(false);
        backButton.setContentAreaFilled(false);
        backButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        backButton.setAlignmentX(LEFT_ALIGNMENT);
        backButton.setFont(backButton.getFont().deriveFont(11f));
        backButton.addActionListener(e -> navigateBack());
        backButton.setVisible(false);

        // ── Item name + star button ────────────────────────────────────────────
        lblName = new JLabel("Search for an item above");
        lblName.setForeground(Color.WHITE);
        lblName.setFont(lblName.getFont().deriveFont(Font.BOLD, 13f));

        starButton = new JButton("☆");
        starButton.setForeground(new Color(220, 185, 75)); // gold
        starButton.setFont(starButton.getFont().deriveFont(Font.BOLD, 14f));
        starButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        starButton.setOpaque(false);
        starButton.setBorderPainted(false);
        starButton.setFocusPainted(false);
        starButton.setContentAreaFilled(false);
        starButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        starButton.setMargin(new Insets(0, 4, 0, 0));
        starButton.setToolTipText("Add to favourites");
        starButton.setVisible(false);
        starButton.addActionListener(e -> {
            SearchItem current = findByName(selectedItemName);
            if (current != null) toggleFavourite(current);
        });

        // Row: name fills available width, star pinned to right
        JPanel nameRow = new JPanel(new BorderLayout(0, 0));
        nameRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        nameRow.setAlignmentX(LEFT_ALIGNMENT);
        nameRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        nameRow.add(lblName,    BorderLayout.CENTER);
        nameRow.add(starButton, BorderLayout.EAST);

        // ── Price ──────────────────────────────────────────────────────────────
        lblPrice = new JLabel(" ");
        lblPrice.setForeground(new Color(220, 185, 75));
        lblPrice.setFont(lblPrice.getFont().deriveFont(Font.BOLD, 12f));
        lblPrice.setAlignmentX(LEFT_ALIGNMENT);

        // ── Members / weight (populated instantly from game cache) ─────────────
        lblMembersWeight = new JLabel(" ");
        lblMembersWeight.setForeground(new Color(140, 140, 140));
        lblMembersWeight.setFont(lblMembersWeight.getFont().deriveFont(11f));
        lblMembersWeight.setAlignmentX(LEFT_ALIGNMENT);

        // ── High-alch value + profit ───────────────────────────────────────────
        lblHaAlch = new JLabel(" ");
        lblHaAlch.setForeground(new Color(180, 220, 120)); // lime-ish green
        lblHaAlch.setFont(lblHaAlch.getFont().deriveFont(12f));
        lblHaAlch.setAlignmentX(LEFT_ALIGNMENT);

        // ── Equipment stats section ────────────────────────────────────────────
        equipmentSection = new JPanel();
        equipmentSection.setLayout(new BoxLayout(equipmentSection, BoxLayout.Y_AXIS));
        equipmentSection.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        equipmentSection.setAlignmentX(LEFT_ALIGNMENT);
        equipmentSection.setVisible(false);

        JLabel eqHeader = new JLabel("Equipment:");
        eqHeader.setForeground(Color.WHITE);
        eqHeader.setFont(eqHeader.getFont().deriveFont(Font.BOLD, 12f));
        eqHeader.setAlignmentX(LEFT_ALIGNMENT);

        equipmentContent = new JPanel();
        equipmentContent.setLayout(new BoxLayout(equipmentContent, BoxLayout.Y_AXIS));
        equipmentContent.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        equipmentContent.setAlignmentX(LEFT_ALIGNMENT);

        equipmentSection.add(eqHeader);
        equipmentSection.add(Box.createRigidArea(new Dimension(0, 3)));
        equipmentSection.add(equipmentContent);
        equipmentSection.add(Box.createRigidArea(new Dimension(0, 6)));

        // ── Shop sources section ───────────────────────────────────────────────
        shopsSection = new JPanel();
        shopsSection.setLayout(new BoxLayout(shopsSection, BoxLayout.Y_AXIS));
        shopsSection.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        shopsSection.setAlignmentX(LEFT_ALIGNMENT);
        shopsSection.setVisible(false);

        JLabel shopHeader = new JLabel("Shop sources:");
        shopHeader.setForeground(Color.WHITE);
        shopHeader.setFont(shopHeader.getFont().deriveFont(Font.BOLD, 12f));
        shopHeader.setAlignmentX(LEFT_ALIGNMENT);

        shopsContent = new JPanel();
        shopsContent.setLayout(new BoxLayout(shopsContent, BoxLayout.Y_AXIS));
        shopsContent.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        shopsContent.setAlignmentX(LEFT_ALIGNMENT);

        shopsSection.add(shopHeader);
        shopsSection.add(Box.createRigidArea(new Dimension(0, 3)));
        shopsSection.add(shopsContent);
        shopsSection.add(Box.createRigidArea(new Dimension(0, 6)));

        // ── Recipe section ─────────────────────────────────────────────────────
        recipeSection = new JPanel();
        recipeSection.setLayout(new BoxLayout(recipeSection, BoxLayout.Y_AXIS));
        recipeSection.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        recipeSection.setAlignmentX(LEFT_ALIGNMENT);
        recipeSection.setVisible(false); // hidden until a recipe is found

        JLabel recipeHeader = new JLabel("Crafting Recipe:");
        recipeHeader.setForeground(Color.WHITE);
        recipeHeader.setFont(recipeHeader.getFont().deriveFont(Font.BOLD, 12f));
        recipeHeader.setAlignmentX(LEFT_ALIGNMENT);

        // One-line summary: "Smithing (Lv. 33) · Anvil · 25 XP"
        lblRecipeInfo = new JLabel(" ");
        lblRecipeInfo.setForeground(new Color(150, 220, 150));
        lblRecipeInfo.setFont(lblRecipeInfo.getFont().deriveFont(11f));
        lblRecipeInfo.setAlignmentX(LEFT_ALIGNMENT);

        recipeIngredientsPanel = new JPanel();
        recipeIngredientsPanel.setLayout(new BoxLayout(recipeIngredientsPanel, BoxLayout.Y_AXIS));
        recipeIngredientsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        recipeIngredientsPanel.setAlignmentX(LEFT_ALIGNMENT);

        recipeSection.add(recipeHeader);
        recipeSection.add(Box.createRigidArea(new Dimension(0, 3)));
        recipeSection.add(lblRecipeInfo);
        recipeSection.add(Box.createRigidArea(new Dimension(0, 4)));
        recipeSection.add(recipeIngredientsPanel);
        // Trailing spacer (invisible when section is hidden — BoxLayout skips invisible components)
        recipeSection.add(Box.createRigidArea(new Dimension(0, 8)));

        // ── Used-to-craft button — expands top area to full list on click ─────
        usedInButton = new JButton("Used to craft: ? items  →");
        usedInButton.setForeground(new Color(100, 210, 100));
        usedInButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        usedInButton.setOpaque(false);
        usedInButton.setBorderPainted(false);
        usedInButton.setFocusPainted(false);
        usedInButton.setContentAreaFilled(false);
        usedInButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        usedInButton.setAlignmentX(LEFT_ALIGNMENT);
        usedInButton.setFont(usedInButton.getFont().deriveFont(Font.BOLD, 11f));
        usedInButton.addActionListener(e -> showUsedInExpanded());
        usedInButton.setVisible(false);

        // ── Drop sources button — expands top area to full monster list on click ─
        dropsButton = new JButton("Dropped by: ? monsters  →");
        dropsButton.setForeground(new Color(100, 210, 100));
        dropsButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        dropsButton.setOpaque(false);
        dropsButton.setBorderPainted(false);
        dropsButton.setFocusPainted(false);
        dropsButton.setContentAreaFilled(false);
        dropsButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        dropsButton.setAlignmentX(LEFT_ALIGNMENT);
        dropsButton.setFont(dropsButton.getFont().deriveFont(Font.BOLD, 11f));
        dropsButton.addActionListener(e -> showDropsExpanded());
        dropsButton.setVisible(false);

        // ── Wiki button ────────────────────────────────────────────────────────
        wikiButton = new JButton("Open Wiki page");
        wikiButton.setForeground(new Color(100, 180, 255));
        wikiButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        wikiButton.setBorderPainted(false);
        wikiButton.setFocusPainted(false);
        wikiButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        wikiButton.setAlignmentX(LEFT_ALIGNMENT);
        wikiButton.addActionListener(e -> openWiki());
        wikiButton.setVisible(false);

        // ── Assemble ───────────────────────────────────────────────────────────
        // Green action buttons sit directly under the price so they are always
        // visible without scrolling, even when the recipe section is long.
        panel.add(backButton);
        panel.add(Box.createRigidArea(new Dimension(0, 4)));
        panel.add(nameRow);
        panel.add(Box.createRigidArea(new Dimension(0, 3)));
        panel.add(lblMembersWeight);   // "Members  ·  1.8 kg"  (instant, no network)
        panel.add(Box.createRigidArea(new Dimension(0, 4)));
        panel.add(lblPrice);
        panel.add(Box.createRigidArea(new Dimension(0, 2)));
        panel.add(lblHaAlch);          // "High Alch: 300 gp  (+192 gp)"
        panel.add(Box.createRigidArea(new Dimension(0, 6)));
        panel.add(equipmentSection);   // hidden until stats load
        panel.add(shopsSection);       // hidden until shops load
        panel.add(usedInButton);       // hidden until used-in data loads
        panel.add(dropsButton);        // hidden until drops data loads
        panel.add(Box.createRigidArea(new Dimension(0, 8)));
        panel.add(recipeSection);      // includes its own trailing spacer
        panel.add(wikiButton);

        return panel;
    }

    // ── Item index building ────────────────────────────────────────────────────

    /**
     * Scans IDs 0-40 000 via the game cache to build a complete item index.
     * MUST run on the client thread — getItemComposition() returns "null" for
     * every item when called from an arbitrary background thread.
     */
    private void buildItemIndex() {
        clientThread.invokeLater(() -> {
            log.info("[JEI] Building item index from game cache...");

            // byName deduplicates: first ID wins (base item before noted/placeholder variants)
            Map<String, SearchItem> byName = new LinkedHashMap<>();
            Map<Integer, SearchItem> byId  = new HashMap<>();

            for (int id = 0; id <= 40_000; id++) {
                try {
                    ItemComposition comp = itemManager.getItemComposition(id);
                    if (comp == null) continue;
                    String name = comp.getName();
                    if (name == null || name.equalsIgnoreCase("null") || name.isEmpty()) continue;
                    if (name.startsWith("<")) continue;
                    String key = name.toLowerCase(Locale.ROOT);
                    if (byName.putIfAbsent(key, new SearchItem(
                            id, name, comp.isMembers(), comp.getHaPrice())) == null) {
                        // First occurrence of this name — also index by ID
                        byId.put(id, byName.get(key));
                    }
                } catch (Exception ignored) { }
            }

            allItems  = new ArrayList<>(byName.values());
            nameIndex = Collections.unmodifiableMap(byName);
            idIndex   = Collections.unmodifiableMap(byId);
            log.info("[JEI] Item index ready — {} unique items", allItems.size());

            String pending = lastSearchQuery;
            if (!pending.isEmpty()) onSearch(pending);
        });
    }

    // ── Search ─────────────────────────────────────────────────────────────────

    private void onSearch(String query) {
        // When the user types a new search, leave favourites mode automatically
        if (favToggle != null && favToggle.isSelected()
                && query != null && !query.trim().isEmpty()) {
            favToggle.setSelected(false);
            favToggle.setText("☆");
            favToggle.setToolTipText("Show favourites");
        }

        if (query == null || query.trim().length() < 2) {
            lastSearchQuery = "";
            SwingUtilities.invokeLater(() -> {
                gridPanel.removeAll();
                selectedCell = null;
                gridPanel.revalidate();
                gridPanel.repaint();
            });
            return;
        }

        String lower = query.trim().toLowerCase(Locale.ROOT);
        lastSearchQuery = lower;

        executor.submit(() -> {
            List<SearchItem> snapshot = allItems;
            List<SearchItem> results = snapshot.stream()
                .filter(item -> item.name.toLowerCase(Locale.ROOT).contains(lower))
                .limit(200)
                .collect(Collectors.toList());

            SwingUtilities.invokeLater(() -> populateGrid(results));
        });
    }

    // ── Navigation ─────────────────────────────────────────────────────────────

    /** Called when the user clicks a grid cell — resets the nav stack. */
    private void onItemSelected(SearchItem item) {
        navStack.clear();
        backButton.setVisible(false);
        gridCardLayout.show(gridCardPanel, "grid");
        loadItemDetail(item);
    }

    /**
     * Called when the user clicks a recipe ingredient — pushes the current item
     * onto the nav stack so the user can press Back to return.
     */
    private void navigateToIngredient(SearchItem target) {
        // Push whatever is currently displayed
        if (!selectedItemName.isEmpty()) {
            SearchItem current = findByName(selectedItemName);
            if (current != null) navStack.push(current);
        }
        backButton.setVisible(true);
        gridCardLayout.show(gridCardPanel, "grid");
        loadItemDetail(target);
    }

    /** Pops one level from the nav stack and returns to the previous item. */
    private void navigateBack() {
        if (navStack.isEmpty()) return;
        SearchItem prev = navStack.pop();
        backButton.setVisible(!navStack.isEmpty());
        gridCardLayout.show(gridCardPanel, "grid");
        loadItemDetail(prev);
    }

    // ── Detail loading ─────────────────────────────────────────────────────────

    /**
     * Populates the detail panel with data for {@code item}.
     * UI resets happen immediately; network fetches run in the background.
     * Must be called on the EDT.
     */
    private void loadItemDetail(SearchItem item) {
        selectedItemName = item.name;
        updateHistory(item); // add to recently-viewed row

        // ── Immediate UI reset (no network needed) ─────────────────────────────
        lblName.setText(item.name);
        lblPrice.setText("Loading...");
        lblHaAlch.setText(" ");
        recipeSection.setVisible(false);
        equipmentSection.setVisible(false);
        shopsSection.setVisible(false);
        usedInButton.setVisible(false);
        dropsButton.setVisible(false);
        gridCardLayout.show(gridCardPanel, "grid"); // restore grid if expanded view was open
        wikiButton.setVisible(true);

        // Members / weight — from game cache, instant
        String membersTag = item.members ? "Members" : "F2P";
        lblMembersWeight.setText(membersTag); // weight appended later once wikitext is parsed

        // Update star button for this item
        updateStarButton(item);
        starButton.setVisible(true);

        executor.submit(() -> {
            try {
                // ── GE price ───────────────────────────────────────────────────
                long gePrice = priceService.getPrice(item.id);
                String priceText = gePrice > 0
                    ? String.format("GE Price: %,d gp", gePrice)
                    : "Not on GE / no price data";
                SwingUtilities.invokeLater(() -> lblPrice.setText(priceText));

                // ── High-alch value + profit ───────────────────────────────────
                // haPrice comes straight from the game cache (SearchItem field).
                // Nature rune price is fetched once and cached by PriceService.
                if (item.haPrice > 0) {
                    long naturePrice = priceService.getPrice(NATURE_RUNE_ID);
                    long profit      = item.haPrice - Math.max(gePrice, 0) - naturePrice;
                    String profitStr = (profit >= 0)
                        ? String.format("+%,d gp", profit)
                        : String.format("%,d gp", profit);
                    String haText = String.format("High Alch: %,d gp  (%s)", item.haPrice, profitStr);
                    Color  haColor = profit >= 0 ? new Color(100, 210, 100) : new Color(220, 100, 100);
                    SwingUtilities.invokeLater(() -> {
                        lblHaAlch.setText(haText);
                        lblHaAlch.setForeground(haColor);
                    });
                }

                // ── Crafting recipe ────────────────────────────────────────────
                // getRecipe() populates RecipeService.wikitextCache as a side-effect,
                // so the subsequent equipment/shop calls cost zero extra network calls.
                Optional<RecipeInfo> recipe = recipeService.getRecipe(item.name);
                SwingUtilities.invokeLater(() -> populateRecipePanel(recipe));

                // ── Equipment stats ────────────────────────────────────────────
                // {{Infobox Bonuses}} and {{Infobox Item}} are static templates in
                // the raw wikitext, so EquipmentService reuses RecipeService's
                // already-cached wikitext — zero extra network calls.
                Optional<EquipmentStats> stats = equipmentService.getStats(item.name);
                double weight = equipmentService.getWeight(item.name);
                String membersTag2 = item.members ? "Members" : "F2P";
                String weightStr = Double.isNaN(weight) || weight < 0
                    ? ""
                    : (weight == 0.0 ? "  ·  0 kg" : String.format("  ·  %.3g kg", weight));
                SwingUtilities.invokeLater(() -> {
                    lblMembersWeight.setText(membersTag2 + weightStr);
                    populateEquipmentSection(stats);
                });

                // ── Shop sources ───────────────────────────────────────────────
                // Shop data is rendered by a Lua template ({{Store locations list}})
                // so it never appears in raw wikitext. ShopService fetches the
                // rendered HTML of the "Shop locations" section directly — same
                // 2-call pattern as WikiService uses for drop sources.
                List<ShopEntry> shops = shopService.getShops(item.name);
                SwingUtilities.invokeLater(() -> populateShopsSection(shops, gePrice));

                // ── Used to craft ──────────────────────────────────────────────
                List<UsedInEntry> usedIn = fetchUsedIn(item.name);
                SwingUtilities.invokeLater(() -> onUsedInLoaded(usedIn));

                // ── Drop sources ───────────────────────────────────────────────
                List<DropEntry> drops = wikiService.getDropSources(item.name);
                log.debug("[JEI] Drop sources for '{}': {} result(s)", item.name, drops.size());
                SwingUtilities.invokeLater(() -> onDropsLoaded(drops));

            } catch (Exception e) {
                log.error("[JEI] Failed to load data for '{}'", item.name, e);
                SwingUtilities.invokeLater(() -> lblPrice.setText("Error loading data"));
            }
        });
    }

    // ── Grid helpers ───────────────────────────────────────────────────────────

    private void populateGrid(List<SearchItem> items) {
        gridPanel.removeAll();
        selectedCell = null;
        for (SearchItem item : items) gridPanel.add(buildItemCell(item));
        gridPanel.revalidate();
        gridPanel.repaint();
    }

    private JPanel buildItemCell(SearchItem item) {
        JPanel cell = new JPanel(new BorderLayout());
        cell.setPreferredSize(new Dimension(38, 38));
        cell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        cell.setBorder(BorderFactory.createLineBorder(new Color(25, 25, 25), 1));
        cell.setToolTipText(item.name);
        cell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // The icon label returns false from contains() so mouse events
        // pass through to the parent cell panel instead of being swallowed.
        JLabel iconLabel = new JLabel() {
            @Override public boolean contains(int x, int y) { return false; }
        };
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setVerticalAlignment(SwingConstants.CENTER);
        AsyncBufferedImage img = itemManager.getImage(item.id);
        if (img != null) img.addTo(iconLabel);
        cell.add(iconLabel, BorderLayout.CENTER);

        cell.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (selectedCell != null) {
                    selectedCell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                    selectedCell.repaint();
                }
                selectedCell = cell;
                cell.setBackground(ColorScheme.BRAND_ORANGE);
                cell.repaint();
                onItemSelected(item);
            }
            @Override public void mouseEntered(MouseEvent e) {
                if (cell != selectedCell) { cell.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR); cell.repaint(); }
            }
            @Override public void mouseExited(MouseEvent e) {
                if (cell != selectedCell) { cell.setBackground(ColorScheme.DARKER_GRAY_COLOR); cell.repaint(); }
            }
        });

        return cell;
    }

    // ── Recipe helpers ─────────────────────────────────────────────────────────

    private void populateRecipePanel(Optional<RecipeInfo> recipeOpt) {
        if (!recipeOpt.isPresent()) {
            recipeSection.setVisible(false);
            return;
        }

        RecipeInfo r = recipeOpt.get();

        // ── Summary line: "Smithing (Lv. 33) · Anvil · 25 XP" ─────────────────
        StringBuilder info = new StringBuilder();
        if (!r.skill.isEmpty()) {
            info.append(r.skill);
            if (r.level > 0) info.append(" (Lv. ").append(r.level).append(")");
        }
        if (!r.facility.isEmpty()) {
            if (info.length() > 0) info.append("  ·  ");
            info.append(r.facility);
        }
        if (r.experience > 0) {
            if (info.length() > 0) info.append("  ·  ");
            // Show XP without trailing .0 when it's a whole number
            if (r.experience == Math.floor(r.experience)) {
                info.append((int) r.experience).append(" XP");
            } else {
                info.append(r.experience).append(" XP");
            }
        }
        lblRecipeInfo.setText(info.toString());

        // ── Ingredient rows ────────────────────────────────────────────────────
        recipeIngredientsPanel.removeAll();

        if (!r.ingredients.isEmpty()) {
            JLabel ingLabel = new JLabel("Ingredients:");
            ingLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            ingLabel.setFont(ingLabel.getFont().deriveFont(Font.BOLD, 11f));
            ingLabel.setAlignmentX(LEFT_ALIGNMENT);
            recipeIngredientsPanel.add(ingLabel);
            recipeIngredientsPanel.add(Box.createRigidArea(new Dimension(0, 3)));

            for (RecipeIngredient ing : r.ingredients) {
                recipeIngredientsPanel.add(buildIngredientRow(ing));
                recipeIngredientsPanel.add(Box.createRigidArea(new Dimension(0, 2)));
            }
        }

        recipeIngredientsPanel.revalidate();
        recipeIngredientsPanel.repaint();
        recipeSection.setVisible(true);
        revalidate();
        repaint();
    }

    /**
     * Builds one ingredient row: [icon] Name × Qty
     *
     * If the ingredient name is found in the item index the row is clickable
     * (blue text, hand cursor) and navigates to that item on click.
     *
     * The icon label uses a {@code contains() → false} override so mouse
     * events pass through to the parent row panel.
     */
    private JPanel buildIngredientRow(RecipeIngredient ing) {
        SearchItem found = findByName(ing.name);

        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        // Icon
        JLabel iconLabel = new JLabel() {
            @Override public boolean contains(int x, int y) { return false; }
        };
        iconLabel.setPreferredSize(new Dimension(26, 26));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setVerticalAlignment(SwingConstants.CENTER);
        if (found != null) {
            AsyncBufferedImage img = itemManager.getImage(found.id);
            if (img != null) img.addTo(iconLabel);
        }

        // Label: "Iron bar × 5"
        String labelText = ing.quantity > 1
            ? ing.name + " × " + ing.quantity   // × character
            : ing.name;
        JLabel nameLabel = new JLabel(labelText);
        nameLabel.setFont(nameLabel.getFont().deriveFont(11f));

        if (found != null) {
            // Clickable — blue text and hand cursor
            nameLabel.setForeground(new Color(100, 180, 255));
            row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            SearchItem target = found;
            MouseAdapter handler = new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { navigateToIngredient(target); }
                @Override public void mouseEntered(MouseEvent e) {
                    row.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR); row.repaint();
                }
                @Override public void mouseExited(MouseEvent e) {
                    row.setBackground(ColorScheme.DARKER_GRAY_COLOR); row.repaint();
                }
            };
            row.addMouseListener(handler);
            nameLabel.addMouseListener(handler);
        } else {
            nameLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        }

        row.add(iconLabel, BorderLayout.WEST);
        row.add(nameLabel, BorderLayout.CENTER);
        return row;
    }

    // ── Used-to-craft helpers ──────────────────────────────────────────────────

    /**
     * Returns every item that can be crafted from {@code ingredientName} by
     * parsing the "Products" section on the ingredient's own OSRS Wiki page.
     *
     * <p>The wiki curates this table itself — results are exact with no false
     * positives — so no per-product verification pass is needed.  The only
     * post-filter is checking each product against the game-item index to drop
     * any wiki page titles that aren't actual in-game items (e.g. minigame
     * objects or quest props that have recipes on the wiki but no item ID).
     */
    private List<UsedInEntry> fetchUsedIn(String ingredientName) {
        List<String> products = usedInService.getCandidateProducts(ingredientName);

        List<UsedInEntry> result = products.stream()
            .filter(name -> findByName(name) != null) // keep only known game items
            .map(UsedInEntry::new)
            .collect(Collectors.toList());

        log.debug("[JEI] UsedIn for '{}': {} item(s) (Products section)", ingredientName, result.size());
        return result;
    }

    /** Called on the EDT once the background fetch of used-in products completes. */
    private void onUsedInLoaded(List<UsedInEntry> entries) {
        cachedUsedInEntries = entries;
        if (entries.isEmpty()) {
            usedInButton.setVisible(false);
        } else {
            String label = entries.size() == 1
                ? "Used to craft: 1 item  →"
                : "Used to craft: " + entries.size() + " items  →";
            usedInButton.setText(label);
            usedInButton.setVisible(true);
        }
        revalidate();
        repaint();
    }

    /**
     * Switches the top area from the icon grid to a full scrollable list of every
     * item that can be crafted from the currently selected ingredient.
     * A "← Back to results" button at the top restores the icon grid.
     */
    private void showUsedInExpanded() {
        usedInListPanel.removeAll();

        // ── Back-to-grid button ────────────────────────────────────────────────
        JButton backToGrid = new JButton("← Back to results");
        backToGrid.setForeground(new Color(100, 180, 255));
        backToGrid.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        backToGrid.setOpaque(false);
        backToGrid.setBorderPainted(false);
        backToGrid.setFocusPainted(false);
        backToGrid.setContentAreaFilled(false);
        backToGrid.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        backToGrid.setAlignmentX(LEFT_ALIGNMENT);
        backToGrid.setFont(backToGrid.getFont().deriveFont(11f));
        backToGrid.addActionListener(e -> gridCardLayout.show(gridCardPanel, "grid"));

        // ── Section title ──────────────────────────────────────────────────────
        JLabel title = new JLabel("Used to craft:  " + selectedItemName);
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 12f));
        title.setAlignmentX(LEFT_ALIGNMENT);

        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setForeground(new Color(60, 60, 60));

        usedInListPanel.add(backToGrid);
        usedInListPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        usedInListPanel.add(title);
        usedInListPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        usedInListPanel.add(sep);
        usedInListPanel.add(Box.createRigidArea(new Dimension(0, 6)));

        // ── Product rows ───────────────────────────────────────────────────────
        for (UsedInEntry entry : cachedUsedInEntries) {
            usedInListPanel.add(buildUsedInRow(entry));
            usedInListPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        }

        usedInListPanel.revalidate();
        usedInListPanel.repaint();
        gridCardLayout.show(gridCardPanel, "usedIn");
    }

    /**
     * One "used to craft" row: [icon] Product name (clickable if in item index).
     * No recipe preview — click the row to navigate to that item and see its recipe.
     */
    private JPanel buildUsedInRow(UsedInEntry entry) {
        SearchItem found = findByName(entry.itemName);

        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        // Icon — contains() override passes mouse events through to the row panel
        JLabel iconLabel = new JLabel() {
            @Override public boolean contains(int x, int y) { return false; }
        };
        iconLabel.setPreferredSize(new Dimension(26, 26));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setVerticalAlignment(SwingConstants.CENTER);
        if (found != null) {
            AsyncBufferedImage img = itemManager.getImage(found.id);
            if (img != null) img.addTo(iconLabel);
        }

        JLabel nameLabel = new JLabel(entry.itemName);
        nameLabel.setFont(nameLabel.getFont().deriveFont(11f));

        if (found != null) {
            nameLabel.setForeground(new Color(100, 180, 255));
            row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            SearchItem target = found;
            MouseAdapter handler = new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { navigateToIngredient(target); }
                @Override public void mouseEntered(MouseEvent e) {
                    row.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR); row.repaint();
                }
                @Override public void mouseExited(MouseEvent e) {
                    row.setBackground(ColorScheme.DARKER_GRAY_COLOR); row.repaint();
                }
            };
            row.addMouseListener(handler);
            nameLabel.addMouseListener(handler);
        } else {
            nameLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        }

        row.add(iconLabel, BorderLayout.WEST);
        row.add(nameLabel, BorderLayout.CENTER);
        return row;
    }

    /** A product that uses the selected item as a crafting ingredient. */
    private static class UsedInEntry {
        final String itemName;
        UsedInEntry(String itemName) { this.itemName = itemName; }
    }

    // ── Equipment stats helpers ────────────────────────────────────────────────

    /**
     * Populates the equipment-stats section if the item is wearable, or hides it.
     *
     * <p>Uses a compact HTML table (Attack / Defence per style) so values never
     * overflow the sidebar width regardless of the number of digits.
     * Called on the EDT.
     */
    private void populateEquipmentSection(Optional<EquipmentStats> statsOpt) {
        equipmentContent.removeAll();

        if (!statsOpt.isPresent()) {
            equipmentSection.setVisible(false);
            revalidate(); repaint();
            return;
        }

        EquipmentStats s = statsOpt.get();
        Font statFont = new Font(Font.SANS_SERIF, Font.PLAIN, 11);

        // ── Slot + speed ───────────────────────────────────────────────────────
        String meta = s.slot;
        if (s.speed > 0) meta += "  ·  Speed " + s.speed;
        JLabel slotLbl = new JLabel(meta);
        slotLbl.setForeground(new Color(200, 200, 200));
        slotLbl.setFont(statFont.deriveFont(Font.BOLD));
        slotLbl.setAlignmentX(LEFT_ALIGNMENT);
        slotLbl.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        equipmentContent.add(slotLbl);
        equipmentContent.add(Box.createRigidArea(new Dimension(0, 4)));

        // ── Attack / Defence table ─────────────────────────────────────────────
        // One row per combat style; Attack and Defence values side by side.
        // An HTML table avoids any horizontal overflow — values never run past the
        // sidebar edge no matter how many digits the bonus has.
        StringBuilder sb = new StringBuilder("<html><table cellspacing='2' cellpadding='0'>");
        sb.append("<tr><td></td>")
          .append("<td><font color='#96c8ff'><b>&nbsp;Atk</b></font></td>")
          .append("<td><font color='#96c8ff'><b>&nbsp;&nbsp;Def</b></font></td></tr>");
        appendStatRow(sb, "Stab",  s.attackStab,  s.defStab);
        appendStatRow(sb, "Slash", s.attackSlash, s.defSlash);
        appendStatRow(sb, "Crush", s.attackCrush, s.defCrush);
        appendStatRow(sb, "Magic", s.attackMagic, s.defMagic);
        appendStatRow(sb, "Range", s.attackRange, s.defRange);
        sb.append("</table></html>");

        JLabel tableLbl = new JLabel(sb.toString());
        tableLbl.setFont(statFont);
        tableLbl.setAlignmentX(LEFT_ALIGNMENT);
        tableLbl.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        equipmentContent.add(tableLbl);

        // ── Other bonuses (Str, Ranged Str, Magic Dmg %, Prayer) ──────────────
        if (s.meleeStr != 0 || s.rangedStr != 0 || s.magicDmg != 0 || s.prayer != 0) {
            equipmentContent.add(Box.createRigidArea(new Dimension(0, 3)));
            StringBuilder other = new StringBuilder("<html><font color='#b4dc78'>");
            if (s.meleeStr  != 0) other.append(String.format("Str %+d &nbsp; ", s.meleeStr));
            if (s.rangedStr != 0) other.append(String.format("Rng Str %+d &nbsp; ", s.rangedStr));
            if (s.magicDmg  != 0) other.append(String.format("Mag Dmg %+d%% &nbsp; ", s.magicDmg));
            if (s.prayer    != 0) other.append(String.format("Prayer %+d", s.prayer));
            other.append("</font></html>");
            JLabel otherLbl = new JLabel(other.toString());
            otherLbl.setFont(statFont);
            otherLbl.setAlignmentX(LEFT_ALIGNMENT);
            otherLbl.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
            equipmentContent.add(otherLbl);
        }

        equipmentContent.revalidate();
        equipmentContent.repaint();
        equipmentSection.setVisible(true);
        revalidate();
        repaint();
    }

    /**
     * Appends one {@code <tr>} to an equipment-stat HTML table.
     * Each row shows the combat style label, the attack value, and the defence value.
     */
    private static void appendStatRow(StringBuilder sb, String label, int atk, int def) {
        sb.append(String.format(
            "<tr><td><font color='#c8c8c8'>%s</font></td>"
            + "<td align='right'><font color='#96c8ff'>%+d</font></td>"
            + "<td align='right'><font color='#96c8ff'>%+d</font></td></tr>",
            label, atk, def));
    }

    private JLabel makeStatLabel(String text, Color color, Font font) {
        JLabel lbl = new JLabel(text);
        lbl.setForeground(color);
        lbl.setFont(font);
        lbl.setAlignmentX(LEFT_ALIGNMENT);
        return lbl;
    }

    // ── Shop sources helpers ───────────────────────────────────────────────────

    /**
     * Populates the shop-sources section with one row per shop, or hides it.
     *
     * <p>The shop price is coloured relative to the current GE price:
     * <ul>
     *   <li>Green  — shop price is at or below the GE price (good deal)</li>
     *   <li>Red    — shop price is above the GE price (buy from GE instead)</li>
     *   <li>Gray   — no GE price data to compare against, or no shop price listed</li>
     * </ul>
     * Called on the EDT.
     *
     * @param gePrice current GE price in coins, or ≤0 if unavailable
     */
    private void populateShopsSection(List<ShopEntry> shops, long gePrice) {
        shopsContent.removeAll();

        if (shops.isEmpty()) {
            shopsSection.setVisible(false);
            revalidate(); repaint();
            return;
        }

        Font shopFont = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
        for (ShopEntry shop : shops) {
            // ── Determine price colour ─────────────────────────────────────────
            String priceStr  = "";
            String priceHex  = "#bebebe"; // neutral gray — no price or no GE data
            if (shop.price > 0) {
                priceStr = "  —  " + String.format("%,d", shop.price) + " gp";
                if (gePrice > 0) {
                    priceHex = (shop.price <= gePrice) ? "#64d264" : "#dc6464";
                }
            }

            // Use HTML so the shop name stays gray and only the price is coloured.
            // setMaximumSize lets BoxLayout stretch the label to the container width,
            // which triggers Swing's HTML renderer to word-wrap long shop names.
            String html = String.format(
                "<html><font color='#bebebe'>· %s</font><font color='%s'>%s</font></html>",
                escHtml(shop.shopName), priceHex, escHtml(priceStr));

            JLabel lbl = new JLabel(html);
            lbl.setFont(shopFont);
            lbl.setAlignmentX(LEFT_ALIGNMENT);
            lbl.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
            shopsContent.add(lbl);
            shopsContent.add(Box.createRigidArea(new Dimension(0, 3)));
        }

        shopsContent.revalidate();
        shopsContent.repaint();
        shopsSection.setVisible(true);
        revalidate();
        repaint();
    }

    // ── Drop helpers ───────────────────────────────────────────────────────────

    /** Called on the EDT once the background drop fetch completes. */
    private void onDropsLoaded(List<DropEntry> drops) {
        cachedDropEntries = drops;
        if (drops.isEmpty()) {
            dropsButton.setVisible(false);
        } else {
            String label = drops.size() == 1
                ? "Dropped by: 1 monster  →"
                : "Dropped by: " + drops.size() + " monsters  →";
            dropsButton.setText(label);
            dropsButton.setVisible(true);
        }
        revalidate();
        repaint();
    }

    /**
     * Switches the top area to a full scrollable list of every monster that
     * drops the selected item.  Shares the same "usedIn" card as the used-to-craft
     * expanded view — only one can be open at a time.
     */
    private void showDropsExpanded() {
        usedInListPanel.removeAll();

        // ── Back button ────────────────────────────────────────────────────────
        JButton backToGrid = new JButton("← Back to results");
        backToGrid.setForeground(new Color(100, 180, 255));
        backToGrid.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        backToGrid.setOpaque(false);
        backToGrid.setBorderPainted(false);
        backToGrid.setFocusPainted(false);
        backToGrid.setContentAreaFilled(false);
        backToGrid.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        backToGrid.setAlignmentX(LEFT_ALIGNMENT);
        backToGrid.setFont(backToGrid.getFont().deriveFont(11f));
        backToGrid.addActionListener(e -> gridCardLayout.show(gridCardPanel, "grid"));

        // ── Section title ──────────────────────────────────────────────────────
        JLabel title = new JLabel("Dropped by:  " + selectedItemName);
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 12f));
        title.setAlignmentX(LEFT_ALIGNMENT);

        JLabel hint = new JLabel("Click a monster to see its locations");
        hint.setForeground(new Color(120, 120, 120));
        hint.setFont(hint.getFont().deriveFont(10f));
        hint.setAlignmentX(LEFT_ALIGNMENT);

        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setForeground(new Color(60, 60, 60));

        usedInListPanel.add(backToGrid);
        usedInListPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        usedInListPanel.add(title);
        usedInListPanel.add(Box.createRigidArea(new Dimension(0, 2)));
        usedInListPanel.add(hint);
        usedInListPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        usedInListPanel.add(sep);
        usedInListPanel.add(Box.createRigidArea(new Dimension(0, 6)));

        // ── Drop rows ──────────────────────────────────────────────────────────
        for (DropEntry drop : cachedDropEntries) {
            usedInListPanel.add(buildDropRow(drop));
            usedInListPanel.add(Box.createRigidArea(new Dimension(0, 3)));
        }

        usedInListPanel.revalidate();
        usedInListPanel.repaint();
        gridCardLayout.show(gridCardPanel, "usedIn");
    }

    /**
     * One drop-source row: monster name in bold on line 1,
     * "Lv.N  ·  qty  ·  rate" in gray on line 2.
     *
     * Hoverable (background highlight) and clickable — clicking opens the
     * in-plugin monster location view.
     */
    private JPanel buildDropRow(DropEntry drop) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        row.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Detail line: "Lv. 50  ·  1–3  ·  1/128"
        StringBuilder detail = new StringBuilder();
        String lvl  = drop.getLevel();
        String qty  = drop.getQuantity();
        String rate = drop.getRate();
        if (lvl  != null && !lvl.isEmpty()  && !lvl.equals("?"))  detail.append("Lv. ").append(lvl);
        if (qty  != null && !qty.isEmpty()  && !qty.equals("?")) {
            if (detail.length() > 0) detail.append("  ·  ");
            detail.append(qty);
        }
        if (rate != null && !rate.isEmpty() && !rate.equals("?")) {
            if (detail.length() > 0) detail.append("  ·  ");
            detail.append(rate);
        }

        // Escape HTML special characters so angle-brackets in names don't break the label
        String safeName   = escHtml(drop.getSource());
        String safeDetail = detail.length() > 0 ? escHtml(detail.toString()) : "?";

        // contains()→false so mouse events reach the row panel, not the label
        JLabel lbl = new JLabel(String.format(
            "<html><b>%s</b><br><font color='#999999'>%s</font></html>",
            safeName, safeDetail))
        {
            @Override public boolean contains(int x, int y) { return false; }
        };
        lbl.setForeground(Color.WHITE);
        lbl.setFont(lbl.getFont().deriveFont(11f));

        // Hover effect + click → show monster location panel inside the plugin
        String monsterName = drop.getSource();
        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showMonsterDetail(monsterName);
            }
            @Override
            public void mouseEntered(MouseEvent e) {
                row.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
                row.repaint();
            }
            @Override
            public void mouseExited(MouseEvent e) {
                row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                row.repaint();
            }
        });

        row.add(lbl, BorderLayout.CENTER);
        return row;
    }

    // ── Recently viewed history ────────────────────────────────────────────────

    /**
     * Builds the empty history bar.  Hidden until the first item is loaded;
     * then populated by {@link #updateHistory}.
     */
    private JPanel buildHistoryBar() {
        JPanel bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.Y_AXIS));
        bar.setBackground(ColorScheme.DARK_GRAY_COLOR);
        bar.setVisible(false);

        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setForeground(new Color(50, 50, 50));

        JLabel lbl = new JLabel("Recent:");
        lbl.setForeground(new Color(120, 120, 120));
        lbl.setFont(lbl.getFont().deriveFont(10f));
        lbl.setAlignmentX(LEFT_ALIGNMENT);

        // Icon row — WrapLayout so icons flow onto a second line if the sidebar is narrow
        JPanel icons = new JPanel(new WrapLayout(FlowLayout.LEFT, 2, 2));
        icons.setBackground(ColorScheme.DARK_GRAY_COLOR);
        icons.setName("historyIcons"); // used to find this child in updateHistory()

        bar.add(Box.createRigidArea(new Dimension(0, 3)));
        bar.add(sep);
        bar.add(Box.createRigidArea(new Dimension(0, 3)));
        bar.add(lbl);
        bar.add(Box.createRigidArea(new Dimension(0, 2)));
        bar.add(icons);
        return bar;
    }

    /**
     * Prepends {@code item} to the recently-viewed list (deduplicating it if
     * already present), trims to {@link #MAX_HISTORY}, then rebuilds the icon row.
     * Must be called on the EDT.
     */
    private void updateHistory(SearchItem item) {
        // Move to front if already present, otherwise just prepend
        recentItems.removeIf(i -> i.id == item.id);
        recentItems.addFirst(item);
        while (recentItems.size() > MAX_HISTORY) recentItems.removeLast();

        // Find the icon container (named "historyIcons") and repopulate it
        JPanel icons = findHistoryIcons();
        if (icons == null) return;

        icons.removeAll();
        for (SearchItem recent : recentItems) icons.add(buildHistoryCell(recent));

        historyPanel.setVisible(true);
        historyPanel.revalidate();
        historyPanel.repaint();
    }

    /** Returns the icon-row panel nested inside {@link #historyPanel}. */
    private JPanel findHistoryIcons() {
        for (Component c : historyPanel.getComponents()) {
            if (c instanceof JPanel && "historyIcons".equals(((JPanel) c).getName())) {
                return (JPanel) c;
            }
        }
        return null;
    }

    /**
     * Builds a 32×32 history cell — identical to a grid cell but smaller,
     * and clicking it calls {@link #onItemSelected} rather than modifying the
     * nav stack (the user is just jumping to a known item).
     */
    private JPanel buildHistoryCell(SearchItem item) {
        JPanel cell = new JPanel(new BorderLayout());
        cell.setPreferredSize(new Dimension(32, 32));
        cell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        cell.setBorder(BorderFactory.createLineBorder(new Color(25, 25, 25), 1));
        cell.setToolTipText(item.name);
        cell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel iconLabel = new JLabel() {
            @Override public boolean contains(int x, int y) { return false; }
        };
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setVerticalAlignment(SwingConstants.CENTER);
        AsyncBufferedImage img = itemManager.getImage(item.id);
        if (img != null) img.addTo(iconLabel);
        cell.add(iconLabel, BorderLayout.CENTER);

        cell.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { onItemSelected(item); }
            @Override public void mouseEntered(MouseEvent e) {
                cell.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR); cell.repaint();
            }
            @Override public void mouseExited(MouseEvent e) {
                cell.setBackground(ColorScheme.DARKER_GRAY_COLOR); cell.repaint();
            }
        });

        return cell;
    }

    // ── Monster detail (location view) ────────────────────────────────────────

    /**
     * Tracks which monster the background fetch was started for so we can
     * discard stale results if the user navigates away before it completes.
     */
    private volatile String pendingMonsterName = "";

    /**
     * Switches the top area to a loading placeholder, kicks off a background
     * wiki fetch for {@code monsterName}, then renders the result.
     * Uses the same "usedIn" card that drops / used-to-craft share.
     */
    private void showMonsterDetail(String monsterName) {
        pendingMonsterName = monsterName;

        // ── Immediate loading state ────────────────────────────────────────────
        usedInListPanel.removeAll();

        JButton back = makeBackToDropsButton();
        JLabel  nameLoading = new JLabel(monsterName);
        nameLoading.setForeground(Color.WHITE);
        nameLoading.setFont(nameLoading.getFont().deriveFont(Font.BOLD, 12f));
        nameLoading.setAlignmentX(LEFT_ALIGNMENT);

        JLabel loading = new JLabel("Loading location data…");
        loading.setForeground(new Color(150, 150, 150));
        loading.setFont(loading.getFont().deriveFont(11f));
        loading.setAlignmentX(LEFT_ALIGNMENT);

        usedInListPanel.add(back);
        usedInListPanel.add(Box.createRigidArea(new Dimension(0, 6)));
        usedInListPanel.add(nameLoading);
        usedInListPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        usedInListPanel.add(loading);
        usedInListPanel.revalidate();
        usedInListPanel.repaint();
        gridCardLayout.show(gridCardPanel, "usedIn");

        // ── Background fetch ───────────────────────────────────────────────────
        executor.submit(() -> {
            Optional<MonsterInfo> info = monsterService.getMonsterInfo(monsterName);
            // Only update if the user hasn't navigated to a different monster
            if (monsterName.equals(pendingMonsterName)) {
                SwingUtilities.invokeLater(() -> populateMonsterDetail(monsterName, info));
            }
        });
    }

    /**
     * Re-populates {@link #usedInListPanel} with the fully-loaded monster
     * location data.  Called on the EDT after the background fetch completes.
     */
    private void populateMonsterDetail(String monsterName, Optional<MonsterInfo> infoOpt) {
        usedInListPanel.removeAll();

        // ── Back button ────────────────────────────────────────────────────────
        usedInListPanel.add(makeBackToDropsButton());
        usedInListPanel.add(Box.createRigidArea(new Dimension(0, 6)));

        // ── Monster name + combat level ────────────────────────────────────────
        String header = infoOpt.isPresent() && infoOpt.get().combatLevel > 0
            ? monsterName + "  (Lv. " + infoOpt.get().combatLevel + ")"
            : monsterName;

        JLabel lblMonsterName = new JLabel(header);
        lblMonsterName.setForeground(Color.WHITE);
        lblMonsterName.setFont(lblMonsterName.getFont().deriveFont(Font.BOLD, 12f));
        lblMonsterName.setAlignmentX(LEFT_ALIGNMENT);

        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setForeground(new Color(60, 60, 60));

        usedInListPanel.add(lblMonsterName);
        usedInListPanel.add(Box.createRigidArea(new Dimension(0, 6)));
        usedInListPanel.add(sep);
        usedInListPanel.add(Box.createRigidArea(new Dimension(0, 6)));

        // ── Location list ──────────────────────────────────────────────────────
        if (infoOpt.isPresent() && !infoOpt.get().locations.isEmpty()) {
            JLabel locHeader = new JLabel("Locations:");
            locHeader.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            locHeader.setFont(locHeader.getFont().deriveFont(Font.BOLD, 11f));
            locHeader.setAlignmentX(LEFT_ALIGNMENT);
            usedInListPanel.add(locHeader);
            usedInListPanel.add(Box.createRigidArea(new Dimension(0, 4)));

            for (String loc : infoOpt.get().locations) {
                JLabel locLabel = new JLabel("· " + loc);
                locLabel.setForeground(new Color(200, 200, 200));
                locLabel.setFont(locLabel.getFont().deriveFont(11f));
                locLabel.setAlignmentX(LEFT_ALIGNMENT);
                usedInListPanel.add(locLabel);
                usedInListPanel.add(Box.createRigidArea(new Dimension(0, 3)));
            }
        } else {
            JLabel noData = new JLabel("Location data not available.");
            noData.setForeground(new Color(150, 150, 150));
            noData.setFont(noData.getFont().deriveFont(11f));
            noData.setAlignmentX(LEFT_ALIGNMENT);
            usedInListPanel.add(noData);
        }

        // ── Path-to-monster button (requires Shortest Path plugin) ────────────
        if (infoOpt.isPresent()) {
            MonsterInfo info = infoOpt.get();
            if (info.mapX > 0 && info.mapY > 0) {
                WorldPoint target = new WorldPoint(
                    info.mapX, info.mapY, Math.max(0, info.mapPlane));

                usedInListPanel.add(Box.createRigidArea(new Dimension(0, 8)));

                // Tile-coordinate hint in small gray text
                JLabel coordHint = new JLabel(String.format(
                    "Tile: %d, %d  (plane %d)", info.mapX, info.mapY, Math.max(0, info.mapPlane)));
                coordHint.setForeground(new Color(110, 110, 110));
                coordHint.setFont(coordHint.getFont().deriveFont(10f));
                coordHint.setAlignmentX(LEFT_ALIGNMENT);
                usedInListPanel.add(coordHint);
                usedInListPanel.add(Box.createRigidArea(new Dimension(0, 4)));

                JButton pathBtn = new JButton("→  Path to Monster");
                pathBtn.setForeground(new Color(100, 210, 100));
                pathBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                pathBtn.setOpaque(false);
                pathBtn.setBorderPainted(false);
                pathBtn.setFocusPainted(false);
                pathBtn.setContentAreaFilled(false);
                pathBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                pathBtn.setAlignmentX(LEFT_ALIGNMENT);
                pathBtn.setFont(pathBtn.getFont().deriveFont(Font.BOLD, 11f));
                pathBtn.setToolTipText("Requires the 'Shortest Path' plugin from the Plugin Hub");
                pathBtn.addActionListener(e ->
                    // setTarget must be called on the client thread
                    clientThread.invokeLater(() -> {
                        boolean ok = tryShortestPath(target);
                        if (!ok) {
                            SwingUtilities.invokeLater(() ->
                                JOptionPane.showMessageDialog(
                                    pathBtn,
                                    "Install the 'Shortest Path' plugin from the\n"
                                    + "RuneLite Plugin Hub to use automatic pathfinding.",
                                    "Shortest Path not found",
                                    JOptionPane.INFORMATION_MESSAGE));
                        }
                    })
                );
                usedInListPanel.add(pathBtn);
            }
        }

        // ── Open Wiki button ───────────────────────────────────────────────────
        usedInListPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        JButton wiki = new JButton("Open Wiki page");
        wiki.setForeground(new Color(100, 180, 255));
        wiki.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        wiki.setBorderPainted(false);
        wiki.setFocusPainted(false);
        wiki.setContentAreaFilled(false);
        wiki.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        wiki.setAlignmentX(LEFT_ALIGNMENT);
        wiki.addActionListener(e -> {
            try {
                String wikiName = monsterName.replace(" ", "_");
                Desktop.getDesktop().browse(
                    new URI("https://oldschool.runescape.wiki/w/" + wikiName));
            } catch (Exception ex) {
                log.warn("[JEI] Could not open wiki for '{}': {}", monsterName, ex.getMessage());
            }
        });
        usedInListPanel.add(wiki);

        usedInListPanel.revalidate();
        usedInListPanel.repaint();
    }

    /**
     * Attempts to trigger the Shortest Path plugin to route the player to
     * {@code target}.
     *
     * <p>Uses reflection to call {@code ShortestPathPlugin.setTarget(WorldPoint)}
     * so there is no hard compile-time dependency on the optional community plugin.
     * Must be called on the <b>client thread</b>.
     *
     * @return {@code true} if the plugin was found and the call succeeded
     */
    private boolean tryShortestPath(WorldPoint target) {
        net.runelite.client.plugins.Plugin sp = pluginManager.getPlugins().stream()
            .filter(p -> p.getClass().getSimpleName().equals("ShortestPathPlugin"))
            .findFirst().orElse(null);

        if (sp == null) {
            log.debug("[JEI] Shortest Path plugin not installed/enabled");
            return false;
        }

        try {
            Method setTarget = sp.getClass().getMethod("setTarget", WorldPoint.class);
            setTarget.setAccessible(true);
            setTarget.invoke(sp, target);
            log.debug("[JEI] Shortest Path target set to {}", target);
            return true;
        } catch (NoSuchMethodException e) {
            log.debug("[JEI] ShortestPathPlugin has no setTarget(WorldPoint) method");
        } catch (Exception e) {
            log.warn("[JEI] Failed to invoke ShortestPath.setTarget: {}", e.getMessage());
        }
        return false;
    }

    /** Builds a "← Back to drops" button that re-shows the drops expanded list. */
    private JButton makeBackToDropsButton() {
        JButton btn = new JButton("← Back to drops");
        btn.setForeground(new Color(100, 180, 255));
        btn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        btn.setOpaque(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setAlignmentX(LEFT_ALIGNMENT);
        btn.setFont(btn.getFont().deriveFont(11f));
        btn.addActionListener(e -> showDropsExpanded());
        return btn;
    }

    // ── Favourites ─────────────────────────────────────────────────────────────

    /**
     * Reads the comma-separated list of favourite item IDs from RuneLite config
     * and populates {@link #favouriteIds}.  Called once during {@link #init()}.
     */
    private void loadFavourites() {
        String csv = configManager.getConfiguration("osrjei", "favourites");
        if (csv == null || csv.isEmpty()) return;
        for (String part : csv.split(",")) {
            try {
                favouriteIds.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) { }
        }
        log.debug("[JEI] Loaded {} favourite(s)", favouriteIds.size());
    }

    /**
     * Persists {@link #favouriteIds} to RuneLite config as a comma-separated
     * string of item IDs.  Call after every change to the set.
     */
    private void saveFavourites() {
        String csv = favouriteIds.stream()
            .map(String::valueOf)
            .collect(Collectors.joining(","));
        configManager.setConfiguration("osrjei", "favourites", csv);
    }

    /** Toggles the given item's favourite status, updates the star button, and saves. */
    private void toggleFavourite(SearchItem item) {
        if (favouriteIds.contains(item.id)) {
            favouriteIds.remove(item.id);
        } else {
            favouriteIds.add(item.id);
        }
        updateStarButton(item);
        saveFavourites();

        // If the favourites grid is open, refresh it to reflect the change
        if (favToggle != null && favToggle.isSelected()) {
            List<SearchItem> favItems = buildFavouritesList();
            SwingUtilities.invokeLater(() -> populateGrid(favItems));
        }
    }

    /**
     * Updates the star button text and tooltip to reflect whether
     * {@code item} is currently in the favourites set.
     */
    private void updateStarButton(SearchItem item) {
        if (favouriteIds.contains(item.id)) {
            starButton.setText("★");
            starButton.setToolTipText("Remove from favourites");
        } else {
            starButton.setText("☆");
            starButton.setToolTipText("Add to favourites");
        }
    }

    /**
     * Builds the ordered list of favourite items by looking up each stored ID
     * in {@link #idIndex}.  O(k) where k = number of favourites.
     * Items not found (index not ready yet, or stale ID) are silently skipped.
     */
    private List<SearchItem> buildFavouritesList() {
        Map<Integer, SearchItem> idx = idIndex; // stable snapshot
        List<SearchItem> result = new ArrayList<>();
        for (int id : favouriteIds) {
            SearchItem si = idx.get(id);
            if (si != null) result.add(si);
        }
        return result;
    }

    // ── In-game right-click lookup ─────────────────────────────────────────────

    /**
     * Called by {@link JEIPlugin} when the player clicks "JEI Lookup" from an
     * item's right-click menu.  Must be called on the EDT.
     *
     * <p>Noted items (e.g. noted iron bar) share a name with the base item but
     * have a different ID.  We resolve the base item ID on the client thread
     * before searching the index so the lookup always succeeds for noted stacks.
     *
     * @param itemId the item ID from the menu event
     */
    public void lookupItem(int itemId) {
        // Resolve noted → base item on the client thread, then search + show on EDT
        clientThread.invokeLater(() -> {
            int resolvedId = itemId;
            try {
                net.runelite.api.ItemComposition comp =
                    itemManager.getItemComposition(itemId);
                // getNote() returns 799 if the item is a note; getLinkedNoteId()
                // gives the un-noted (base) item's ID
                if (comp != null && comp.getNote() == 799) {
                    resolvedId = comp.getLinkedNoteId();
                }
            } catch (Exception ignored) { }

            final int finalId = resolvedId;
            SwingUtilities.invokeLater(() -> {
                // O(1) lookup via idIndex
                SearchItem item = idIndex.get(finalId);
                if (item != null) {
                    navStack.clear();
                    backButton.setVisible(false);
                    gridCardLayout.show(gridCardPanel, "grid");
                    loadItemDetail(item);
                } else {
                    log.warn("[JEI] Item ID {} not found in item index", finalId);
                }
            });
        });
    }

    // ── Utilities ──────────────────────────────────────────────────────────────

    /**
     * Finds a SearchItem by exact (case-insensitive) name.
     * O(1) via {@link #nameIndex}; was previously O(n) linear scan.
     */
    private SearchItem findByName(String name) {
        if (name == null || name.isEmpty()) return null;
        return nameIndex.get(name.toLowerCase(Locale.ROOT));
    }

    private static String escHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void openWiki() {
        if (selectedItemName.isEmpty()) return;
        try {
            String wikiName = selectedItemName.replace(" ", "_");
            Desktop.getDesktop().browse(new URI("https://oldschool.runescape.wiki/w/" + wikiName));
        } catch (Exception e) {
            log.warn("[JEI] Could not open wiki page: {}", e.getMessage());
        }
    }
}
