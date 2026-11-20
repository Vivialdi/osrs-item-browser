package com.osr.jei;

import com.osr.jei.crafting.BuildExpander;
import com.osr.jei.crafting.RecipePages;
import com.osr.jei.drops.DropRate;
import com.osr.jei.drops.DropSort;
import com.osr.jei.drops.DropSorter;
import com.osr.jei.equipment.EquipmentCompare;
import com.osr.jei.equipment.GearSlot;
import com.osr.jei.equipment.WornGear;
import com.osr.jei.ironman.DetailLayout;
import com.osr.jei.ironman.IronmanAccounts;
import com.osr.jei.ironman.TradeCopy;
import com.osr.jei.model.DropEntry;
import com.osr.jei.model.EquipmentStats;
import com.osr.jei.model.MonsterInfo;
import com.osr.jei.model.SearchItem;
import com.osr.jei.model.ShopEntry;
import com.osr.jei.requirements.ItemRequirements;
import com.osr.jei.requirements.Mark;
import com.osr.jei.requirements.PlayerProgress;
import com.osr.jei.requirements.ProgressSnapshot;
import com.osr.jei.requirements.RequirementJudge;
import com.osr.jei.owned.OwnedItems;
import com.osr.jei.owned.OwnedText;
import com.osr.jei.pricing.Alchemy;
import com.osr.jei.pricing.BuyLimitSnapshot;
import com.osr.jei.pricing.MarketQuote;
import com.osr.jei.pricing.PriceQuote;
import com.osr.jei.search.ItemSearch;
import com.osr.jei.search.ItemTraits;
import com.osr.jei.search.SearchCriteria;
import com.osr.jei.service.BuyLimitService;
import com.osr.jei.service.ItemDetails;
import com.osr.jei.service.ItemDetailsService;
import com.osr.jei.service.MonsterService;
import com.osr.jei.service.PriceService;
import com.osr.jei.ui.RecipeTreeView;
import com.osr.jei.ui.SplitLayout;
import com.osr.jei.ui.UiStyle;
import com.osr.jei.ui.WrappingLabel;
import com.osr.jei.wiki.RequestCancelledException;
import com.osr.jei.wiki.WikiHttp;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemStats;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.LinkBrowser;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sidebar: search grid on top, item detail below, both sharing the window height.
 * Search runs on its own thread. Wiki and price work run on a different thread,
 * debounced, and ignored when a newer selection has already started.
 */
@Slf4j
@Singleton
public class JEIPanel extends PluginPanel {

    private static final int MAX_HISTORY = 12;
    private static final int INDEX_LIMIT = 40_000;
    private static final int INDEX_CHUNK = 500;
    private static final int SEARCH_LIMIT = 200;
    private static final long DETAIL_DEBOUNCE_MS = 200;
    private static final long SEARCH_DEBOUNCE_MS = 60;

    private static final Color GOLD = new Color(220, 185, 75);
    private static final Color LINK = new Color(100, 180, 255);
    private static final Color GOOD = new Color(100, 210, 100);
    private static final Color BAD = new Color(220, 100, 100);
    private static final Color MUTED = new Color(150, 150, 150);
    private static final Color NEUTRAL = new Color(190, 190, 190);

    @Inject private ClientThread clientThread;
    @Inject private ItemManager itemManager;
    @Inject private ConfigManager configManager;
    @Inject private JEIConfig config;
    @Inject private ItemDetailsService itemDetailsService;
    @Inject private PriceService priceService;
    @Inject private BuyLimitService buyLimitService;
    @Inject private MonsterService monsterService;
    @Inject private WikiHttp wikiHttp;
    @Inject private PlayerProgress progress;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(daemon("jei-schedule"));
    private final ExecutorService searchExecutor = Executors.newSingleThreadExecutor(daemon("jei-search"));

    private final AtomicInteger searchGeneration = new AtomicInteger();
    private final AtomicInteger detailGeneration = new AtomicInteger();
    private final AtomicInteger monsterGeneration = new AtomicInteger();

    private final Object favouritesLock = new Object();
    private final Set<Integer> favouriteIds = new LinkedHashSet<>();
    private final List<Integer> recentIds = new ArrayList<>();
    private final Deque<SearchItem> navStack = new ArrayDeque<>();
    private final Map<String, SearchItem> indexingByName = new LinkedHashMap<>();
    private final Map<Integer, SearchItem> indexingById = new HashMap<>();

    private volatile boolean closed;
    private volatile List<SearchItem> allItems = Collections.emptyList();
    private volatile Map<String, SearchItem> nameIndex = Collections.emptyMap();
    private volatile Map<Integer, SearchItem> idIndex = Collections.emptyMap();
    private volatile String lastSearchQuery = "";

    private UiStyle style;
    private boolean initStarted;
    private boolean initialised;
    private volatile boolean indexPopulated;
    private volatile boolean indexScanning;
    private volatile GameState pendingGameState;
    private boolean dividerApplied;
    private int lastWrapWidth = -1;
    private ScheduledFuture<?> pendingSearch;
    private ScheduledFuture<?> pendingDetail;

    private JSplitPane split;
    private JPanel gridPanel;
    private JPanel selectedCell;
    private CardLayout gridCardLayout;
    private JPanel gridCardPanel;
    private JPanel usedInListPanel;
    private JPanel historyPanel;
    private JPanel historyIcons;
    private JLabel indexStatus;
    private JToggleButton favToggle;
    private JButton backButton;
    private JButton starButton;
    private JButton usedInButton;
    private JButton dropsButton;
    private JButton wikiButton;
    private JPanel nameRow;
    private JLabel lblName;
    private JLabel lblIronman;
    private JPanel marketBox;
    private JLabel lblPrice;
    private WrappingLabel lblMarket;
    private WrappingLabel lblOwned;
    private JLabel lblMembersWeight;
    private JLabel lblHaAlch;
    private JPanel detailPanel;
    private JPanel equipmentSection;
    private JPanel equipmentContent;
    private JPanel shopsSection;
    private JPanel shopsContent;
    private JPanel recipeSection;
    private JPanel recipeBody;

    private String selectedItemName = "";
    private SearchItem shownItem;
    private ItemDetails shownDetails;
    private boolean ironmanActive;
    private final Set<String> expandedPaths = new LinkedHashSet<>();
    private final Set<String> treeFailed = new LinkedHashSet<>();
    private boolean treeLoadInFlight;
    private ProgressSnapshot progressSnapshot = ProgressSnapshot.LOGGED_OUT;
    private WornGear wornGear = WornGear.LOGGED_OUT;
    private boolean compareOpen;
    private final Set<String> compareStarted = new LinkedHashSet<>();
    private final Set<String> compareFailed = new LinkedHashSet<>();
    private boolean syncingFilters;
    private String gridMode = "grid";
    private JComboBox<SearchCriteria.Trade> tradeCombo;
    private JComboBox<SearchCriteria.Members> membersCombo;
    private JComboBox<SearchCriteria.Equip> equipCombo;
    private JComboBox<SearchCriteria.SlotChoice> slotCombo;
    private PriceQuote shownPrice = PriceQuote.MISSING;
    private PriceQuote shownNature = PriceQuote.MISSING;
    private boolean pricesFetched;
    private OwnedItems.Snapshot ownedSnapshot = OwnedItems.Snapshot.NONE;
    private BuyLimitSnapshot shownLimits;
    private boolean limitLoadInFlight;
    private List<SearchItem> lastGridItems = Collections.emptyList();

    public JEIPanel() {
        super(false);
    }

    public boolean isClosed() {
        return closed;
    }

