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
import com.osr.jei.pricing.LimitResult;
import com.osr.jei.pricing.MarketQuote;
import com.osr.jei.pricing.PriceAge;
import com.osr.jei.pricing.PriceLookup;
import com.osr.jei.pricing.PriceQuote;
import com.osr.jei.wiki.WikiFailures;
import com.osr.jei.search.ItemSearch;
import com.osr.jei.search.ItemTraits;
import com.osr.jei.search.SearchCriteria;
import com.osr.jei.service.BuyLimitService;
import com.osr.jei.service.ItemDetails;
import com.osr.jei.service.ItemDetailsService;
import com.osr.jei.service.MonsterService;
import com.osr.jei.service.PriceService;
import com.osr.jei.map.MapMarkers;
import com.osr.jei.ui.CollapsibleSection;
import com.osr.jei.ui.DropRowMarks;
import com.osr.jei.ui.EllipsisLabel;
import com.osr.jei.ui.IconGridPanel;
import com.osr.jei.ui.LabeledFilters;
import com.osr.jei.ui.RecipeTreeView;
import com.osr.jei.ui.ShopRows;
import com.osr.jei.ui.StarButton;
import com.osr.jei.ui.ScrollWidthPanel;
import com.osr.jei.ui.SectionState;
import com.osr.jei.ui.SectionSummaries;
import com.osr.jei.ui.SplitLayout;
import com.osr.jei.ui.StatGrid;
import com.osr.jei.ui.UiStyle;
import com.osr.jei.ui.WrappingButton;
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
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import java.awt.BorderLayout;
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
    @Inject private MapMarkers mapMarkers;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(daemon("jei-schedule"));
    private final ExecutorService searchExecutor = Executors.newSingleThreadExecutor(daemon("jei-search"));

    private final AtomicInteger searchGeneration = new AtomicInteger();
    private final AtomicInteger detailGeneration = new AtomicInteger();
    private final AtomicInteger monsterGeneration = new AtomicInteger();
    private final java.util.concurrent.atomic.AtomicBoolean indexUiQueued = new java.util.concurrent.atomic.AtomicBoolean();
    private volatile boolean indexUiPending;

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
    private boolean reflowQueued;
    private ScheduledFuture<?> pendingSearch;
    private ScheduledFuture<?> pendingDetail;

    private JSplitPane split;
    private IconGridPanel gridPanel;
    private JScrollPane gridScroll;
    private boolean resultsOpen;
    private JPanel selectedCell;
    private JScrollPane detailScroll;
    private JPanel historyPanel;
    private JPanel favouritesPanel;
    private IconGridPanel favouriteIcons;
    private JPanel historyIcons;
    private JLabel indexStatus;
    private JButton backButton;
    private StarButton starButton;
    private JButton wikiButton;
    private JPanel nameRow;
    private WrappingLabel lblName;
    private JLabel lblIronman;
    private JPanel marketBox;
    private StatGrid marketGrid;
    private CollapsibleSection tradingSection;
    private JPanel tradingContent;
    private WrappingLabel lblOwned;
    private WrappingLabel lblMembersWeight;
    private WrappingLabel lblHaAlch;
    private WrappingLabel lblAlchProfit;
    private JPanel alchBox;
    private JPanel detailPanel;
    private CollapsibleSection equipmentSection;
    private JPanel equipmentContent;
    private CollapsibleSection shopsSection;
    private JPanel shopsContent;
    private CollapsibleSection recipeSection;
    private JPanel recipeBody;
    private CollapsibleSection usedInSection;
    private JPanel usedInContent;
    private CollapsibleSection dropsSection;
    private JPanel dropsContent;
    private final SectionState sectionState = new SectionState();
    /** True while a section header is opening, so that refresh does not start a wiki fetch. */
    private boolean fillingSection;
    private final Set<String> openMonsters = new LinkedHashSet<>();
    private final Map<String, JPanel> dropBlocks = new java.util.HashMap<>();
    private WrappingLabel pinnedMapLabel;
    private final Map<String, LocationView> locationViews = new HashMap<>();

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
    private boolean limitsHoldoff;
    private JPanel notesBox;
    private JButton pageRetry;
    private JButton priceRetry;
    private WrappingLabel lblPriceNote;
    private String detailProblem;
    private boolean priceUnavailable;
    private boolean priceStale;
    private long priceFetchedAt;
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

        favouritesPanel = buildIconStrip("Favourites", true);
        favouriteIcons = (IconGridPanel) favouritesPanel.getClientProperty("icons");
        historyPanel = buildIconStrip("Recent", false);
        historyIcons = (IconGridPanel) historyPanel.getClientProperty("icons");
        indexStatus = plainLabel("Waiting for the game cache\u2026", "small", MUTED);
        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.setBackground(ColorScheme.DARK_GRAY_COLOR);
        north.setAlignmentX(LEFT_ALIGNMENT);
        north.add(buildSearchBar());
        north.add(buildFilterBar());
        north.add(indexStatus);
        north.add(favouritesPanel);
        north.add(historyPanel);
        add(north, BorderLayout.NORTH);
        add(buildContent(), BorderLayout.CENTER);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                maybeApplyInitialDivider();
                queueReflow();
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
            if (indexStatus instanceof WrappingLabel) {
                ((WrappingLabel) indexStatus).setHtml(escape(text));
            } else {
                indexStatus.setText(text);
            }
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
        pinnedMapLabel = null;
        if (mapMarkers != null) {
            mapMarkers.clear();
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
            if (dropsSection != null && dropsSection.isVisible() && dropsSection.isExpanded()
                && shownDetails != null) {
                populateDrops(shownDetails.drops);
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
        if ("sectionsOpenByDefault".equals(key)) {
            sectionState.setMainSections(config.sectionsOpenByDefault());
            syncSections();
            if (shownDetails != null) {
                renderDetails();
            }
            return;
        }
        if ("showHighAlch".equals(key)) {
            if (shownItem != null) {
                updateTradeLines(shownItem);
            }
            return;
        }
        style = UiStyle.from(config.fontSize(), config.fontStyle(), config.fontFamily());
        if (marketGrid != null) {
            marketGrid.useStyle(style);
        }
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
                    log.debug("[Item Browser] Item id {} is not in the index yet", canonical);
                    return;
                }
                navStack.clear();
                backButton.setVisible(false);
                loadItemDetail(item, true);
            });
        });
    }

    private JPanel buildSearchBar() {
        IconTextField searchBar = new IconTextField();
        searchBar.setIcon(IconTextField.Icon.SEARCH);
        searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        searchBar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
        // RuneLite's search field is about 30px. The default preferred height is
        // only the font line, which leaves the text tight against the edges.
        Dimension natural = searchBar.getPreferredSize();
        searchBar.setPreferredSize(new Dimension(Math.max(120, natural.width), 30));
        searchBar.setMinimumSize(new Dimension(0, 30));
        searchBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        searchBar.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { onSearch(searchBar.getText()); }
            @Override public void removeUpdate(DocumentEvent e) { onSearch(searchBar.getText()); }
            @Override public void changedUpdate(DocumentEvent e) { onSearch(searchBar.getText()); }
        });

        JPanel row = fullWidthRow();
        row.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));
        row.add(searchBar, BorderLayout.CENTER);
        return row;
    }

    private JPanel buildFilterBar() {
        tradeCombo = filterCombo(SearchCriteria.Trade.values(), config.filterTradeable(), "filterTradeable",
            "Tradeable, untradeable, or both");
        membersCombo = filterCombo(SearchCriteria.Members.values(), config.filterMembers(), "filterMembers",
            "Members, free-to-play, or both");
        equipCombo = filterCombo(SearchCriteria.Equip.values(), config.filterEquipable(), "filterEquipable",
            "Items that can be equipped, or any item");
        slotCombo = filterCombo(SearchCriteria.SlotChoice.values(), config.filterSlot(), "filterSlot",
            "One equipment slot, or any slot");
        JPanel filters = LabeledFilters.column(
            style.forRole("small"),
            MUTED,
            new String[]{"Trade", "World", "Item", "Slot"},
            new JComponent[]{tradeCombo, membersCombo, equipCombo, slotCombo}
        );
        JPanel row = fullWidthRow();
        row.add(filters, BorderLayout.CENTER);
        return row;
    }

    /** BoxLayout child that fills the sidebar width and stays left-aligned. */
    private JPanel fullWidthRow() {
        JPanel row = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }

            @Override
            public Dimension getMinimumSize() {
                return new Dimension(0, getPreferredSize().height);
            }
        };
        row.setBackground(ColorScheme.DARK_GRAY_COLOR);
        row.setAlignmentX(LEFT_ALIGNMENT);
        return row;
    }

    private <E> JComboBox<E> filterCombo(E[] values, E selected, String key, String tip) {
        JComboBox<E> combo = new JComboBox<>(values);
        style.apply(combo, "small");
        combo.setSelectedItem(selected == null ? values[0] : selected);
        combo.setToolTipText(tip);
        int height = 24;
        Dimension natural = combo.getPreferredSize();
        combo.setPreferredSize(new Dimension(Math.max(80, natural.width), height));
        combo.setMinimumSize(new Dimension(0, height));
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
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
        if (lastSearchQuery != null && !lastSearchQuery.isEmpty()) {
            onSearch(lastSearchQuery);
        }
    }

    private JComponent buildContent() {
        gridPanel = new IconGridPanel(36, 4);
        gridPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        gridScroll = scroll(gridPanel);
        gridScroll.setMinimumSize(new Dimension(0, 0));
        gridScroll.setVisible(false);

        detailScroll = scroll(buildDetailPanel());
        detailScroll.setMinimumSize(new Dimension(0, 120));
        detailScroll.setPreferredSize(new Dimension(PANEL_WIDTH, 180));
        detailScroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                queueReflow();
            }
        });

        split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, gridScroll, detailScroll);
        split.setContinuousLayout(true);
        split.setBorder(null);
        split.setDividerSize(6);
        split.setBackground(ColorScheme.DARK_GRAY_COLOR);
        split.setResizeWeight(0);
        split.setDividerSize(0);
        return split;
    }

    private JPanel buildDetailPanel() {
        detailPanel = verticalBox();
        detailPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        detailPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        backButton = linkButton("\u2190 Back", "button");
        backButton.addActionListener(e -> navigateBack());
        backButton.setVisible(false);

        lblName = new WrappingLabel();
        style.apply(lblName, "title");
        lblName.setForeground(Color.WHITE);
        setWrap(lblName, "Search for an item above");

        starButton = new StarButton("Add to favourites");
        starButton.setVisible(false);
        starButton.addActionListener(e -> {
            SearchItem current = findByName(selectedItemName);
            if (current != null) {
                toggleFavourite(current);
            }
        });

        lblIronman = plainLabel("Ironman", "small", new Color(140, 175, 140));
        lblIronman.setVisible(false);

        JPanel nameStack = verticalBox();
        nameStack.setOpaque(false);
        nameStack.add(lblName);
        nameStack.add(lblIronman);

        nameRow = new JPanel(new BorderLayout(4, 0)) {
            @Override
            public Dimension getMaximumSize() {
                Dimension preferred = getPreferredSize();
                return new Dimension(Integer.MAX_VALUE, preferred.height);
            }

            @Override
            public Dimension getMinimumSize() {
                Dimension preferred = getPreferredSize();
                return new Dimension(0, preferred.height);
            }
        };
        nameRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        nameRow.setAlignmentX(LEFT_ALIGNMENT);
        nameRow.add(nameStack, BorderLayout.CENTER);
        nameRow.add(starButton, BorderLayout.EAST);

        lblMembersWeight = new WrappingLabel();
        style.apply(lblMembersWeight, "small");
        lblMembersWeight.setForeground(MUTED);
        setWrap(lblMembersWeight, " ");
        lblOwned = new WrappingLabel();
        style.apply(lblOwned, "small");
        lblOwned.setForeground(MUTED);
        lblOwned.setVisible(false);
        lblPriceNote = new WrappingLabel();
        style.apply(lblPriceNote, "small");
        lblPriceNote.setForeground(MUTED);
        lblPriceNote.setVisible(false);
        priceRetry = linkButton("Retry prices", "small");
        priceRetry.setForeground(LINK);
        priceRetry.setToolTipText("Load Grand Exchange prices and buy limits again.");
        priceRetry.setVisible(false);
        priceRetry.addActionListener(e -> retryPrices());
        marketGrid = new StatGrid(style);
        marketGrid.setVisible(false);
        lblHaAlch = new WrappingLabel();
        style.apply(lblHaAlch, "body");
        lblHaAlch.setForeground(GOOD);
        lblHaAlch.setVisible(false);
        lblAlchProfit = new WrappingLabel();
        style.apply(lblAlchProfit, "body");
        lblAlchProfit.setVisible(false);
        tradingSection = collapsible(SectionState.TRADING);
        tradingContent = tradingSection.body();
        marketBox = verticalBox();
        marketBox.add(marketGrid);
        marketBox.add(lblHaAlch);
        marketBox.add(lblPriceNote);
        marketBox.add(priceRetry);
        marketBox.add(tradingSection);
        alchBox = marketBox;

        equipmentSection = collapsible(SectionState.EQUIPMENT);
        equipmentContent = equipmentSection.body();
        equipmentSection.setOnToggle(() -> {
            if (equipmentSection.isExpanded() && compareOpen && shownDetails != null) {
                renderDetails();
            }
        });
        shopsSection = collapsible(SectionState.SHOPS);
        shopsContent = shopsSection.body();
        shopsSection.setOnToggle(() -> {
            if (shownDetails != null) {
                populateShops(shownDetails.shops, shownGeBuy());
            }
        });
        recipeSection = collapsible(SectionState.CRAFTING);
        recipeBody = recipeSection.body();
        recipeSection.setOnToggle(() -> {
            if (shownDetails == null) {
                return;
            }
            fillingSection = true;
            try {
                populateRecipes(shownDetails.recipes);
            } finally {
                fillingSection = false;
            }
        });
        usedInSection = collapsible(SectionState.USED_IN);
        usedInContent = usedInSection.body();
        usedInSection.setOnToggle(() -> {
            if (shownDetails != null) {
                populateUsedIn(shownDetails.products);
            }
        });
        dropsSection = collapsible(SectionState.DROPS);
        dropsContent = dropsSection.body();
        dropsSection.setOnToggle(() -> {
            if (shownDetails != null) {
                populateDrops(shownDetails.drops);
            }
        });

        wikiButton = linkButton("Open Wiki page", "button");
        wikiButton.setForeground(LINK);
        wikiButton.addActionListener(e -> openWiki(selectedItemName));
        wikiButton.setVisible(false);

        notesBox = verticalBox();
        notesBox.setVisible(false);
        pageRetry = linkButton("Retry wiki page", "small");
        pageRetry.setForeground(LINK);
        pageRetry.setToolTipText("Ask the OSRS Wiki for this page again.");
        pageRetry.setVisible(false);
        pageRetry.addActionListener(e -> retryWikiPage());

        applySectionOrder();
        return detailPanel;
    }

    private CollapsibleSection collapsible(String key) {
        CollapsibleSection section = new CollapsibleSection(key, sectionState, style);
        section.setVisible(false);
        section.setAlignmentX(LEFT_ALIGNMENT);
        return section;
    }

    private void applySectionOrder() {
        if (detailPanel == null) {
            return;
        }
        detailPanel.removeAll();
        detailPanel.add(backButton);
        detailPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        detailPanel.add(nameRow);
        detailPanel.add(Box.createRigidArea(new Dimension(0, 2)));
        detailPanel.add(lblMembersWeight);
        detailPanel.add(lblOwned);
        detailPanel.add(Box.createRigidArea(new Dimension(0, 6)));
        for (String section : DetailLayout.order(ironmanActive)) {
            detailPanel.add(sectionComponent(section));
        }
        detailPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        detailPanel.add(notesBox);
        detailPanel.add(pageRetry);
        detailPanel.add(wikiButton);
        detailPanel.revalidate();
        detailPanel.repaint();
    }

    private JComponent sectionComponent(String section) {
        switch (section) {
            case DetailLayout.GE:
                return marketBox;
            case DetailLayout.ALCH:
                return alchBox;
            case DetailLayout.EQUIPMENT:
                return equipmentSection;
            case DetailLayout.SHOPS:
                return shopsSection;
            case DetailLayout.USED_IN:
                return usedInSection;
            case DetailLayout.DROPS:
                return dropsSection;
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
                log.info("[Item Browser] Item index was empty; waiting for the game cache");
                showIndexStatus("Waiting for the game cache\u2026");
                return;
            }
            indexPopulated = true;
            indexScanning = false;
            log.info("[Item Browser] Item index ready: {} unique items", items.size());
        }
        // One pending UI flush. Intermediate chunks must not queue a history
        // rebuild per 500 ids, or a monster location update waits behind them.
        indexUiPending = true;
        if (!indexUiQueued.compareAndSet(false, true)) {
            return;
        }
        SwingUtilities.invokeLater(this::flushIndexUi);
    }

    private void flushIndexUi() {
        indexUiQueued.set(false);
        if (closed || !indexUiPending) {
            return;
        }
        indexUiPending = false;
        if (!indexScanning && indexPopulated) {
            showIndexStatus(null);
        }
        rebuildHistory();
        if (!lastSearchQuery.isEmpty()) {
            onSearch(lastSearchQuery);
        }
        if (indexUiPending && indexUiQueued.compareAndSet(false, true)) {
            SwingUtilities.invokeLater(this::flushIndexUi);
        }
    }

    private void onSearch(String query) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> onSearch(query));
            return;
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
            showResultGrid(false);
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
                showResultGrid(true);
            });
        }), SEARCH_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
    }

    private void populateGrid(List<SearchItem> items) {
        gridPanel.removeAll();
        selectedCell = null;
        if (items.isEmpty()) {
            String text;
            if (!indexPopulated) {
                text = "The item list is still loading.";
            } else {
                text = "No items match that search.";
            }
            JLabel empty = plainLabel(text, "small", MUTED);
            empty.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            gridPanel.add(empty);
        }
        for (SearchItem item : items) {
            gridPanel.add(buildItemCell(item));
        }
        gridPanel.revalidate();
        gridPanel.repaint();
        boolean searching = lastSearchQuery != null && lastSearchQuery.length() >= 2;
        showResultGrid(searching);
    }

    private void showResultGrid(boolean show) {
        if (split == null || gridScroll == null || resultsOpen == show) {
            if (show && split != null && split.getDividerLocation() <= 0 && split.getHeight() > 0) {
                applyResizeWeight();
                applyDividerFromConfig();
            }
            return;
        }
        resultsOpen = show;
        gridScroll.setVisible(show);
        if (show) {
            gridScroll.setMinimumSize(new Dimension(0, 72));
            split.setDividerSize(6);
            applyResizeWeight();
            if (split.getHeight() > 0) {
                applyDividerFromConfig();
            }
        } else {
            gridScroll.setMinimumSize(new Dimension(0, 0));
            gridScroll.setPreferredSize(new Dimension(0, 0));
            split.setDividerSize(0);
            split.setResizeWeight(0);
            if (split.getHeight() > 0) {
                split.setDividerLocation(0);
            }
        }
        split.revalidate();
        split.repaint();
    }

    private JPanel buildItemCell(SearchItem item) {
        boolean favourite = isFavourite(item.id);
        JPanel cell = new JPanel(new BorderLayout());
        cell.putClientProperty("itemId", item.id);
        cell.setPreferredSize(new Dimension(36, 36));
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
            public void mousePressed(MouseEvent e) {
                showFavouriteMenu(e, cell, item);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                showFavouriteMenu(e, cell, item);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) {
                    return;
                }
                if (selectedCell != null) {
                    paintCell(selectedCell, false);
                }
                selectedCell = cell;
                cell.setBackground(ColorScheme.BRAND_ORANGE);
                cell.repaint();
                navStack.clear();
                backButton.setVisible(false);
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
        markFavouriteBorders(historyIcons);
        markFavouriteBorders(favouriteIcons);
    }

    private void markFavouriteBorders(JPanel icons) {
        if (icons == null) {
            return;
        }
        for (Component component : icons.getComponents()) {
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
        resetItemExpansions();
        if (mapMarkers != null && (config == null || config.clearPinsOnSwitch())) {
            mapMarkers.clear();
        }
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

    private void resetItemExpansions() {
        monsterGeneration.incrementAndGet();
        pinnedMapLabel = null;
        openMonsters.clear();
        locationViews.clear();
        dropBlocks.clear();
        expandedPaths.clear();
        treeFailed.clear();
        treeLoadInFlight = false;
        sectionState.reset(config != null && config.sectionsOpenByDefault());
        syncSections();
    }

    private void syncSections() {
        CollapsibleSection[] sections = new CollapsibleSection[] {
            tradingSection, equipmentSection, shopsSection, usedInSection, dropsSection, recipeSection
        };
        for (CollapsibleSection section : sections) {
            if (section != null) {
                section.sync();
            }
        }
    }

    private void fetchDetail(int gen, SearchItem item) {
        if (gen != detailGeneration.get() || closed) {
            return;
        }
        // One wiki parse covers recipes, equipment, shops, products, and drops.
        // Prices are a separate bulk snapshot, and only when the setting is on.
        boolean fetchPrices = TradeCopy.shouldFetchGePrices(config.showPrices());
        CompletableFuture<ItemDetails> detailsFuture = itemDetailsService.load(item.name);
        CompletableFuture<PriceLookup> priceFuture = fetchPrices
            ? priceService.lookup(item.id)
            : CompletableFuture.completedFuture(PriceLookup.HIDDEN);
        CompletableFuture<PriceLookup> natureFuture = fetchPrices
            ? priceService.lookup(ItemID.NATURERUNE)
            : CompletableFuture.completedFuture(PriceLookup.HIDDEN);
        detailsFuture.whenComplete((details, error) -> SwingUtilities.invokeLater(() -> {
            if (gen != detailGeneration.get() || closed) {
                return;
            }
            if (error != null) {
                if (unwrap(error) instanceof RequestCancelledException) {
                    return;
                }
                log.debug("[Item Browser] Failed to load '{}': {}", item.name, unwrap(error).getMessage());
                detailProblem = WikiFailures.page(unwrap(error));
                shownDetails = null;
            } else {
                detailProblem = null;
                shownDetails = details == null ? ItemDetails.EMPTY : details;
            }
            shownItem = item;
            renderDetails();
        }));
        CompletableFuture.allOf(priceFuture, natureFuture).whenComplete((ignored, error) ->
            SwingUtilities.invokeLater(() -> {
                if (gen != detailGeneration.get() || closed) {
                    return;
                }
                if (error != null && unwrap(error) instanceof RequestCancelledException) {
                    return;
                }
                acceptPrices(fetchPrices, priceFuture, natureFuture, error);
                if (shownItem == item) {
                    renderDetails();
                }
            }));
    }

    private void acceptPrices(
        boolean fetchPrices,
        CompletableFuture<PriceLookup> priceFuture,
        CompletableFuture<PriceLookup> natureFuture,
        Throwable error
    ) {
        pricesFetched = fetchPrices;
        if (!fetchPrices) {
            shownPrice = PriceQuote.MISSING;
            shownNature = PriceQuote.MISSING;
            priceUnavailable = false;
            priceStale = false;
            priceFetchedAt = 0L;
            return;
        }
        if (error != null) {
            shownPrice = PriceQuote.MISSING;
            shownNature = PriceQuote.MISSING;
            priceUnavailable = true;
            priceStale = false;
            priceFetchedAt = 0L;
            return;
        }
        PriceLookup price = priceFuture.getNow(PriceLookup.UNAVAILABLE);
        PriceLookup nature = natureFuture.getNow(PriceLookup.UNAVAILABLE);
        shownPrice = price.quote == null ? PriceQuote.MISSING : price.quote;
        shownNature = nature.quote == null ? PriceQuote.MISSING : nature.quote;
        priceUnavailable = price.unavailable;
        priceStale = price.stale;
        priceFetchedAt = price.fetchedAtMs;
    }

    private void retryWikiPage() {
        if (shownItem == null || itemDetailsService == null) {
            return;
        }
        itemDetailsService.retry(shownItem.name);
        loadItemDetail(shownItem, false);
    }

    private void retryPrices() {
        if (shownItem == null || priceService == null || !config.showPrices()) {
            return;
        }
        priceService.allowRetry();
        if (buyLimitService != null) {
            buyLimitService.allowRetry();
        }
        limitsHoldoff = false;
        int gen = detailGeneration.get();
        SearchItem item = shownItem;
        if (priceRetry != null) {
            priceRetry.setVisible(false);
        }
        showPriceLoading();
        CompletableFuture<PriceLookup> priceFuture = priceService.lookup(item.id);
        CompletableFuture<PriceLookup> natureFuture = priceService.lookup(ItemID.NATURERUNE);
        CompletableFuture.allOf(priceFuture, natureFuture).whenComplete((ignored, error) ->
            SwingUtilities.invokeLater(() -> {
                if (gen != detailGeneration.get() || closed) {
                    return;
                }
                if (error != null && unwrap(error) instanceof RequestCancelledException) {
                    return;
                }
                acceptPrices(true, priceFuture, natureFuture, error);
                ensureLimits();
                renderDetails();
            }));
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private void renderLoading(SearchItem item) {
        setWrap(lblName, item.name);
        updateStarButton(item);
        starButton.setVisible(true);
        setWrap(lblMembersWeight, item.members ? "Members" : "F2P");
        updateIronmanBadge();
        if (config.showPrices()) {
            showPriceLoading();
        } else {
            clearPrices();
        }
        shownDetails = null;
        detailProblem = null;
        shownPrice = PriceQuote.MISSING;
        shownNature = PriceQuote.MISSING;
        pricesFetched = false;
        priceUnavailable = false;
        priceStale = false;
        priceFetchedAt = 0L;
        if (lblPriceNote != null) {
            lblPriceNote.setVisible(false);
        }
        if (priceRetry != null) {
            priceRetry.setVisible(false);
        }
        if (notesBox != null) {
            notesBox.removeAll();
            notesBox.setVisible(false);
        }
        if (pageRetry != null) {
            pageRetry.setVisible(false);
        }
        updateMarket();
        ensureLimits();
        updateOwnedHeader();
        clearAlch();
        hideDetailSections();
        wikiButton.setVisible(true);
        detailPanel.revalidate();
        detailPanel.repaint();
    }

    private void renderDetails() {
        if (shownItem == null) {
            return;
        }
        if (detailProblem != null) {
            renderProblem();
            return;
        }
        if (shownDetails == null) {
            return;
        }
        SearchItem item = shownItem;
        ItemDetails details = shownDetails;
        paintItemHeader(item, details.weight);
        long geBuy = updateTradeLines(item);
        if (details.missing) {
            hideDetailSections();
            fillEmptyNotes(details);
            if (pageRetry != null) {
                pageRetry.setVisible(true);
            }
            detailPanel.revalidate();
            detailPanel.repaint();
            return;
        }
        if (pageRetry != null) {
            pageRetry.setVisible(false);
        }
        syncQuestWatch();
        populateEquipment(details.stats, details.weight, details.requirements);
        populateShops(details.shops, geBuy);
        populateRecipes(details.recipes);
        populateUsedIn(details.products);
        populateDrops(details.drops);
        fillEmptyNotes(details);
        detailPanel.revalidate();
        detailPanel.repaint();
    }

    private void renderProblem() {
        SearchItem item = shownItem;
        paintItemHeader(item, Double.NaN);
        updateTradeLines(item);
        hideDetailSections();
        if (notesBox != null) {
            notesBox.removeAll();
            notesBox.add(noteLabel(detailProblem));
            notesBox.setVisible(true);
        }
        if (pageRetry != null) {
            pageRetry.setVisible(true);
        }
        wikiButton.setVisible(true);
        detailPanel.revalidate();
        detailPanel.repaint();
    }

    private void paintItemHeader(SearchItem item, double weight) {
        setWrap(lblName, item.name);
        updateStarButton(item);
        starButton.setVisible(true);
        wikiButton.setVisible(true);
        String members = item.members ? "Members" : "F2P";
        String weightText = "";
        if (!Double.isNaN(weight)) {
            weightText = weight == 0.0 ? "  \u00b7  0 kg" : String.format("  \u00b7  %.3g kg", weight);
        }
        setWrap(lblMembersWeight, members + weightText);
        updateIronmanBadge();
        updateOwnedHeader();
    }

    private long updateTradeLines(SearchItem item) {
        long geBuy = shownGeBuy();
        long nature = config.showPrices() && shownNature != null && !priceUnavailable ? shownNature.buyPrice() : -1;
        Alchemy.Result alchemy = Alchemy.compute(item.haPrice, geBuy, nature);
        paintGeBlock(alchemy);
        updatePriceNote();
        ensureLimits();
        paintAlch(alchemy);
        return geBuy;
    }

    private void showPriceLoading() {
        if (marketGrid == null) {
            return;
        }
        List<StatGrid.Row> rows = new ArrayList<>();
        rows.add(new StatGrid.Row("", "Loading prices\u2026", ironmanActive ? MUTED : GOLD));
        marketGrid.setRows(rows);
    }

    private void clearPrices() {
        if (marketGrid != null) {
            marketGrid.setRows(Collections.emptyList());
        }
        if (tradingSection != null) {
            tradingContent.removeAll();
            tradingSection.setVisible(false);
        }
    }

    private void clearAlch() {
        if (lblHaAlch != null) {
            lblHaAlch.setVisible(false);
        }
        if (lblAlchProfit != null) {
            lblAlchProfit.setVisible(false);
        }
    }

    private long shownGeBuy() {
        return config.showPrices() && shownPrice != null && !priceUnavailable ? shownPrice.buyPrice() : -1;
    }

    private void paintGeBlock(Alchemy.Result alchemy) {
        if (marketGrid == null) {
            return;
        }
        if (!config.showPrices() || priceUnavailable) {
            clearPrices();
            return;
        }
        if (!pricesFetched) {
            showPriceLoading();
            if (tradingSection != null) {
                tradingSection.setVisible(false);
            }
            return;
        }
        List<StatGrid.Row> rows = new ArrayList<>();
        List<StatGrid.Row> trading = new ArrayList<>();
        Color geColor = ironmanActive ? MUTED : GOLD;
        String geLine = TradeCopy.geLine(ironmanActive, true, shownPrice);
        if (!geLine.isEmpty()) {
            rows.add(new StatGrid.Row("", geLine, geColor));
        }
        boolean ready = shownLimits != null && shownLimits.fetchedAt() > 0L;
        boolean limitsUnavailable = !ready && limitsHoldoff;
        Integer limit = ready && shownItem != null ? shownLimits.limit(shownItem.id) : null;
        int itemId = shownItem == null ? -1 : shownItem.id;
        MarketQuote quote = MarketQuote.describe(
            ironmanActive, itemId, shownPrice, limit, ready, limitsUnavailable);
        for (MarketQuote.Line line : quote.lines) {
            if ("Reference only".equals(line.value)) {
                continue;
            }
            StatGrid.Row row = new StatGrid.Row(line.key, line.value, marketColor(line));
            if (isGuideLine(line)) {
                rows.add(row);
            } else {
                trading.add(row);
            }
        }
        if (config.showHighAlch() && alchemy != null && alchemy.profit != null) {
            trading.add(new StatGrid.Row(
                "Alch profit", profitText(alchemy.profit.longValue()), alchemy.profit >= 0 ? GOOD : BAD));
        }
        marketGrid.setRows(rows);
        marketGrid.setToolTipText(ironmanActive
            ? "Grand Exchange figures are a reference only."
            : "Guide price and buy limit. Flipping figures are under Trading details.");
        fillTrading(trading);
    }

    private static boolean isGuideLine(MarketQuote.Line line) {
        if (line == null) {
            return false;
        }
        return "Limit".equals(line.key) || "Buy limit".equals(line.key) || "Reference only".equals(line.value);
    }

    private static String profitText(long profit) {
        if (profit >= 0) {
            return String.format("+%,d gp", profit);
        }
        return String.format("%,d gp", profit);
    }

    private void fillTrading(List<StatGrid.Row> rows) {
        if (tradingSection == null) {
            return;
        }
        tradingContent.removeAll();
        if (rows == null || rows.isEmpty()) {
            tradingSection.setVisible(false);
            return;
        }
        tradingSection.setTitle("Trading details");
        StatGrid grid = new StatGrid(style);
        grid.setRows(rows);
        tradingContent.add(grid);
        tradingSection.setVisible(true);
    }

    private Color marketColor(MarketQuote.Line line) {
        if (ironmanActive || line == null || line.tone == 0) {
            return ironmanActive ? MUTED : NEUTRAL;
        }
        return line.tone > 0 ? GOOD : BAD;
    }

    private void paintAlch(Alchemy.Result alchemy) {
        if (alchemy == null || (config != null && !config.showHighAlch())) {
            clearAlch();
            return;
        }
        setWrap(lblHaAlch, String.format("High Alch: %,d gp", alchemy.haValue));
        lblHaAlch.setForeground(ironmanActive ? GOLD : NEUTRAL);
        lblHaAlch.setVisible(true);
        if (lblAlchProfit != null) {
            lblAlchProfit.setVisible(false);
        }
    }

    private void updatePriceNote() {
        if (lblPriceNote == null) {
            return;
        }
        String note = "";
        if (config.showPrices() && pricesFetched && priceUnavailable) {
            note = PriceAge.UNAVAILABLE;
        } else if (config.showPrices() && pricesFetched && shownPrice != null && shownPrice.known()) {
            String age = PriceAge.suffix(shownPrice, priceFetchedAt, System.currentTimeMillis());
            if (!age.isEmpty()) {
                note = "Price data is " + age + ".";
            }
        }
        if (note.isEmpty()) {
            lblPriceNote.setVisible(false);
        } else {
            lblPriceNote.setHtml(escape(note));
            lblPriceNote.setForeground(priceUnavailable ? BAD : MUTED);
            lblPriceNote.setVisible(true);
            lblPriceNote.setToolTipText(priceStale
                ? "Could not refresh Grand Exchange prices. Showing the last saved snapshot."
                : "Age of the wiki's latest trade for this item, or of the saved price snapshot.");
        }
        if (priceRetry != null) {
            boolean limitsNeedRetry = limitsHoldoff
                && (shownLimits == null || !shownLimits.fresh(System.currentTimeMillis()));
            priceRetry.setVisible(config.showPrices() && pricesFetched
                && (priceUnavailable || priceStale || limitsNeedRetry));
        }
    }

    private void hideDetailSections() {
        equipmentSection.setVisible(false);
        shopsSection.setVisible(false);
        recipeSection.setVisible(false);
        usedInSection.setVisible(false);
        dropsSection.setVisible(false);
    }

    private void fillEmptyNotes(ItemDetails details) {
        if (notesBox == null) {
            return;
        }
        notesBox.removeAll();
        if (details != null && details.missing) {
            notesBox.add(noteLabel("The OSRS Wiki has no page for this item."));
            notesBox.setVisible(true);
            return;
        }
        if (details == null) {
            notesBox.setVisible(false);
            return;
        }
        if (details.recipes == null || details.recipes.isEmpty()) {
            notesBox.add(noteLabel("No recipes listed for this item."));
        }
        if (visibleShops(details.shops).isEmpty()) {
            notesBox.add(noteLabel("No shops sell this item."));
        }
        if (config.showDropSources() && (details.drops == null || details.drops.isEmpty())) {
            notesBox.add(noteLabel("No drop sources listed."));
        }
        if (knownProducts(details.products).isEmpty()) {
            notesBox.add(noteLabel("No uses listed."));
        }
        notesBox.setVisible(notesBox.getComponentCount() > 0);
    }

    private WrappingLabel noteLabel(String text) {
        WrappingLabel line = htmlLine(escape(text), null, "small");
        line.setForeground(MUTED);
        return line;
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
        setWrap(lblOwned, OwnedText.header(snapshot.inventoryKnown(), snapshot.bankKnown(), have));
        lblOwned.setVisible(true);
        lblOwned.setForeground(snapshot.inventoryKnown() && snapshot.bankKnown() ? NEUTRAL : MUTED);
        lblOwned.setToolTipText(snapshot.bankKnown()
            ? "Inventory, worn equipment, and bank."
            : OwnedText.BANK_UNKNOWN_TIP);
    }

    private void updateMarket() {
        Alchemy.Result alchemy = null;
        if (shownItem != null) {
            long nature = config.showPrices() && shownNature != null && !priceUnavailable ? shownNature.buyPrice() : -1;
            alchemy = Alchemy.compute(shownItem.haPrice, shownGeBuy(), nature);
        }
        paintGeBlock(alchemy);
    }

    /** One mapping fetch for the whole session. Skipped unless GE prices are on. */
    private void ensureLimits() {
        if (closed || buyLimitService == null || !config.showPrices()) {
            return;
        }
        if (shownLimits != null && shownLimits.fresh(System.currentTimeMillis())) {
            return;
        }
        if (limitLoadInFlight || limitsHoldoff) {
            return;
        }
        limitLoadInFlight = true;
        buyLimitService.load().whenComplete((result, error) -> SwingUtilities.invokeLater(() -> {
            limitLoadInFlight = false;
            if (closed) {
                return;
            }
            if (error != null) {
                if (!(unwrap(error) instanceof RequestCancelledException)) {
                    limitsHoldoff = true;
                    updatePriceNote();
                    updateMarket();
                }
                return;
            }
            LimitResult loaded = result == null ? new LimitResult(null, true) : result;
            if (loaded.snapshot != null && loaded.snapshot.fetchedAt() > 0L) {
                shownLimits = loaded.snapshot;
            }
            limitsHoldoff = loaded.failed
                && (shownLimits == null || !shownLimits.fresh(System.currentTimeMillis()));
            if (pricesFetched) {
                updatePriceNote();
                updateMarket();
                if (detailPanel != null) {
                    detailPanel.revalidate();
                    detailPanel.repaint();
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
        equipmentSection.setTitle(SectionSummaries.equipment(stats.slot, stats.twoHanded()));
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
        if (!compareOpen || !equipmentSection.isExpanded()) {
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
        boolean failedLoad = false;
        List<String> failedNames = new ArrayList<>();
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
                failedLoad = true;
                failedNames.add(name);
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
            if (failedLoad) {
                equipmentContent.add(plainLabel("Worn stats could not be loaded from the wiki.", "small", MUTED));
                JButton retry = linkButton("Retry worn stats", "small");
                retry.setForeground(LINK);
                retry.addActionListener(e -> retryWornStats(failedNames));
                equipmentContent.add(retry);
            } else {
                equipmentContent.add(plainLabel(
                    "Worn stats are not listed for something you have equipped.", "small", MUTED));
            }
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

    private void retryWornStats(List<String> names) {
        for (String name : names) {
            compareFailed.remove(wornKey(name));
            compareStarted.remove(wornKey(name));
            itemDetailsService.retry(name);
        }
        if (shownDetails != null) {
            renderDetails();
        }
    }

    private List<ShopEntry> visibleShops(List<ShopEntry> shops) {
        List<ShopEntry> visible = new ArrayList<>();
        if (shops == null) {
            return visible;
        }
        for (ShopEntry shop : shops) {
            if (!shop.sells || shop.permanentlyOutOfStock()) {
                continue;
            }
            visible.add(shop);
        }
        return visible;
    }

    private List<String> knownProducts(List<String> products) {
        List<String> known = new ArrayList<>();
        if (products == null) {
            return known;
        }
        for (String name : products) {
            if (findByName(name) != null) {
                known.add(name);
            }
        }
        return known;
    }

    private void populateShops(List<ShopEntry> shops, long geBuy) {
        shopsContent.removeAll();
        List<ShopEntry> visible = visibleShops(shops);
        if (visible.isEmpty()) {
            shopsSection.setVisible(false);
            return;
        }
        shopsSection.setTitle(SectionSummaries.shops(visible));
        shopsSection.setVisible(true);
        if (!shopsSection.isExpanded()) {
            return;
        }
        for (ShopEntry shop : visible) {
            TradeCopy.ShopLine line = TradeCopy.shop(ironmanActive, shop, geBuy);
            String shopName = shop.shopName;
            boolean unmapped = mapMarkers != null && mapMarkers.knownWithoutPins(shopName);
            shopsContent.add(ShopRows.row(style, shop, line,
                () -> openWiki(shopName),
                unmapped ? null : label -> showOnMap(shopName, label)));
            shopsContent.add(Box.createRigidArea(new Dimension(0, 4)));
        }
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
        recipeSection.setTitle(SectionSummaries.crafting(recipes));
        recipeSection.setVisible(true);
        if (!recipeSection.isExpanded()) {
            return;
        }
        BuildExpander.Result tree = BuildExpander.expandSelected(rootName, rootId, root, viewPages(), expandedPaths);
        RecipeTreeView.fill(
            recipeBody, style, tree, !expandedPaths.isEmpty(), treeActions(), ownedCounts(), recipeMarks(), false);
        if (!fillingSection && !tree.pending.isEmpty()) {
            scheduleTreeLoads(tree.pending);
        }
        if (!treeFailed.isEmpty()) {
            recipeBody.add(noteLabel(
                "Some build-path pages could not be loaded. Open those ingredients again to retry."));
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
                refreshRecipeBody();
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
            if (treeFailed.remove(treeKey(itemName))) {
                itemDetailsService.retry(itemName);
            }
        }
        refreshRecipeBody();
    }

    private void expandFullTree() {
        if (shownItem == null || shownDetails == null || treeLoadInFlight) {
            return;
        }
        int gen = detailGeneration.get();
        String name = shownItem.name;
        Integer id = shownItem.id;
        ItemDetails root = shownDetails;
        for (String key : new ArrayList<>(treeFailed)) {
            itemDetailsService.retry(key);
        }
        treeFailed.clear();
        treeLoadInFlight = true;
        showTreeLoading();
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
                refreshRecipeBody();
            });
        });
    }

    private void showTreeLoading() {
        if (recipeBody == null) {
            return;
        }
        recipeBody.removeAll();
        recipeBody.add(plainLabel("Loading build path\u2026", "small", MUTED));
        recipeSection.setVisible(true);
        recipeBody.revalidate();
        recipeBody.repaint();
    }

    /** Rebuilds only the crafting section, not the rest of the detail pane. */
    private void refreshRecipeBody() {
        if (shownDetails == null || recipeSection == null) {
            return;
        }
        populateRecipes(shownDetails.recipes);
        recipeBody.revalidate();
        recipeBody.repaint();
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
                refreshRecipeBody();
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
        usedInContent.removeAll();
        List<String> known = knownProducts(products);
        if (known.isEmpty()) {
            usedInSection.setVisible(false);
            return;
        }
        usedInSection.setTitle(SectionSummaries.uses(known));
        usedInSection.setVisible(true);
        if (!usedInSection.isExpanded()) {
            return;
        }
        for (String name : known) {
            usedInContent.add(linkedRow(findByName(name), name, null));
            usedInContent.add(Box.createRigidArea(new Dimension(0, 2)));
        }
    }

    private void populateDrops(List<DropEntry> drops) {
        dropsContent.removeAll();
        dropBlocks.clear();
        if (!config.showDropSources() || drops == null || drops.isEmpty()) {
            dropsSection.setVisible(false);
            return;
        }
        dropsSection.setTitle(SectionSummaries.drops(drops));
        dropsSection.setVisible(true);
        if (!dropsSection.isExpanded()) {
            return;
        }
        dropsContent.add(dropSortControl());
        dropsContent.add(Box.createRigidArea(new Dimension(0, 4)));
        DropSort sort = config.dropSort() == null ? DropSort.RATE : config.dropSort();
        for (DropEntry drop : DropSorter.ordered(drops, sort)) {
            JPanel block = buildDropRow(drop);
            dropBlocks.put(monsterKey(drop.getSource()), block);
            dropsContent.add(block);
            dropsContent.add(Box.createRigidArea(new Dimension(0, 2)));
        }
    }

    private JComboBox<DropSort> dropSortControl() {
        JComboBox<DropSort> combo = new JComboBox<>(DropSort.values());
        style.apply(combo, "small");
        DropSort selected = config.dropSort() == null ? DropSort.RATE : config.dropSort();
        combo.setSelectedItem(selected);
        combo.setAlignmentX(LEFT_ALIGNMENT);
        int rowHeight = Math.max(22, combo.getPreferredSize().height);
        combo.setMinimumSize(new Dimension(0, rowHeight));
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, rowHeight));
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
        boolean open = openMonsters.contains(monsterKey(drop.getSource()));
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        row.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
        JLabel chevron = new JLabel(DropRowMarks.chevron(open));
        style.apply(chevron, "body");
        chevron.setForeground(new Color(0x64, 0xb4, 0xff));
        chevron.setCursor(row.getCursor());
        WrappingLabel label = new WrappingLabel();
        style.apply(label, "small");
        label.setForeground(Color.WHITE);
        label.setAlignmentX(LEFT_ALIGNMENT);
        label.setCursor(row.getCursor());
        row.putClientProperty("drop.chevron", chevron);
        row.putClientProperty("drop.label", label);
        row.putClientProperty("drop.entry", drop);
        row.add(chevron, BorderLayout.WEST);
        row.add(label, BorderLayout.CENTER);
        paintDropHeader(chevron, label, drop, open);
        capHeight(row);
        String monsterName = drop.getSource();
        MouseAdapter opener = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggleMonster(monsterName);
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
        row.addMouseListener(opener);
        label.addMouseListener(opener);
        chevron.addMouseListener(opener);
        JPanel block = verticalBox();
        block.add(row);
        if (openMonsters.contains(monsterKey(monsterName))) {
            block.add(locationBody(monsterName));
        }
        return block;
    }

    private void toggleMonster(String monsterName) {
        long started = System.nanoTime();
        String key = monsterKey(monsterName);
        if (!openMonsters.add(key)) {
            openMonsters.remove(key);
        } else {
            ensureMonster(monsterName);
        }
        JPanel block = dropBlocks.get(key);
        if (block == null) {
            if (shownDetails != null) {
                populateDrops(shownDetails.drops);
            }
        } else {
            refreshDropHeader(block, monsterName);
            replaceLocation(block, monsterName);
            showDropChange(block);
        }
        log.debug("Dropped-by toggle for {} painted in {} ms", monsterName, elapsedMs(started));
    }

    /**
     * Loads a monster page the first time its row is opened. Later expands
     * reuse {@link #locationViews} and do not call the wiki.
     */
    private void ensureMonster(String monsterName) {
        String key = monsterKey(monsterName);
        LocationView view = locationViews.get(key);
        if (view != null && view.started) {
            return;
        }
        if (view == null) {
            view = new LocationView();
            locationViews.put(key, view);
        }
        view.started = true;
        view.loading = true;
        view.error = null;
        int gen = monsterGeneration.get();
        final LocationView pending = view;
        final long requested = System.nanoTime();
        log.debug("Dropped-by fetch started for {}", monsterName);
        monsterService.getMonsterInfo(monsterName).whenComplete((info, error) -> {
            long parsed = System.nanoTime();
            SwingUtilities.invokeLater(() -> {
                long edt = System.nanoTime();
                if (!acceptMonsterResult(gen, monsterGeneration.get(), closed)) {
                    log.debug("Dropped-by result for {} ignored after an item change", monsterName);
                    return;
                }
                pending.loading = false;
                if (error != null) {
                    if (unwrap(error) instanceof RequestCancelledException) {
                        pending.started = false;
                        log.debug("Dropped-by fetch for {} cancelled", monsterName);
                        return;
                    }
                    pending.error = WikiFailures.monster(unwrap(error));
                    pending.info = Optional.empty();
                } else {
                    pending.error = null;
                    pending.info = info == null ? Optional.empty() : info;
                }
                JPanel block = dropBlocks.get(monsterKey(monsterName));
                if (block != null && openMonsters.contains(monsterKey(monsterName))) {
                    replaceLocation(block, monsterName);
                    showDropChange(block);
                }
                log.debug(
                    "Dropped-by locations for {} ready: wiki+parse {} ms, EDT wait {} ms, row update {} ms",
                    monsterName,
                    elapsedMs(requested, parsed),
                    elapsedMs(parsed, edt),
                    elapsedMs(edt)
                );
            });
        });
    }

    /**
     * Lays out the drop list now, on this EDT turn, instead of leaving a
     * revalidate that waits behind the next index or price UI task.
     */
    private void showDropChange(JPanel block) {
        if (dropsContent != null) {
            dropsContent.revalidate();
            dropsContent.validate();
            dropsContent.repaint();
        } else {
            block.revalidate();
            block.validate();
            block.repaint();
        }
    }

    private void paintDropHeader(JLabel chevron, WrappingLabel label, DropEntry drop, boolean open) {
        String detail = dropDetail(drop);
        chevron.setText(DropRowMarks.chevron(open));
        label.setHtml(DropRowMarks.html(escape(drop.getSource()), escape(detail), open));
        DropRate rate = DropRate.parse(drop.getRate(), drop.getQuantity());
        String tip = rate.original == null || rate.original.isEmpty() ? rate.display : rate.original;
        if (!open) {
            tip = tip == null || tip.isEmpty() ? DropRowMarks.HINT : tip + ". " + DropRowMarks.HINT;
        }
        label.setToolTipText(tip);
        if (chevron.getParent() instanceof JPanel) {
            ((JPanel) chevron.getParent()).setToolTipText(tip);
        }
    }

    private void refreshDropHeader(JPanel block, String monsterName) {
        if (block.getComponentCount() == 0 || !(block.getComponent(0) instanceof JPanel)) {
            return;
        }
        JPanel row = (JPanel) block.getComponent(0);
        Object chevron = row.getClientProperty("drop.chevron");
        Object label = row.getClientProperty("drop.label");
        Object entry = row.getClientProperty("drop.entry");
        if (chevron instanceof JLabel && label instanceof WrappingLabel && entry instanceof DropEntry) {
            paintDropHeader(
                (JLabel) chevron,
                (WrappingLabel) label,
                (DropEntry) entry,
                openMonsters.contains(monsterKey(monsterName)));
        }
    }

    private static String dropDetail(DropEntry drop) {
        StringBuilder detail = new StringBuilder();
        appendPiece(detail, drop.getLevel() == null || drop.getLevel().isEmpty() || "?".equals(drop.getLevel())
            ? "" : "Lv. " + drop.getLevel());
        appendPiece(detail, drop.getQuantity() == null || "?".equals(drop.getQuantity()) ? "" : drop.getQuantity());
        DropRate rate = DropRate.parse(drop.getRate(), drop.getQuantity());
        appendPiece(detail, "?".equals(rate.display) ? "" : rate.display);
        return detail.length() == 0 ? "?" : detail.toString();
    }

    private void replaceLocation(JPanel block, String monsterName) {
        while (block.getComponentCount() > 1) {
            block.remove(block.getComponentCount() - 1);
        }
        if (openMonsters.contains(monsterKey(monsterName))) {
            block.add(locationBody(monsterName));
        }
    }

    /** A monster page that started for a previous item must not paint into the new one. */
    public static boolean acceptMonsterResult(int capturedGeneration, int currentGeneration, boolean closed) {
        return !closed && capturedGeneration == currentGeneration;
    }

    private static long elapsedMs(long started) {
        return elapsedMs(started, System.nanoTime());
    }

    private static long elapsedMs(long started, long ended) {
        return (ended - started) / 1_000_000L;
    }

    private JComponent locationBody(String monsterName) {
        String key = monsterKey(monsterName);
        LocationView view = locationViews.get(key);
        if (view == null || view.loading || !view.started) {
            if (view == null || !view.started) {
                ensureMonster(monsterName);
            }
            return plainLabel("Loading\u2026", "small", MUTED);
        }
        if (view.error != null) {
            JPanel box = verticalBox();
            box.add(noteLabel(view.error));
            JButton retry = linkButton("Retry", "small");
            retry.setForeground(LINK);
            retry.addActionListener(e -> {
                locationViews.remove(key);
                monsterService.retry(monsterName);
                openMonsters.add(key);
                ensureMonster(monsterName);
                if (shownDetails != null) {
                    populateDrops(shownDetails.drops);
                }
            });
            box.add(retry);
            return box;
        }
        MonsterInfo info = view.info == null ? null : view.info.orElse(null);
        JPanel box = verticalBox();
        if (info != null && info.combatLevel > 0) {
            box.add(plainLabel("Combat " + info.combatLevel, "small", MUTED));
        }
        if (info == null || info.locations == null || info.locations.isEmpty()) {
            box.add(plainLabel("No locations are listed for this monster.", "small", MUTED));
        } else {
            CollapsibleSection locations = new CollapsibleSection(SectionState.LOCATIONS, sectionState, style);
            locations.setTitle(SectionSummaries.locations(info.locations));
            for (String place : info.locations) {
                WrappingLabel line = htmlLine("\u00b7 " + escape(place), "#c8c8c8", "small");
                line.setToolTipText(place);
                locations.body().add(line);
            }
            box.add(locations);
        }
        JButton wiki = linkButton("Open Wiki page", "button");
        wiki.setForeground(LINK);
        wiki.addActionListener(e -> openWiki(monsterName));
        box.add(wiki);
        if (info != null && info.mapped) {
            WrappingLabel map = ShopRows.mapLink(style);
            String monster = monsterName;
            map.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    showOnMap(monster, map);
                }
            });
            box.add(map);
        }
        return box;
    }

    private void showOnMap(String page, WrappingLabel label) {
        if (pinnedMapLabel != null && pinnedMapLabel != label && showsPinned(pinnedMapLabel)) {
            pinnedMapLabel.setHtml(ShopRows.MAP_LINK);
        }
        pinnedMapLabel = label;
        if (label != null) {
            label.setHtml(ShopRows.MAP_PINNED);
        }
        if (mapMarkers == null || page == null || page.isEmpty()) {
            return;
        }
        mapMarkers.show(page, status -> {
            if ("pinned".equals(status) && pinnedMapLabel != label) {
                return;
            }
            applyMapStatus(label, status);
            if (label == pinnedMapLabel && !"pinned".equals(status)) {
                pinnedMapLabel = null;
            }
        });
    }

    private static boolean showsPinned(WrappingLabel label) {
        String text = label.getText();
        return text != null && text.contains("Pinned. Open the world map");
    }

    private static void applyMapStatus(WrappingLabel label, String status) {
        if (label == null || status == null) {
            return;
        }
        if ("none".equals(status)) {
            label.setHtml("\u2316 No map pin on the wiki");
        } else if ("failed".equals(status)) {
            label.setHtml("\u2316 Could not load the map pin");
        } else {
            label.setHtml(ShopRows.MAP_PINNED);
        }
    }

    /** Client thread, once per game tick. Finishes a world-map jump after the map opens. */
    public void nudgeMap() {
        if (mapMarkers != null && !closed) {
            mapMarkers.nudge();
        }
    }

    private static String monsterKey(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private void navigateTo(SearchItem target) {
        if (!selectedItemName.isEmpty()) {
            SearchItem current = findByName(selectedItemName);
            if (current != null) {
                navStack.push(current);
            }
        }
        backButton.setVisible(true);
        loadItemDetail(target, true);
    }

    private void navigateBack() {
        if (navStack.isEmpty()) {
            return;
        }
        SearchItem prev = navStack.pop();
        backButton.setVisible(!navStack.isEmpty());
        loadItemDetail(prev, false);
    }

    private JPanel buildIconStrip(String title, boolean favourites) {
        JPanel bar = new JPanel() {
            @Override
            public Dimension getMaximumSize() {
                Dimension preferred = getPreferredSize();
                return new Dimension(Integer.MAX_VALUE, preferred.height);
            }

            @Override
            public Dimension getMinimumSize() {
                Dimension preferred = getPreferredSize();
                return new Dimension(0, preferred.height);
            }
        };
        bar.setLayout(new BoxLayout(bar, BoxLayout.Y_AXIS));
        bar.setBackground(ColorScheme.DARK_GRAY_COLOR);
        bar.setVisible(favourites);
        bar.setAlignmentX(LEFT_ALIGNMENT);
        bar.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        JLabel label = plainLabel(title, "small", MUTED);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
        IconGridPanel icons = new IconGridPanel(32, 4);
        icons.setBackground(ColorScheme.DARK_GRAY_COLOR);
        icons.setAlignmentX(LEFT_ALIGNMENT);
        bar.putClientProperty("icons", icons);
        bar.add(label);
        bar.add(icons);
        if (favourites) {
            favouriteIcons = icons;
        }
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
        rebuildFavourites();
    }

    private void rebuildFavourites() {
        if (favouriteIcons == null || favouritesPanel == null) {
            return;
        }
        favouriteIcons.removeAll();
        boolean any = false;
        synchronized (favouritesLock) {
            for (int id : favouriteIds) {
                SearchItem item = idIndex.get(id);
                if (item == null) {
                    continue;
                }
                any = true;
                favouriteIcons.add(buildHistoryCell(item));
            }
        }
        favouritesPanel.setVisible(any);
        favouritesPanel.revalidate();
        favouritesPanel.repaint();
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
            public void mousePressed(MouseEvent e) {
                showFavouriteMenu(e, cell, item);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                showFavouriteMenu(e, cell, item);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) {
                    return;
                }
                navStack.clear();
                backButton.setVisible(false);
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
        rebuildFavourites();
    }

    private void showFavouriteMenu(MouseEvent event, Component source, SearchItem item) {
        if (!event.isPopupTrigger()) {
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        boolean favourite = isFavourite(item.id);
        JMenuItem choice = new JMenuItem(favourite ? "Remove from favourites" : "Add to favourites");
        choice.addActionListener(e -> toggleFavourite(item));
        menu.add(choice);
        menu.show(source, event.getX(), event.getY());
    }

    private void updateStarButton(SearchItem item) {
        boolean favourite = isFavourite(item.id);
        starButton.setSelected(favourite);
        starButton.setToolTipText(favourite ? "Remove from favourites" : "Add to favourites");
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

    private void queueReflow() {
        if (reflowQueued || closed) {
            return;
        }
        reflowQueued = true;
        SwingUtilities.invokeLater(() -> {
            reflowQueued = false;
            if (closed || detailPanel == null) {
                return;
            }
            int width = detailPanel.getParent() instanceof javax.swing.JViewport
                ? detailPanel.getParent().getWidth()
                : getWidth();
            if (width <= 0 || Math.abs(width - lastWrapWidth) < 2) {
                return;
            }
            lastWrapWidth = width;
            detailPanel.revalidate();
        });
    }

    private static void setWrap(WrappingLabel label, String plain) {
        if (label == null) {
            return;
        }
        String text = plain == null ? "" : plain;
        label.setHtml(escape(text));
        label.setToolTipText(text.isEmpty() ? null : text);
    }

    private JPanel verticalBox() {
        ScrollWidthPanel panel = new ScrollWidthPanel();
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setOpaque(true);
        panel.setAlignmentX(LEFT_ALIGNMENT);
        return panel;
    }

    private JLabel plainLabel(String text, String role, Color color) {
        WrappingLabel label = new WrappingLabel();
        style.apply(label, role);
        label.setForeground(color);
        label.setAlignmentX(LEFT_ALIGNMENT);
        setWrap(label, text);
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
        JButton button = new WrappingButton(text);
        style.apply(button, role);
        button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        button.setOpaque(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setAlignmentX(LEFT_ALIGNMENT);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setMargin(new Insets(0, 0, 0, 0));
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
        EllipsisLabel label = new EllipsisLabel();
        style.apply(label, "small");
        Color nameColor = target != null ? LINK : ColorScheme.LIGHT_GRAY_COLOR;
        label.setPieces(text, nameColor, secondary, MUTED);
        if (target != null) {
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

    /** Parsed monster locations for the open item. Expanding a row reads this and does not fetch again. */
    private static final class LocationView {
        private boolean started;
        private boolean loading;
        private String error;
        private Optional<MonsterInfo> info = Optional.empty();
    }
}