    public void init() {
        if (closed || initialised || initStarted) {
            return;
        }
        initStarted = true;
        style = UiStyle.from(config.fontSize(), config.fontStyle(), config.fontFamily());
        setLayout(new BorderLayout(0, 4));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        loadFavourites();
        loadRecent();

        historyPanel = buildHistoryBar();
        indexStatus = plainLabel("Waiting for the game cache\u2026", "small", MUTED);
        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.setBackground(ColorScheme.DARK_GRAY_COLOR);
        north.add(buildSearchBar());
        north.add(buildFilterBar());
        north.add(indexStatus);
        north.add(historyPanel);
        add(north, BorderLayout.NORTH);
        add(buildContent(), BorderLayout.CENTER);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                maybeApplyInitialDivider();
                int width = getWidth();
                if (Math.abs(width - lastWrapWidth) < 2) {
                    return;
                }
                lastWrapWidth = width;
                if (detailPanel != null) {
                    detailPanel.revalidate();
                }
                if (usedInListPanel != null) {
                    usedInListPanel.revalidate();
                }
            }
        });

        initialised = true;
        GameState pending = pendingGameState;
        if (pending != null) {
            ensureItemIndex(pending);
        }
        SwingUtilities.invokeLater(this::hookDividerDrag);
    }

    /**
     * Starts the item-definition scan once the game cache is available.
     * Safe to call from the client thread or the EDT. A finished index is left alone.
     */
    public void ensureItemIndex(GameState state) {
        if (closed) {
            return;
        }
        if (!initialised) {
            pendingGameState = state;
            return;
        }
        if (!ItemIndexGate.shouldStart(state, indexPopulated, indexScanning)) {
            if (!indexPopulated && !indexScanning) {
                showIndexStatus("Waiting for the game cache\u2026");
            }
            return;
        }
        synchronized (this) {
            if (indexPopulated || indexScanning) {
                return;
            }
            indexScanning = true;
        }
        showIndexStatus("Loading item index\u2026");
        clientThread.invokeLater(() -> {
            if (closed) {
                indexScanning = false;
                return;
            }
            indexingByName.clear();
            indexingById.clear();
            scanChunk(0);
        });
    }

    private void showIndexStatus(String text) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> showIndexStatus(text));
            return;
        }
        if (indexStatus == null) {
            return;
        }
        if (text == null || text.isEmpty()) {
            indexStatus.setVisible(false);
        } else {
            indexStatus.setText(text);
            indexStatus.setVisible(true);
        }
        revalidate();
        repaint();
    }

    public void shutdown() {
        closed = true;
        searchGeneration.incrementAndGet();
        detailGeneration.incrementAndGet();
        monsterGeneration.incrementAndGet();
        if (pendingSearch != null) {
            pendingSearch.cancel(false);
        }
        if (pendingDetail != null) {
            pendingDetail.cancel(false);
        }
        if (wikiHttp != null) {
            wikiHttp.shutdown();
        }
        searchExecutor.shutdownNow();
        scheduler.shutdownNow();
    }

    public void onConfigChanged(String key) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> onConfigChanged(key));
            return;
        }
        if (closed || !initialised) {
            return;
        }
        if ("favourites".equals(key) || "recent".equals(key) || "ironmanMode".equals(key)) {
            return;
        }
        if (isSearchFilterKey(key)) {
            syncFilterControls();
            refreshFilteredResults();
            return;
        }
        if ("dropSort".equals(key)) {
            if ("drops".equals(gridMode)) {
                SwingUtilities.invokeLater(this::showDropsExpanded);
            }
            return;
        }
        if ("showRequirementChecks".equals(key) || "showEquipmentCompare".equals(key)) {
            if (shownItem != null && shownDetails != null) {
                renderDetails();
            }
            return;
        }
        if ("detailRatio".equals(key)) {
            dividerApplied = true;
            applyDividerFromConfig();
            return;
        }
        style = UiStyle.from(config.fontSize(), config.fontStyle(), config.fontFamily());
        style.applyTree(this);
        applyResizeWeight();
        dividerApplied = true;
        applyDividerFromConfig();
        if (shownItem != null && config.showPrices() && !pricesFetched) {
            loadItemDetail(shownItem, false);
        } else if (shownItem != null) {
            renderDetails();
        }
        revalidate();
        repaint();
    }

    /**
     * Applies the effective ironman layout. No-ops when the flag is unchanged,
     * and never rebuilds the item index.
     */
    public void onIronmanChanged(boolean active) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> onIronmanChanged(active));
            return;
        }
        if (!IronmanAccounts.shouldRefreshDetail(ironmanActive, active)) {
            return;
        }
        ironmanActive = active;
        if (!initialised) {
            return;
        }
        updateIronmanBadge();
        applySectionOrder();
        if (shownItem != null && shownDetails != null) {
            renderDetails();
        }
    }

    /**
     * Replaces the owned snapshot. The Swing tree is touched only from the
     * event thread, and only when the detail view is already showing an item.
     */
    public void onOwnedChanged(OwnedItems.Snapshot snapshot) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> onOwnedChanged(snapshot));
            return;
        }
        if (closed) {
            return;
        }
        OwnedItems.Snapshot next = snapshot == null ? OwnedItems.Snapshot.NONE : snapshot;
        if (next.equals(ownedSnapshot)) {
            return;
        }
        ownedSnapshot = next;
        if (!initialised) {
            return;
        }
        if (shownItem != null && shownDetails != null) {
            renderDetails();
        } else {
            updateOwnedHeader();
        }
    }

    /** Real levels and quest states. Logged-out snapshots draw requirements with no judgment. */
    public void onProgress(ProgressSnapshot snapshot) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> onProgress(snapshot));
            return;
        }
        if (closed) {
            return;
        }
        ProgressSnapshot next = snapshot == null ? ProgressSnapshot.LOGGED_OUT : snapshot;
        if (next.equals(progressSnapshot)) {
            return;
        }
        progressSnapshot = next;
        if (initialised && shownItem != null && shownDetails != null) {
            renderDetails();
        }
    }

    /** Worn item ids. Wiki pages for those items load only while the comparison is open. */
    public void onWornChanged(WornGear gear) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> onWornChanged(gear));
            return;
        }
        if (closed) {
            return;
        }
        WornGear next = gear == null ? WornGear.LOGGED_OUT : gear;
        if (next.equals(wornGear)) {
            return;
        }
        wornGear = next;
        if (initialised && compareOpen && shownItem != null && shownDetails != null) {
            renderDetails();
        }
    }

    public void lookupItem(int itemId) {
        clientThread.invokeLater(() -> {
            int resolved = itemId;
            try {
                resolved = itemManager.canonicalize(itemId);
            } catch (Exception ignored) {
                resolved = itemId;
            }
            int canonical = resolved;
            SwingUtilities.invokeLater(() -> {
                if (closed || !initialised) {
                    return;
                }
                SearchItem item = idIndex.get(canonical);
                if (item == null) {
                    item = idIndex.get(itemId);
                }
                if (item == null) {
                    log.debug("[JEI] Item id {} is not in the index yet", canonical);
                    return;
                }
                navStack.clear();
                backButton.setVisible(false);
                showGridCard();
                loadItemDetail(item, true);
            });
        });
    }

    private JPanel buildSearchBar() {
        IconTextField searchBar = new IconTextField();
        searchBar.setIcon(IconTextField.Icon.SEARCH);
        searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        searchBar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
        searchBar.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { onSearch(searchBar.getText()); }
            @Override public void removeUpdate(DocumentEvent e) { onSearch(searchBar.getText()); }
            @Override public void changedUpdate(DocumentEvent e) { onSearch(searchBar.getText()); }
        });

        favToggle = new JToggleButton("\u2606");
        style.apply(favToggle, "button");
        favToggle.setForeground(GOLD);
        favToggle.setOpaque(false);
        favToggle.setBorderPainted(false);
        favToggle.setFocusPainted(false);
        favToggle.setContentAreaFilled(false);
        favToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        favToggle.setToolTipText("Show favourites");
        favToggle.setPreferredSize(new Dimension(28, 30));
        favToggle.addActionListener(e -> {
            if (favToggle.isSelected()) {
                favToggle.setText("\u2605");
                favToggle.setToolTipText("Show all items");
                lastGridItems = buildFavouritesList();
                populateGrid(lastGridItems);
            } else {
                favToggle.setText("\u2606");
                favToggle.setToolTipText("Show favourites");
                onSearch(lastSearchQuery);
            }
        });

        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBackground(ColorScheme.DARK_GRAY_COLOR);
        row.add(searchBar, BorderLayout.CENTER);
        row.add(favToggle, BorderLayout.EAST);
        return row;
    }

    private JPanel buildFilterBar() {
        JPanel bar = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 2)) {
            @Override
            public Dimension getMaximumSize() {
                Dimension preferred = getPreferredSize();
                return new Dimension(Integer.MAX_VALUE, preferred.height);
            }
        };
        bar.setBackground(ColorScheme.DARK_GRAY_COLOR);
        bar.setAlignmentX(LEFT_ALIGNMENT);
        tradeCombo = filterCombo(SearchCriteria.Trade.values(), config.filterTradeable(), "filterTradeable");
        membersCombo = filterCombo(SearchCriteria.Members.values(), config.filterMembers(), "filterMembers");
        equipCombo = filterCombo(SearchCriteria.Equip.values(), config.filterEquipable(), "filterEquipable");
        slotCombo = filterCombo(SearchCriteria.SlotChoice.values(), config.filterSlot(), "filterSlot");
        bar.add(tradeCombo);
        bar.add(membersCombo);
        bar.add(equipCombo);
        bar.add(slotCombo);
        return bar;
    }

    private <E> JComboBox<E> filterCombo(E[] values, E selected, String key) {
        JComboBox<E> combo = new JComboBox<>(values);
        style.apply(combo, "small");
        combo.setSelectedItem(selected == null ? values[0] : selected);
        combo.addActionListener(e -> {
            if (syncingFilters) {
                return;
            }
            configManager.setConfiguration("osrjei", key, combo.getSelectedItem());
        });
        return combo;
    }

    private void syncFilterControls() {
        if (tradeCombo == null) {
            return;
        }
        syncingFilters = true;
        try {
            tradeCombo.setSelectedItem(config.filterTradeable());
            membersCombo.setSelectedItem(config.filterMembers());
            equipCombo.setSelectedItem(config.filterEquipable());
            slotCombo.setSelectedItem(config.filterSlot());
        } finally {
            syncingFilters = false;
        }
    }

    private static boolean isSearchFilterKey(String key) {
        return "filterTradeable".equals(key)
            || "filterMembers".equals(key)
            || "filterEquipable".equals(key)
            || "filterSlot".equals(key);
    }

    private SearchCriteria searchCriteria() {
        return new SearchCriteria(
            config.filterTradeable(),
            config.filterMembers(),
            config.filterEquipable(),
            config.filterSlot()
        );
    }

    private void refreshFilteredResults() {
        if (favToggle != null && favToggle.isSelected()) {
            lastGridItems = buildFavouritesList();
            populateGrid(lastGridItems);
            return;
        }
        if (lastSearchQuery != null && !lastSearchQuery.isEmpty()) {
            onSearch(lastSearchQuery);
        }
    }

    private JComponent buildContent() {
        gridPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 2, 2));
        gridPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        JScrollPane gridScroll = scroll(gridPanel);

        usedInListPanel = new JPanel();
        usedInListPanel.setLayout(new BoxLayout(usedInListPanel, BoxLayout.Y_AXIS));
        usedInListPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        usedInListPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        JScrollPane usedScroll = scroll(usedInListPanel);

        gridCardLayout = new CardLayout();
        gridCardPanel = new JPanel(gridCardLayout);
        gridCardPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        gridCardPanel.add(gridScroll, "grid");
        gridCardPanel.add(usedScroll, "usedIn");
        gridCardPanel.setMinimumSize(new Dimension(0, 72));

        JScrollPane detailScroll = scroll(buildDetailPanel());
        detailScroll.setMinimumSize(new Dimension(0, 120));
        detailScroll.setPreferredSize(new Dimension(PANEL_WIDTH, 180));

        split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, gridCardPanel, detailScroll);
        split.setContinuousLayout(true);
        split.setBorder(null);
        split.setDividerSize(6);
        split.setBackground(ColorScheme.DARK_GRAY_COLOR);
        split.setResizeWeight(0.55);
        applyResizeWeight();
        return split;
    }

    private JPanel buildDetailPanel() {
        detailPanel = new JPanel();
        detailPanel.setLayout(new BoxLayout(detailPanel, BoxLayout.Y_AXIS));
        detailPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        detailPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        backButton = linkButton("\u2190 Back", "button");
        backButton.addActionListener(e -> navigateBack());
        backButton.setVisible(false);

        lblName = new WrappingLabel();
        style.apply(lblName, "title");
        lblName.setForeground(Color.WHITE);
        lblName.setText("Search for an item above");

        starButton = linkButton("\u2606", "button");
        starButton.setForeground(GOLD);
        starButton.setMargin(new Insets(0, 4, 0, 0));
        starButton.setToolTipText("Add to favourites");
        starButton.setVisible(false);
        starButton.addActionListener(e -> {
            SearchItem current = findByName(selectedItemName);
            if (current != null) {
                toggleFavourite(current);
            }
        });

        lblIronman = plainLabel("Ironman", "small", new Color(140, 175, 140));
        lblIronman.setVisible(false);

        JPanel nameStack = new JPanel();
        nameStack.setLayout(new BoxLayout(nameStack, BoxLayout.Y_AXIS));
        nameStack.setOpaque(false);
        nameStack.setAlignmentX(LEFT_ALIGNMENT);
        nameStack.add(lblName);
        nameStack.add(lblIronman);

        nameRow = new JPanel(new BorderLayout(4, 0)) {
            @Override
            public Dimension getMaximumSize() {
                Dimension preferred = getPreferredSize();
                return new Dimension(Integer.MAX_VALUE, preferred.height);
            }
        };
        nameRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        nameRow.setAlignmentX(LEFT_ALIGNMENT);
        nameRow.add(nameStack, BorderLayout.CENTER);
        nameRow.add(starButton, BorderLayout.EAST);

        lblMembersWeight = plainLabel(" ", "small", MUTED);
        lblOwned = new WrappingLabel();
        style.apply(lblOwned, "small");
        lblOwned.setForeground(MUTED);
        lblOwned.setVisible(false);
        lblPrice = plainLabel(" ", "price", GOLD);
        lblMarket = new WrappingLabel();
        style.apply(lblMarket, "small");
        lblMarket.setForeground(NEUTRAL);
        lblMarket.setVisible(false);
        marketBox = verticalBox();
        marketBox.add(lblPrice);
        marketBox.add(lblMarket);
        lblHaAlch = plainLabel(" ", "body", GOOD);

        equipmentSection = section("Equipment");
        equipmentContent = verticalBox();
        equipmentSection.add(equipmentContent);

        shopsSection = section("Shop sources");
        shopsContent = verticalBox();
        shopsSection.add(shopsContent);

        recipeSection = verticalBox();
        recipeSection.setVisible(false);
        recipeBody = verticalBox();
        recipeSection.add(recipeBody);

        usedInButton = linkButton("Used to craft", "bodyBold");
        usedInButton.setForeground(GOOD);
        usedInButton.addActionListener(e -> showUsedInExpanded());
        usedInButton.setVisible(false);

        dropsButton = linkButton("Dropped by", "bodyBold");
        dropsButton.setForeground(GOOD);
        dropsButton.addActionListener(e -> showDropsExpanded());
        dropsButton.setVisible(false);

        wikiButton = linkButton("Open Wiki page", "button");
        wikiButton.setForeground(LINK);
        wikiButton.addActionListener(e -> openWiki(selectedItemName));
        wikiButton.setVisible(false);

        applySectionOrder();
        return detailPanel;
    }

    private void applySectionOrder() {
        if (detailPanel == null) {
            return;
        }
        detailPanel.removeAll();
        detailPanel.add(backButton);
        detailPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        detailPanel.add(nameRow);
        detailPanel.add(Box.createRigidArea(new Dimension(0, 3)));
        detailPanel.add(lblMembersWeight);
        detailPanel.add(lblOwned);
        detailPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        for (String section : DetailLayout.order(ironmanActive)) {
            detailPanel.add(sectionComponent(section));
            detailPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        }
        detailPanel.add(wikiButton);
        detailPanel.revalidate();
        detailPanel.repaint();
    }

    private JComponent sectionComponent(String section) {
        switch (section) {
            case DetailLayout.GE:
                return marketBox;
            case DetailLayout.ALCH:
                return lblHaAlch;
            case DetailLayout.EQUIPMENT:
                return equipmentSection;
            case DetailLayout.SHOPS:
                return shopsSection;
            case DetailLayout.USED_IN:
                return usedInButton;
            case DetailLayout.DROPS:
                return dropsButton;
            case DetailLayout.CRAFTING:
                return recipeSection;
            default:
                return new JPanel();
        }
    }

    private void scanChunk(int start) {
        if (closed) {
            return;
        }
        int end = Math.min(INDEX_LIMIT, start + INDEX_CHUNK - 1);
        for (int id = start; id <= end; id++) {
            try {
                ItemComposition comp = itemManager.getItemComposition(id);
                if (comp == null) {
                    continue;
                }
                String name = comp.getName();
                if (name == null || name.isEmpty() || name.equalsIgnoreCase("null") || name.charAt(0) == '<') {
                    continue;
                }
                String key = name.toLowerCase(java.util.Locale.ROOT);
                SearchItem item = indexingByName.get(key);
                if (item == null) {
                    item = new SearchItem(id, name, comp.isMembers(), comp.getHaPrice(), traitsFor(id, comp));
                    indexingByName.put(key, item);
                }
                indexingById.put(id, item);
            } catch (Exception ignored) {
                // sparse ids throw or return placeholders; keep scanning
            }
        }
        publishIndex(end >= INDEX_LIMIT);
        if (end < INDEX_LIMIT && !closed) {
            clientThread.invokeLater(() -> scanChunk(end + 1));
        }
    }

    private void publishIndex(boolean finished) {
        List<SearchItem> items = new ArrayList<>(indexingByName.values());
        Map<String, SearchItem> names = new HashMap<>(indexingByName);
        Map<Integer, SearchItem> ids = new HashMap<>(indexingById);
        allItems = items;
        nameIndex = names;
        idIndex = ids;
        if (finished) {
            if (items.isEmpty()) {
                indexScanning = false;
                log.info("[JEI] Item index was empty; waiting for the game cache");
                showIndexStatus("Waiting for the game cache\u2026");
                return;
            }
            indexPopulated = true;
            indexScanning = false;
            log.info("[JEI] Item index ready — {} unique items", items.size());
        }
        SwingUtilities.invokeLater(() -> {
            if (closed) {
                return;
            }
            if (finished) {
                showIndexStatus(null);
            }
            rebuildHistory();
            if (!lastSearchQuery.isEmpty()) {
                onSearch(lastSearchQuery);
            }
        });
    }

    private void onSearch(String query) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> onSearch(query));
            return;
        }
        if (favToggle != null && favToggle.isSelected() && query != null && !query.trim().isEmpty()) {
            favToggle.setSelected(false);
            favToggle.setText("\u2606");
            favToggle.setToolTipText("Show favourites");
        }
        if (query == null || query.trim().length() < 2) {
            lastSearchQuery = "";
            searchGeneration.incrementAndGet();
            if (pendingSearch != null) {
                pendingSearch.cancel(false);
            }
            gridPanel.removeAll();
            selectedCell = null;
            lastGridItems = Collections.emptyList();
            gridPanel.revalidate();
            gridPanel.repaint();
            return;
        }
        String lower = query.trim().toLowerCase(java.util.Locale.ROOT);
        lastSearchQuery = lower;
        int gen = searchGeneration.incrementAndGet();
        if (pendingSearch != null) {
            pendingSearch.cancel(false);
        }
        pendingSearch = scheduler.schedule(() -> searchExecutor.submit(() -> {
            if (gen != searchGeneration.get()) {
                return;
            }
            List<SearchItem> results = ItemSearch.rank(allItems, lower, SEARCH_LIMIT, searchCriteria());
            SwingUtilities.invokeLater(() -> {
                if (gen != searchGeneration.get()) {
                    return;
                }
                lastGridItems = results;
                populateGrid(results);
            });
        }), SEARCH_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
    }

    private void populateGrid(List<SearchItem> items) {
        gridPanel.removeAll();
        selectedCell = null;
        for (SearchItem item : items) {
            gridPanel.add(buildItemCell(item));
        }
        gridPanel.revalidate();
        gridPanel.repaint();
    }

    private JPanel buildItemCell(SearchItem item) {
        boolean favourite = isFavourite(item.id);
        JPanel cell = new JPanel(new BorderLayout());
        cell.putClientProperty("itemId", item.id);
        cell.setPreferredSize(new Dimension(38, 38));
        cell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        cell.setBorder(BorderFactory.createLineBorder(favourite ? GOLD : new Color(25, 25, 25), favourite ? 2 : 1));
        cell.setToolTipText(favourite ? "\u2605 " + item.name : item.name);
        cell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel iconLabel = passThroughLabel();
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        AsyncBufferedImage img = itemManager.getImage(item.id);
        if (img != null) {
            img.addTo(iconLabel);
        }
        cell.add(iconLabel, BorderLayout.CENTER);
        cell.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (selectedCell != null) {
                    paintCell(selectedCell, false);
                }
                selectedCell = cell;
                cell.setBackground(ColorScheme.BRAND_ORANGE);
                cell.repaint();
                navStack.clear();
                backButton.setVisible(false);
                showGridCard();
                loadItemDetail(item, true);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                if (cell != selectedCell) {
                    cell.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
                    cell.repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (cell != selectedCell) {
                    cell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                    cell.repaint();
                }
            }
        });
        return cell;
    }

    private void paintCell(JPanel cell, boolean selected) {
        cell.setBackground(selected ? ColorScheme.BRAND_ORANGE : ColorScheme.DARKER_GRAY_COLOR);
        Object id = cell.getClientProperty("itemId");
        boolean favourite = id instanceof Integer && isFavourite((Integer) id);
        cell.setBorder(BorderFactory.createLineBorder(
            favourite ? GOLD : (selected ? ColorScheme.BRAND_ORANGE : new Color(25, 25, 25)),
            favourite ? 2 : 1));
        cell.repaint();
    }

    private void refreshFavouriteMarkers() {
        for (Component component : gridPanel.getComponents()) {
            if (component instanceof JPanel) {
                paintCell((JPanel) component, component == selectedCell);
            }
        }
        for (Component component : historyIcons.getComponents()) {
            if (component instanceof JPanel) {
                JPanel cell = (JPanel) component;
                Object id = cell.getClientProperty("itemId");
                boolean favourite = id instanceof Integer && isFavourite((Integer) id);
                cell.setBorder(BorderFactory.createLineBorder(favourite ? GOLD : new Color(25, 25, 25), favourite ? 2 : 1));
            }
        }
    }

    private void loadItemDetail(SearchItem item, boolean recordHistory) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> loadItemDetail(item, recordHistory));
            return;
        }
        int gen = detailGeneration.incrementAndGet();
        monsterGeneration.incrementAndGet();
        expandedPaths.clear();
        treeFailed.clear();
        treeLoadInFlight = false;
        wikiHttp.beginRequest();
        selectedItemName = item.name;
        shownItem = item;
        if (recordHistory) {
            updateHistory(item);
        }
        renderLoading(item);
        if (pendingDetail != null) {
            pendingDetail.cancel(false);
        }
        pendingDetail = scheduler.schedule(
            () -> fetchDetail(gen, item),
            DETAIL_DEBOUNCE_MS,
            TimeUnit.MILLISECONDS);
    }

    private void fetchDetail(int gen, SearchItem item) {
        if (gen != detailGeneration.get() || closed) {
            return;
        }
        // One wiki parse covers recipes, equipment, shops, products, and drops.
        // Prices are a separate bulk snapshot, and only when the setting is on.
        boolean fetchPrices = TradeCopy.shouldFetchGePrices(config.showPrices());
        CompletableFuture<ItemDetails> detailsFuture = itemDetailsService.load(item.name);
        CompletableFuture<PriceQuote> priceFuture = fetchPrices
            ? priceService.getQuote(item.id).exceptionally(error -> priceFallback(error))
            : CompletableFuture.completedFuture(PriceQuote.MISSING);
        CompletableFuture<PriceQuote> natureFuture = fetchPrices
            ? priceService.getQuote(ItemID.NATURERUNE).exceptionally(error -> priceFallback(error))
            : CompletableFuture.completedFuture(PriceQuote.MISSING);
        CompletableFuture.allOf(detailsFuture, priceFuture, natureFuture).whenComplete((ignored, error) ->
            SwingUtilities.invokeLater(() -> {
                if (gen != detailGeneration.get()) {
                    return;
                }
                if (error != null) {
                    if (unwrap(error) instanceof RequestCancelledException) {
                        return;
                    }
                    log.debug("[JEI] Failed to load '{}': {}", item.name, unwrap(error).getMessage());
                    lblPrice.setText("Error loading data");
                    return;
                }
                shownItem = item;
                shownDetails = detailsFuture.getNow(ItemDetails.EMPTY);
                shownPrice = priceFuture.getNow(PriceQuote.MISSING);
                shownNature = natureFuture.getNow(PriceQuote.MISSING);
                pricesFetched = fetchPrices;
                renderDetails();
            }));
    }

    private static PriceQuote priceFallback(Throwable error) {
        if (unwrap(error) instanceof RequestCancelledException) {
            throw new CompletionException(unwrap(error));
        }
        return PriceQuote.MISSING;
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private void renderLoading(SearchItem item) {
        lblName.setText(escape(item.name));
        ((WrappingLabel) lblName).setHtml(escape(item.name));
        updateStarButton(item);
        starButton.setVisible(true);
        lblMembersWeight.setText(item.members ? "Members" : "F2P");
        updateIronmanBadge();
        lblPrice.setForeground(ironmanActive ? MUTED : GOLD);
        lblPrice.setText(config.showPrices() ? "Loading..." : " ");
        shownDetails = null;
        shownPrice = PriceQuote.MISSING;
        pricesFetched = false;
        updateMarket();
        ensureLimits();
        updateOwnedHeader();
        lblHaAlch.setText(" ");
        equipmentSection.setVisible(false);
        shopsSection.setVisible(false);
        recipeSection.setVisible(false);
        usedInButton.setVisible(false);
        dropsButton.setVisible(false);
        wikiButton.setVisible(true);
        showGridCard();
        detailPanel.revalidate();
        detailPanel.repaint();
    }

    private void renderDetails() {
        if (shownItem == null || shownDetails == null) {
            return;
        }
        SearchItem item = shownItem;
        ItemDetails details = shownDetails;
        ((WrappingLabel) lblName).setHtml(escape(item.name));
        updateStarButton(item);
        starButton.setVisible(true);
        wikiButton.setVisible(true);

        String members = item.members ? "Members" : "F2P";
        String weight = "";
        if (!Double.isNaN(details.weight)) {
            weight = details.weight == 0.0 ? "  \u00b7  0 kg" : String.format("  \u00b7  %.3g kg", details.weight);
        }
        lblMembersWeight.setText(members + weight);
        updateIronmanBadge();

        String geLine = TradeCopy.geLine(ironmanActive, config.showPrices(), shownPrice);
        lblPrice.setText(geLine.isEmpty() ? " " : geLine);
        lblPrice.setForeground(ironmanActive ? MUTED : GOLD);
        updateMarket();
        ensureLimits();
        updateOwnedHeader();

        long geBuy = config.showPrices() && shownPrice != null ? shownPrice.buyPrice() : -1;
        long nature = config.showPrices() && shownNature != null ? shownNature.buyPrice() : -1;
        Alchemy.Result alchemy = Alchemy.compute(item.haPrice, geBuy, nature);
        String alchLine = TradeCopy.alchLine(ironmanActive, alchemy);
        if (alchLine.isEmpty()) {
            lblHaAlch.setText(" ");
        } else {
            lblHaAlch.setText(alchLine);
            if (!ironmanActive && alchemy != null && alchemy.profit != null) {
                lblHaAlch.setForeground(alchemy.profit >= 0 ? GOOD : BAD);
            } else {
                lblHaAlch.setForeground(ironmanActive ? GOLD : NEUTRAL);
            }
        }

        syncQuestWatch();
        populateEquipment(details.stats, details.weight, details.requirements);
        populateShops(details.shops, geBuy);
        populateRecipes(details.recipes);
        populateUsedIn(details.products);
        populateDrops(details.drops);
        detailPanel.revalidate();
        detailPanel.repaint();
    }

    private void updateOwnedHeader() {
        if (lblOwned == null) {
            return;
        }
        if (!config.showOwned() || shownItem == null) {
            lblOwned.setVisible(false);
            return;
        }
        OwnedItems.Snapshot snapshot = ownedSnapshot == null ? OwnedItems.Snapshot.NONE : ownedSnapshot;
        long have = snapshot.count(shownItem.id);
        lblOwned.setText(OwnedText.header(snapshot.inventoryKnown(), snapshot.bankKnown(), have));
        lblOwned.setVisible(true);
        lblOwned.setForeground(snapshot.inventoryKnown() && snapshot.bankKnown() ? NEUTRAL : MUTED);
        lblOwned.setToolTipText(snapshot.bankKnown()
            ? "Inventory, worn equipment, and bank."
            : OwnedText.BANK_UNKNOWN_TIP);
    }

    private void updateMarket() {
        if (lblMarket == null) {
            return;
        }
        if (!config.showPrices() || !pricesFetched || shownItem == null) {
            lblMarket.setText(" ");
            lblMarket.setVisible(false);
            return;
        }
        boolean ready = shownLimits != null && shownLimits.fresh(System.currentTimeMillis());
        Integer limit = ready ? shownLimits.limit(shownItem.id) : null;
        MarketQuote quote = MarketQuote.describe(ironmanActive, shownItem.id, shownPrice, limit, ready);
        if (quote.text.isEmpty()) {
            lblMarket.setVisible(false);
            return;
        }
        lblMarket.setHtml(escape(quote.text));
        if (ironmanActive) {
            lblMarket.setForeground(MUTED);
            lblMarket.setToolTipText("Grand Exchange figures are a reference only.");
        } else if (quote.marginSign > 0) {
            lblMarket.setForeground(GOOD);
            lblMarket.setToolTipText("Margin after the 2% GE tax, capped at 5m per item.");
        } else if (quote.marginSign < 0) {
            lblMarket.setForeground(BAD);
            lblMarket.setToolTipText("Margin after the 2% GE tax, capped at 5m per item.");
        } else {
            lblMarket.setForeground(NEUTRAL);
            lblMarket.setToolTipText("Buy limit from the wiki price mapping.");
        }
        lblMarket.setVisible(true);
    }

    /** One mapping fetch for the whole session. Skipped unless GE prices are on. */
    private void ensureLimits() {
        if (closed || buyLimitService == null || !config.showPrices()) {
            return;
        }
        if (shownLimits != null && shownLimits.fresh(System.currentTimeMillis())) {
            return;
        }
        if (limitLoadInFlight) {
            return;
        }
        limitLoadInFlight = true;
        buyLimitService.load().whenComplete((snap, error) -> SwingUtilities.invokeLater(() -> {
            limitLoadInFlight = false;
            if (closed) {
                return;
            }
            if (snap != null && snap.fresh(System.currentTimeMillis())) {
                shownLimits = snap;
                if (pricesFetched) {
                    updateMarket();
                    if (detailPanel != null) {
                        detailPanel.revalidate();
                        detailPanel.repaint();
                    }
                }
            }
        }));
    }

    private RecipeTreeView.Counts ownedCounts() {
        if (!config.showOwned()) {
            return null;
        }
        OwnedItems.Snapshot snapshot = ownedSnapshot == null ? OwnedItems.Snapshot.NONE : ownedSnapshot;
        return new RecipeTreeView.Counts() {
            @Override
            public boolean show() {
                return true;
            }

            @Override
            public boolean inventoryKnown() {
                return snapshot.inventoryKnown();
            }

            @Override
            public boolean bankKnown() {
                return snapshot.bankKnown();
            }

            @Override
            public Long have(String itemName) {
                if (!snapshot.inventoryKnown()) {
                    return null;
                }
                SearchItem item = findByName(itemName);
                if (item == null) {
                    return null;
                }
                return snapshot.count(item.id);
            }
        };
    }

    private void populateEquipment(Optional<EquipmentStats> statsOpt, double weight, ItemRequirements requirements) {
        equipmentContent.removeAll();
        if (statsOpt == null || !statsOpt.isPresent()) {
            equipmentSection.setVisible(false);
            return;
        }
        EquipmentStats stats = statsOpt.get();
        String meta = stats.slot;
        if (stats.speed > 0) {
            meta += "  \u00b7  Speed " + stats.speed;
        }
        equipmentContent.add(htmlLine(escape(meta), "#c8c8c8", "bodyBold"));
        fillRequirements(requirements);

        StringBuilder table = new StringBuilder("<html><table cellspacing='2' cellpadding='0'>");
        table.append("<tr><td></td><td><font color='#96c8ff'><b>&nbsp;Atk</b></font></td>")
            .append("<td><font color='#96c8ff'><b>&nbsp;&nbsp;Def</b></font></td></tr>");
        appendStatRow(table, "Stab", stats.attackStab, stats.defStab);
        appendStatRow(table, "Slash", stats.attackSlash, stats.defSlash);
        appendStatRow(table, "Crush", stats.attackCrush, stats.defCrush);
        appendStatRow(table, "Magic", stats.attackMagic, stats.defMagic);
        appendStatRow(table, "Range", stats.attackRange, stats.defRange);
        table.append("</table></html>");
        WrappingLabel tableLabel = new WrappingLabel();
        style.apply(tableLabel, "small");
        tableLabel.setText(table.toString());
        equipmentContent.add(tableLabel);

        if (stats.meleeStr != 0 || stats.rangedStr != 0 || stats.magicDmg != 0 || stats.prayer != 0) {
            StringBuilder other = new StringBuilder();
            if (stats.meleeStr != 0) {
                other.append(String.format("Str %+d &nbsp; ", stats.meleeStr));
            }
            if (stats.rangedStr != 0) {
                other.append(String.format("Rng Str %+d &nbsp; ", stats.rangedStr));
            }
            if (stats.magicDmg != 0) {
                other.append(String.format("Mag Dmg %+d%% &nbsp; ", stats.magicDmg));
            }
            if (stats.prayer != 0) {
                other.append(String.format("Prayer %+d", stats.prayer));
            }
            equipmentContent.add(htmlLine(other.toString(), "#b4dc78", "small"));
        }
        fillComparison(stats, weight);
        equipmentSection.setVisible(true);
    }

    private void fillRequirements(ItemRequirements requirements) {
        if (requirements == null || requirements.isEmpty() || progress == null) {
            return;
        }
        boolean judge = config.showRequirementChecks();
        for (ItemRequirements.SkillNeed skill : requirements.skills) {
            Mark mark = progress.skill(skill.name, skill.level, judge);
            addRequirementLine(RequirementJudge.htmlPrefix(mark) + escape(skill.name) + " " + skill.level, mark);
        }
        for (String quest : requirements.quests) {
            Mark mark = progress.quest(quest, judge);
            addRequirementLine(RequirementJudge.htmlPrefix(mark) + escape(quest), mark);
        }
    }

    private void addRequirementLine(String html, Mark mark) {
        WrappingLabel line = htmlLine(html, null, "small");
        line.setForeground(mark == Mark.UNMET ? new Color(130, 130, 130) : NEUTRAL);
        equipmentContent.add(line);
    }

    private void fillComparison(EquipmentStats stats, double weight) {
        if (!config.showEquipmentCompare()) {
            return;
        }
        JButton toggle = linkButton(compareOpen ? "Hide comparison" : "Compare with worn", "small");
        toggle.setForeground(LINK);
        toggle.addActionListener(e -> {
            compareOpen = !compareOpen;
            if (shownDetails != null) {
                renderDetails();
            }
        });
        equipmentContent.add(toggle);
        if (!compareOpen) {
            return;
        }
        if (wornGear == null || !wornGear.loggedIn) {
            equipmentContent.add(plainLabel("Log in to compare with what you are wearing.", "small", MUTED));
            return;
        }
        GearSlot slot = GearSlot.fromWiki(stats.slot);
        if (slot == null) {
            equipmentContent.add(plainLabel("This slot can't be compared.", "small", MUTED));
            return;
        }
        int weaponId = wornGear.id(EquipmentInventorySlot.WEAPON.getSlotIdx());
        int shieldId = wornGear.id(EquipmentInventorySlot.SHIELD.getSlotIdx());
        int sameId = wornGear.id(slot.containerIndex());
        LinkedHashSet<Integer> needed = new LinkedHashSet<>();
        if (stats.twoHanded() || slot == GearSlot.WEAPON || slot == GearSlot.SHIELD) {
            if (weaponId > 0) {
                needed.add(weaponId);
            }
        }
        if (stats.twoHanded() || slot == GearSlot.SHIELD) {
            if (shieldId > 0) {
                needed.add(shieldId);
            }
        }
        if (slot != GearSlot.WEAPON && slot != GearSlot.SHIELD && sameId > 0) {
            needed.add(sameId);
        }
        boolean waiting = false;
        boolean blocked = false;
        for (int id : needed) {
            String name = nameForWorn(id);
            if (name == null) {
                blocked = true;
                continue;
            }
            ItemDetails cached = itemDetailsService.peek(name);
            if (cached != null) {
                if (!cached.stats.isPresent()) {
                    blocked = true;
                }
                continue;
            }
            if (compareFailed.contains(wornKey(name))) {
                blocked = true;
                continue;
            }
            waiting = true;
            if (compareStarted.add(wornKey(name))) {
                final String pendingName = name;
                itemDetailsService.load(name).whenComplete((details, error) -> SwingUtilities.invokeLater(() -> {
                    if (error != null) {
                        compareFailed.add(wornKey(pendingName));
                    }
                    if (!closed && compareOpen && shownDetails != null) {
                        renderDetails();
                    }
                }));
            }
        }
        if (blocked) {
            equipmentContent.add(plainLabel("Worn stats are unavailable for something you have equipped.", "small", MUTED));
            return;
        }
        if (waiting) {
            equipmentContent.add(plainLabel("Loading worn stats\u2026", "small", MUTED));
            return;
        }
        EquipmentCompare.Result result = EquipmentCompare.compare(
            stats,
            weight,
            pieceFor(weaponId),
            pieceFor(shieldId),
            pieceFor(sameId)
        );
        equipmentContent.add(plainLabel(result.note, "small", MUTED));
        for (EquipmentCompare.Delta delta : result.deltas) {
            Color color = delta.sign() > 0 ? GOOD : delta.sign() < 0 ? BAD : MUTED;
            equipmentContent.add(plainLabel(delta.label + "  " + formatDelta(delta), "small", color));
        }
    }

    private EquipmentCompare.Piece pieceFor(int itemId) {
        if (itemId <= 0) {
            return null;
        }
        String name = nameForWorn(itemId);
        if (name == null) {
            return null;
        }
        ItemDetails details = itemDetailsService.peek(name);
        if (details == null || !details.stats.isPresent()) {
            return null;
        }
        return new EquipmentCompare.Piece(details.stats.get(), details.weight);
    }

    private String nameForWorn(int itemId) {
        SearchItem item = idIndex.get(itemId);
        return item == null ? null : item.name;
    }

    private static String wornKey(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    private static String formatDelta(EquipmentCompare.Delta delta) {
        if ("Weight".equals(delta.label)) {
            return String.format(Locale.ROOT, "%+.3f kg", delta.amount);
        }
        if ("Magic dmg".equals(delta.label)) {
            return String.format(Locale.ROOT, "%+.0f%%", delta.amount);
        }
        return String.format(Locale.ROOT, "%+.0f", delta.amount);
    }

    private int traitsFor(int id, ItemComposition comp) {
        boolean equipable = false;
        int slot = -1;
        try {
            ItemStats stats = itemManager.getItemStats(id);
            if (stats != null && stats.isEquipable()) {
                ItemEquipmentStats equipment = stats.getEquipment();
                if (equipment != null) {
                    equipable = true;
                    slot = equipment.getSlot();
                }
            }
        } catch (RuntimeException ignored) {
            equipable = false;
            slot = -1;
        }
        return ItemTraits.pack(comp.isMembers(), comp.isTradeable(), equipable, slot);
    }

    private void syncQuestWatch() {
        if (progress == null) {
            return;
        }
        if (!config.showRequirementChecks() || shownDetails == null || shownDetails.requirements == null) {
            progress.watch(Collections.<String>emptyList());
            return;
        }
        progress.watch(shownDetails.requirements.quests);
    }

    private RecipeTreeView.Marks recipeMarks() {
        if (progress == null) {
            return null;
        }
        boolean judge = config.showRequirementChecks();
        return recipe -> progress.recipe(recipe, judge);
    }

    private void populateShops(List<ShopEntry> shops, long geBuy) {
        shopsContent.removeAll();
        List<ShopEntry> visible = new ArrayList<>();
        if (shops != null) {
            for (ShopEntry shop : shops) {
                if (!shop.sells || shop.permanentlyOutOfStock()) {
                    continue;
                }
                visible.add(shop);
            }
        }
        if (visible.isEmpty()) {
            shopsSection.setVisible(false);
            return;
        }
        for (ShopEntry shop : visible) {
            TradeCopy.ShopLine line = TradeCopy.shop(ironmanActive, shop, geBuy);
            String html = "<font color='#bebebe'>\u00b7 " + escape(shop.shopName) + escape(line.stock) + "</font>"
                + "<font color='" + line.priceColor + "'>" + escape(line.price) + "</font>";
            shopsContent.add(htmlLine(html, null, "small"));
        }
        shopsSection.setVisible(true);
    }

    private void populateRecipes(List<com.osr.jei.model.RecipeInfo> recipes) {
        recipeBody.removeAll();
        if (recipes == null || recipes.isEmpty()) {
            recipeSection.setVisible(false);
            return;
        }
        String rootName = shownItem == null ? "" : shownItem.name;
        Integer rootId = shownItem == null ? null : shownItem.id;
        ItemDetails root = shownDetails == null ? ItemDetails.EMPTY : shownDetails;
        BuildExpander.Result tree = BuildExpander.expandSelected(rootName, rootId, root, viewPages(), expandedPaths);
        RecipeTreeView.fill(recipeBody, style, tree, !expandedPaths.isEmpty(), treeActions(), ownedCounts(), recipeMarks());
        recipeSection.setVisible(true);
        if (!tree.pending.isEmpty()) {
            scheduleTreeLoads(tree.pending);
        }
    }

    private RecipeTreeView.Actions treeActions() {
        return new RecipeTreeView.Actions() {
            @Override
            public void toggle(String path, String itemName) {
                toggleTree(path, itemName);
            }

            @Override
            public void openItem(String itemName) {
                SearchItem target = findByName(itemName);
                if (target != null) {
                    navigateTo(target);
                }
            }

            @Override
            public boolean canOpen(String itemName) {
                return findByName(itemName) != null;
            }

            @Override
            public void expandAll() {
                expandFullTree();
            }

            @Override
            public void collapseAll() {
                expandedPaths.clear();
                renderDetails();
            }

            @Override
            public void icon(JLabel label, String itemName) {
                SearchItem target = findByName(itemName);
                if (target == null) {
                    return;
                }
                AsyncBufferedImage img = itemManager.getImage(target.id);
                if (img != null) {
                    img.addTo(label);
                }
            }
        };
    }

    private void toggleTree(String path, String itemName) {
        if (expandedPaths.contains(path)) {
            String prefix = path + BuildExpander.SEP;
            expandedPaths.removeIf(existing -> existing.equals(path) || existing.startsWith(prefix));
        } else {
            expandedPaths.add(path);
            treeFailed.remove(treeKey(itemName));
        }
        renderDetails();
    }

    private void expandFullTree() {
        if (shownItem == null || shownDetails == null || treeLoadInFlight) {
            return;
        }
        int gen = detailGeneration.get();
        String name = shownItem.name;
        Integer id = shownItem.id;
        ItemDetails root = shownDetails;
        treeFailed.clear();
        treeLoadInFlight = true;
        Set<String> failed = new LinkedHashSet<>();
        scheduler.execute(() -> {
            BuildExpander.Result result = BuildExpander.expandAll(name, id, root, fetchingPages(failed));
            SwingUtilities.invokeLater(() -> {
                treeLoadInFlight = false;
                if (gen != detailGeneration.get() || closed) {
                    return;
                }
                treeFailed.addAll(failed);
                expandedPaths.clear();
                expandedPaths.addAll(result.expandedPaths);
                renderDetails();
            });
        });
    }

    private void scheduleTreeLoads(List<String> names) {
        if (treeLoadInFlight || names.isEmpty()) {
            return;
        }
        int gen = detailGeneration.get();
        List<String> pending = new ArrayList<>(names);
        Set<String> failed = new LinkedHashSet<>();
        treeLoadInFlight = true;
        scheduler.execute(() -> {
            for (String name : pending) {
                if (gen != detailGeneration.get() || closed) {
                    break;
                }
                if (itemDetailsService.peek(name) != null) {
                    continue;
                }
                try {
                    itemDetailsService.load(name).join();
                } catch (Exception error) {
                    failed.add(treeKey(name));
                }
            }
            SwingUtilities.invokeLater(() -> {
                treeLoadInFlight = false;
                if (gen != detailGeneration.get() || closed || shownDetails == null) {
                    return;
                }
                treeFailed.addAll(failed);
                renderDetails();
            });
        });
    }

    private RecipePages viewPages() {
        return new RecipePages() {
            @Override
            public ItemDetails cached(String itemName) {
                if (treeFailed.contains(treeKey(itemName))) {
                    return ItemDetails.EMPTY;
                }
                return itemDetailsService.peek(itemName);
            }

            @Override
            public ItemDetails load(String itemName) {
                return cached(itemName);
            }

            @Override
            public Integer idFor(String itemName) {
                SearchItem item = findByName(itemName);
                return item == null ? null : item.id;
            }
        };
    }

    private RecipePages fetchingPages(Set<String> failed) {
        return new RecipePages() {
            @Override
            public ItemDetails cached(String itemName) {
                return itemDetailsService.peek(itemName);
            }

            @Override
            public ItemDetails load(String itemName) {
                ItemDetails have = cached(itemName);
                if (have != null) {
                    return have;
                }
                try {
                    ItemDetails loaded = itemDetailsService.load(itemName).join();
                    return loaded == null ? ItemDetails.EMPTY : loaded;
                } catch (Exception error) {
                    failed.add(treeKey(itemName));
                    return ItemDetails.EMPTY;
                }
            }

            @Override
            public Integer idFor(String itemName) {
                SearchItem item = findByName(itemName);
                return item == null ? null : item.id;
            }
        };
    }

    private void updateIronmanBadge() {
        if (lblIronman != null) {
            lblIronman.setVisible(ironmanActive);
        }
    }

    private static String treeKey(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private void populateUsedIn(List<String> products) {
        List<String> known = new ArrayList<>();
        if (products != null) {
            for (String name : products) {
                if (findByName(name) != null) {
                    known.add(name);
                }
            }
        }
        if (known.isEmpty()) {
            usedInButton.setVisible(false);
            usedInButton.putClientProperty("products", null);
            return;
        }
        usedInButton.putClientProperty("products", known);
        usedInButton.setText(known.size() == 1
            ? "Used to craft: 1 item  \u2192"
            : "Used to craft: " + known.size() + " items  \u2192");
        usedInButton.setVisible(true);
    }

    private void populateDrops(List<DropEntry> drops) {
        if (!config.showDropSources() || drops == null || drops.isEmpty()) {
            dropsButton.setVisible(false);
            return;
        }
        dropsButton.setText(drops.size() == 1
            ? "Dropped by: 1 monster  \u2192"
            : "Dropped by: " + drops.size() + " monsters  \u2192");
        dropsButton.setVisible(true);
    }

    @SuppressWarnings("unchecked")
    private void showUsedInExpanded() {
        monsterGeneration.incrementAndGet();
        usedInListPanel.removeAll();
        usedInListPanel.add(backToGridButton());
        usedInListPanel.add(plainLabel("Used to craft:  " + selectedItemName, "title", Color.WHITE));
        usedInListPanel.add(Box.createRigidArea(new Dimension(0, 6)));
        Object stored = usedInButton.getClientProperty("products");
        List<String> products = stored instanceof List ? (List<String>) stored : Collections.emptyList();
        for (String name : products) {
            usedInListPanel.add(linkedRow(findByName(name), name, null));
            usedInListPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        }
        usedInListPanel.revalidate();
        usedInListPanel.repaint();
        gridMode = "usedIn";
        gridCardLayout.show(gridCardPanel, "usedIn");
    }

    private void showDropsExpanded() {
        monsterGeneration.incrementAndGet();
        usedInListPanel.removeAll();
        usedInListPanel.add(backToGridButton());
        usedInListPanel.add(plainLabel("Dropped by:  " + selectedItemName, "title", Color.WHITE));
        usedInListPanel.add(plainLabel("Click a monster to see its locations", "small", MUTED));
        usedInListPanel.add(dropSortControl());
        usedInListPanel.add(Box.createRigidArea(new Dimension(0, 6)));
        List<DropEntry> drops = shownDetails == null ? Collections.emptyList() : shownDetails.drops;
        DropSort sort = config.dropSort() == null ? DropSort.RATE : config.dropSort();
        for (DropEntry drop : DropSorter.ordered(drops, sort)) {
            usedInListPanel.add(buildDropRow(drop));
            usedInListPanel.add(Box.createRigidArea(new Dimension(0, 3)));
        }
        usedInListPanel.revalidate();
        usedInListPanel.repaint();
        gridMode = "drops";
        gridCardLayout.show(gridCardPanel, "usedIn");
    }

    private JComboBox<DropSort> dropSortControl() {
        JComboBox<DropSort> combo = new JComboBox<>(DropSort.values());
        style.apply(combo, "small");
        DropSort selected = config.dropSort() == null ? DropSort.RATE : config.dropSort();
        combo.setSelectedItem(selected);
        combo.setAlignmentX(LEFT_ALIGNMENT);
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, combo.getPreferredSize().height));
        combo.addActionListener(e -> {
            if (syncingFilters) {
                return;
            }
            Object choice = combo.getSelectedItem();
            if (choice instanceof DropSort && choice != config.dropSort()) {
                configManager.setConfiguration("osrjei", "dropSort", choice);
            }
        });
        return combo;
    }

    private JPanel buildDropRow(DropEntry drop) {
        StringBuilder detail = new StringBuilder();
        appendPiece(detail, drop.getLevel() == null || drop.getLevel().isEmpty() || "?".equals(drop.getLevel())
            ? "" : "Lv. " + drop.getLevel());
        appendPiece(detail, drop.getQuantity() == null || "?".equals(drop.getQuantity()) ? "" : drop.getQuantity());
        DropRate rate = DropRate.parse(drop.getRate(), drop.getQuantity());
        appendPiece(detail, "?".equals(rate.display) ? "" : rate.display);
        String html = "<b>" + escape(drop.getSource()) + "</b><br><font color='#999999'>"
            + escape(detail.length() == 0 ? "?" : detail.toString()) + "</font>";
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        row.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
        WrappingLabel label = new WrappingLabel();
        style.apply(label, "small");
        label.setForeground(Color.WHITE);
        label.setHtml(html);
        label.setAlignmentX(LEFT_ALIGNMENT);
        String tip = rate.original == null || rate.original.isEmpty() ? rate.display : rate.original;
        row.setToolTipText(tip);
        label.setToolTipText(tip);
        row.add(label, BorderLayout.CENTER);
        capHeight(row);
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
        return row;
    }

    private void showMonsterDetail(String monsterName) {
        int gen = monsterGeneration.incrementAndGet();
        usedInListPanel.removeAll();
        usedInListPanel.add(backToDropsButton());
        usedInListPanel.add(plainLabel(monsterName, "title", Color.WHITE));
        usedInListPanel.add(plainLabel("Loading location data\u2026", "small", MUTED));
        usedInListPanel.revalidate();
        usedInListPanel.repaint();
        gridMode = "monster";
        gridCardLayout.show(gridCardPanel, "usedIn");
        final String pending = monsterName;
        wikiHttp.beginRequest();
        monsterService.getMonsterInfo(monsterName).whenComplete((info, error) -> SwingUtilities.invokeLater(() -> {
            if (gen != monsterGeneration.get()) {
                return;
            }
            if (error != null) {
                if (!(unwrap(error) instanceof RequestCancelledException)) {
                    populateMonsterDetail(pending, Optional.empty());
                }
                return;
            }
            populateMonsterDetail(pending, info);
        }));
    }

    private void populateMonsterDetail(String monsterName, Optional<MonsterInfo> infoOpt) {
        usedInListPanel.removeAll();
        usedInListPanel.add(backToDropsButton());
        String header = monsterName;
        if (infoOpt.isPresent() && infoOpt.get().combatLevel > 0) {
            header = monsterName + "  (Lv. " + infoOpt.get().combatLevel + ")";
        }
        usedInListPanel.add(plainLabel(header, "title", Color.WHITE));
        usedInListPanel.add(Box.createRigidArea(new Dimension(0, 6)));
        if (infoOpt.isPresent() && !infoOpt.get().locations.isEmpty()) {
            usedInListPanel.add(plainLabel("Locations", "bodyBold", ColorScheme.LIGHT_GRAY_COLOR));
            for (String location : infoOpt.get().locations) {
                usedInListPanel.add(htmlLine("\u00b7 " + escape(location), "#c8c8c8", "small"));
            }
        } else {
            usedInListPanel.add(plainLabel("Location data not available.", "small", MUTED));
        }
        usedInListPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        JButton wiki = linkButton("Open Wiki page", "button");
        wiki.setForeground(LINK);
        wiki.addActionListener(e -> openWiki(monsterName));
        usedInListPanel.add(wiki);
        usedInListPanel.revalidate();
        usedInListPanel.repaint();
    }

    private void navigateTo(SearchItem target) {
        if (!selectedItemName.isEmpty()) {
            SearchItem current = findByName(selectedItemName);
            if (current != null) {
                navStack.push(current);
            }
        }
        backButton.setVisible(true);
        showGridCard();
        loadItemDetail(target, true);
    }

    private void navigateBack() {
        if (navStack.isEmpty()) {
            return;
        }
        SearchItem prev = navStack.pop();
        backButton.setVisible(!navStack.isEmpty());
        showGridCard();
        loadItemDetail(prev, false);
    }

    private void showGridCard() {
        gridMode = "grid";
        if (gridCardLayout != null) {
            gridCardLayout.show(gridCardPanel, "grid");
        }
    }

    private JPanel buildHistoryBar() {
        JPanel bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.Y_AXIS));
        bar.setBackground(ColorScheme.DARK_GRAY_COLOR);
        bar.setVisible(false);
        JLabel label = plainLabel("Recent", "small", MUTED);
        historyIcons = new JPanel(new WrapLayout(FlowLayout.LEFT, 2, 2));
        historyIcons.setBackground(ColorScheme.DARK_GRAY_COLOR);
        bar.add(Box.createRigidArea(new Dimension(0, 3)));
        bar.add(label);
        bar.add(historyIcons);
        return bar;
    }

    private void updateHistory(SearchItem item) {
        recentIds.remove(Integer.valueOf(item.id));
        recentIds.add(0, item.id);
        while (recentIds.size() > MAX_HISTORY) {
            recentIds.remove(recentIds.size() - 1);
        }
        saveRecent();
        rebuildHistory();
    }

    private void rebuildHistory() {
        if (historyIcons == null) {
            return;
        }
        historyIcons.removeAll();
        boolean any = false;
        for (int id : recentIds) {
            SearchItem item = idIndex.get(id);
            if (item == null) {
                continue;
            }
            any = true;
            historyIcons.add(buildHistoryCell(item));
        }
        historyPanel.setVisible(any);
        historyPanel.revalidate();
        historyPanel.repaint();
    }

    private JPanel buildHistoryCell(SearchItem item) {
        boolean favourite = isFavourite(item.id);
        JPanel cell = new JPanel(new BorderLayout());
        cell.putClientProperty("itemId", item.id);
        cell.setPreferredSize(new Dimension(32, 32));
        cell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        cell.setBorder(BorderFactory.createLineBorder(favourite ? GOLD : new Color(25, 25, 25), favourite ? 2 : 1));
        cell.setToolTipText(favourite ? "\u2605 " + item.name : item.name);
        cell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        JLabel icon = passThroughLabel();
        icon.setHorizontalAlignment(SwingConstants.CENTER);
        AsyncBufferedImage img = itemManager.getImage(item.id);
        if (img != null) {
            img.addTo(icon);
        }
        cell.add(icon, BorderLayout.CENTER);
        cell.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                navStack.clear();
                backButton.setVisible(false);
                showGridCard();
                loadItemDetail(item, true);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                cell.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
                cell.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                cell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                cell.repaint();
            }
        });
        return cell;
    }

    private void loadFavourites() {
        synchronized (favouritesLock) {
            favouriteIds.clear();
            addIds(favouriteIds, configManager.getConfiguration("osrjei", "favourites"));
        }
    }

    private void saveFavourites() {
        String csv;
        synchronized (favouritesLock) {
            csv = joinIds(favouriteIds);
        }
        configManager.setConfiguration("osrjei", "favourites", csv);
    }

    private void loadRecent() {
        recentIds.clear();
        addIds(recentIds, configManager.getConfiguration("osrjei", "recent"));
        while (recentIds.size() > MAX_HISTORY) {
            recentIds.remove(recentIds.size() - 1);
        }
    }

    private void saveRecent() {
        configManager.setConfiguration("osrjei", "recent", joinIds(recentIds));
    }

    private void toggleFavourite(SearchItem item) {
        synchronized (favouritesLock) {
            if (!favouriteIds.add(item.id)) {
                favouriteIds.remove(item.id);
            }
        }
        updateStarButton(item);
        saveFavourites();
        refreshFavouriteMarkers();
        if (favToggle != null && favToggle.isSelected()) {
            lastGridItems = buildFavouritesList();
            populateGrid(lastGridItems);
        }
    }

    private void updateStarButton(SearchItem item) {
        if (isFavourite(item.id)) {
            starButton.setText("\u2605");
            starButton.setToolTipText("Remove from favourites");
        } else {
            starButton.setText("\u2606");
            starButton.setToolTipText("Add to favourites");
        }
    }

    private boolean isFavourite(int id) {
        synchronized (favouritesLock) {
            return favouriteIds.contains(id);
        }
    }

    private List<SearchItem> buildFavouritesList() {
        List<SearchItem> result = new ArrayList<>();
        synchronized (favouritesLock) {
            for (int id : favouriteIds) {
                SearchItem item = idIndex.get(id);
                if (item != null && searchCriteria().matches(item)) {
                    result.add(item);
                }
            }
        }
        return result;
    }

    private SearchItem findByName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        return nameIndex.get(name.toLowerCase(java.util.Locale.ROOT));
    }

    private void openWiki(String page) {
        if (page == null || page.isEmpty()) {
            return;
        }
        LinkBrowser.browse("https://oldschool.runescape.wiki/w/" + page.replace(' ', '_'));
    }

    private void applyResizeWeight() {
        if (split == null) {
            return;
        }
        int detail = SplitLayout.clampDetailPercent(config.detailRatio());
        split.setResizeWeight((100 - detail) / 100.0);
    }

    private void maybeApplyInitialDivider() {
        if (dividerApplied || split == null || split.getHeight() <= 0) {
            return;
        }
        dividerApplied = true;
        applyDividerFromConfig();
    }

    private void applyDividerFromConfig() {
        if (split == null || split.getHeight() <= 0) {
            return;
        }
        applyResizeWeight();
        int top = SplitLayout.topDividerPx(split.getHeight(), split.getDividerSize(), config.detailRatio());
        split.setDividerLocation(Math.max(72, top));
    }

    private void hookDividerDrag() {
        if (split == null || !(split.getUI() instanceof BasicSplitPaneUI)) {
            return;
        }
        BasicSplitPaneDivider divider = ((BasicSplitPaneUI) split.getUI()).getDivider();
        divider.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                saveDividerRatio();
            }
        });
    }

    private void saveDividerRatio() {
        if (split == null || split.getHeight() <= 0) {
            return;
        }
        int detail = SplitLayout.detailPercentFromDivider(split.getHeight(), split.getDividerLocation());
        if (detail == config.detailRatio()) {
            return;
        }
        configManager.setConfiguration("osrjei", "detailRatio", detail);
    }

    private JScrollPane scroll(JPanel view) {
        JScrollPane pane = new JScrollPane(view);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        pane.getVerticalScrollBar().setPreferredSize(new Dimension(6, 0));
        pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return pane;
    }

    private JPanel section(String title) {
        JPanel panel = verticalBox();
        panel.setVisible(false);
        JLabel header = plainLabel(title, "bodyBold", Color.WHITE);
        panel.add(header);
        panel.add(Box.createRigidArea(new Dimension(0, 3)));
        return panel;
    }

    private JPanel verticalBox() {
        JPanel panel = new JPanel() {
            @Override
            public Dimension getMaximumSize() {
                Dimension preferred = getPreferredSize();
                return new Dimension(Integer.MAX_VALUE, preferred.height);
            }
        };
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setAlignmentX(LEFT_ALIGNMENT);
        return panel;
    }

    private JLabel plainLabel(String text, String role, Color color) {
        JLabel label = new JLabel(text);
        style.apply(label, role);
        label.setForeground(color);
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private WrappingLabel htmlLine(String innerHtml, String unusedColor, String role) {
        WrappingLabel label = new WrappingLabel();
        style.apply(label, role);
        label.setHtml(innerHtml);
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private JButton linkButton(String text, String role) {
        JButton button = new JButton(text);
        style.apply(button, role);
        button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        button.setOpaque(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setAlignmentX(LEFT_ALIGNMENT);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        return button;
    }

    private JButton backToGridButton() {
        JButton button = linkButton("\u2190 Back to results", "button");
        button.setForeground(LINK);
        button.addActionListener(e -> showGridCard());
        return button;
    }

    private JButton backToDropsButton() {
        JButton button = linkButton("\u2190 Back to drops", "button");
        button.setForeground(LINK);
        button.addActionListener(e -> showDropsExpanded());
        return button;
    }

    private JPanel linkedRow(SearchItem target, String text, String secondary) {
        JPanel row = new JPanel(new BorderLayout(6, 0)) {
            @Override
            public Dimension getMaximumSize() {
                Dimension preferred = getPreferredSize();
                return new Dimension(Integer.MAX_VALUE, preferred.height);
            }
        };
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setAlignmentX(LEFT_ALIGNMENT);
        JLabel icon = passThroughLabel();
        icon.setPreferredSize(new Dimension(26, 26));
        icon.setHorizontalAlignment(SwingConstants.CENTER);
        if (target != null) {
            AsyncBufferedImage img = itemManager.getImage(target.id);
            if (img != null) {
                img.addTo(icon);
            }
        }
        String html = secondary == null || secondary.isEmpty()
            ? escape(text)
            : escape(text) + "<br><font color='#999999'>" + escape(secondary) + "</font>";
        WrappingLabel label = new WrappingLabel();
        style.apply(label, "small");
        label.setHtml(html);
        if (target != null) {
            label.setForeground(LINK);
            row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter handler = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    navigateTo(target);
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
            };
            row.addMouseListener(handler);
            label.addMouseListener(handler);
        } else {
            label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        }
        row.add(icon, BorderLayout.WEST);
        row.add(label, BorderLayout.CENTER);
        return row;
    }

    private static JLabel passThroughLabel() {
        return new JLabel() {
            @Override
            public boolean contains(int x, int y) {
                return false;
            }
        };
    }

    private static void capHeight(JPanel row) {
        row.setAlignmentX(LEFT_ALIGNMENT);
    }

    private static void appendPiece(StringBuilder builder, String piece) {
        if (piece == null || piece.isEmpty()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("  \u00b7  ");
        }
        builder.append(piece);
    }

    private static void appendStatRow(StringBuilder sb, String label, int attack, int defence) {
        sb.append(String.format(
            "<tr><td><font color='#c8c8c8'>%s</font></td>"
                + "<td align='right'><font color='#96c8ff'>%+d</font></td>"
                + "<td align='right'><font color='#96c8ff'>%+d</font></td></tr>",
            label, attack, defence));
    }

    private static void addIds(java.util.Collection<Integer> into, String csv) {
        if (csv == null || csv.isEmpty()) {
            return;
        }
        for (String part : csv.split(",")) {
            try {
                into.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
                // skip junk left in the config profile
            }
        }
    }

    private static String joinIds(Iterable<Integer> ids) {
        StringBuilder builder = new StringBuilder();
        for (int id : ids) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(id);
        }
        return builder.toString();
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static ThreadFactory daemon(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }
}
