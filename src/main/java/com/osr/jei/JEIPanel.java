package com.osr.jei;

import com.osr.jei.model.DropEntry;
import com.osr.jei.model.EquipmentStats;
import com.osr.jei.model.MonsterInfo;
import com.osr.jei.model.RecipeInfo;
import com.osr.jei.model.RecipeIngredient;
import com.osr.jei.model.SearchItem;
import com.osr.jei.model.ShopEntry;
import com.osr.jei.pricing.Alchemy;
import com.osr.jei.pricing.PriceQuote;
import com.osr.jei.search.ItemSearch;
import com.osr.jei.service.ItemDetails;
import com.osr.jei.service.ItemDetailsService;
import com.osr.jei.service.MonsterService;
import com.osr.jei.service.PriceService;
import com.osr.jei.ui.SplitLayout;
import com.osr.jei.ui.UiStyle;
import com.osr.jei.ui.WrappingLabel;
import com.osr.jei.wiki.RequestCancelledException;
import com.osr.jei.wiki.WikiHttp;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.ItemID;
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
    @Inject private MonsterService monsterService;
    @Inject private WikiHttp wikiHttp;

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
    private JLabel lblName;
    private JLabel lblPrice;
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
    private PriceQuote shownPrice = PriceQuote.MISSING;
    private PriceQuote shownNature = PriceQuote.MISSING;
    private boolean pricesFetched;
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
        if ("favourites".equals(key) || "recent".equals(key)) {
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

        JPanel nameRow = new JPanel(new BorderLayout(4, 0)) {
            @Override
            public Dimension getMaximumSize() {
                Dimension preferred = getPreferredSize();
                return new Dimension(Integer.MAX_VALUE, preferred.height);
            }
        };
        nameRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        nameRow.setAlignmentX(LEFT_ALIGNMENT);
        nameRow.add(lblName, BorderLayout.CENTER);
        nameRow.add(starButton, BorderLayout.EAST);

        lblMembersWeight = plainLabel(" ", "small", MUTED);
        lblPrice = plainLabel(" ", "price", GOLD);
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

        detailPanel.add(backButton);
        detailPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        detailPanel.add(nameRow);
        detailPanel.add(Box.createRigidArea(new Dimension(0, 3)));
        detailPanel.add(lblMembersWeight);
        detailPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        detailPanel.add(lblPrice);
        detailPanel.add(Box.createRigidArea(new Dimension(0, 2)));
        detailPanel.add(lblHaAlch);
        detailPanel.add(Box.createRigidArea(new Dimension(0, 6)));
        detailPanel.add(equipmentSection);
        detailPanel.add(shopsSection);
        detailPanel.add(usedInButton);
        detailPanel.add(dropsButton);
        detailPanel.add(Box.createRigidArea(new Dimension(0, 6)));
        detailPanel.add(recipeSection);
        detailPanel.add(Box.createRigidArea(new Dimension(0, 6)));
        detailPanel.add(wikiButton);
        return detailPanel;
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
                    item = new SearchItem(id, name, comp.isMembers(), comp.getHaPrice());
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
            List<SearchItem> results = ItemSearch.rank(allItems, lower, SEARCH_LIMIT);
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
        boolean fetchPrices = config.showPrices();
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
        lblPrice.setText(config.showPrices() ? "Loading..." : " ");
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

        if (!config.showPrices()) {
            lblPrice.setText(" ");
        } else if (shownPrice == null || !shownPrice.known()) {
            lblPrice.setText("Not on GE / no price data");
        } else if (shownPrice.instantBuy > 0) {
            lblPrice.setText(String.format("GE buy: %,d gp", shownPrice.instantBuy));
        } else {
            lblPrice.setText(String.format("GE price: %,d gp", shownPrice.mid));
        }

        long geBuy = config.showPrices() && shownPrice != null ? shownPrice.buyPrice() : -1;
        long nature = config.showPrices() && shownNature != null ? shownNature.buyPrice() : -1;
        Alchemy.Result alchemy = Alchemy.compute(item.haPrice, geBuy, nature);
        if (alchemy == null) {
            lblHaAlch.setText(" ");
        } else if (alchemy.profit == null) {
            lblHaAlch.setText(String.format("High Alch: %,d gp", alchemy.haValue));
            lblHaAlch.setForeground(NEUTRAL);
        } else {
            String profit = alchemy.profit >= 0
                ? String.format("+%,d gp", alchemy.profit)
                : String.format("%,d gp", alchemy.profit);
            lblHaAlch.setText(String.format("High Alch: %,d gp  (%s)", alchemy.haValue, profit));
            lblHaAlch.setForeground(alchemy.profit >= 0 ? GOOD : BAD);
        }

        populateEquipment(details.stats);
        populateShops(details.shops, geBuy);
        populateRecipes(details.recipes);
        populateUsedIn(details.products);
        populateDrops(details.drops);
        detailPanel.revalidate();
        detailPanel.repaint();
    }

    private void populateEquipment(Optional<EquipmentStats> statsOpt) {
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
        equipmentSection.setVisible(true);
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
            String stock = "";
            if (shop.infiniteStock()) {
                stock = " (\u221e)";
            } else if (shop.stock > 0) {
                stock = " (" + String.format("%,d", shop.stock) + ")";
            }
            String unit = shop.isCoins() ? "gp" : shop.currency;
            String price = " \u2014 " + String.format("%,d %s", shop.price, unit);
            String priceColor = "#bebebe";
            if (shop.isCoins() && geBuy > 0 && shop.price > 0) {
                priceColor = shop.price <= geBuy ? "#64d264" : "#dc6464";
            }
            String html = "<font color='#bebebe'>\u00b7 " + escape(shop.shopName) + escape(stock) + "</font>"
                + "<font color='" + priceColor + "'>" + escape(price) + "</font>";
            shopsContent.add(htmlLine(html, null, "small"));
        }
        shopsSection.setVisible(true);
    }

    private void populateRecipes(List<RecipeInfo> recipes) {
        recipeBody.removeAll();
        if (recipes == null || recipes.isEmpty()) {
            recipeSection.setVisible(false);
            return;
        }
        JLabel header = plainLabel(recipes.size() == 1 ? "Crafting recipe" : "Crafting recipes", "bodyBold", Color.WHITE);
        recipeBody.add(header);
        recipeBody.add(Box.createRigidArea(new Dimension(0, 3)));
        for (int i = 0; i < recipes.size(); i++) {
            if (i > 0) {
                recipeBody.add(Box.createRigidArea(new Dimension(0, 8)));
            }
            RecipeInfo recipe = recipes.get(i);
            recipeBody.add(htmlLine(escape(recipeSummary(recipe)), "#96dc96", "small"));
            if (!recipe.ingredients.isEmpty()) {
                recipeBody.add(plainLabel("Ingredients", "bodyBold", ColorScheme.LIGHT_GRAY_COLOR));
                for (RecipeIngredient ingredient : recipe.ingredients) {
                    recipeBody.add(buildIngredientRow(ingredient));
                }
            }
        }
        recipeSection.setVisible(true);
    }

    private static String recipeSummary(RecipeInfo recipe) {
        StringBuilder info = new StringBuilder();
        if (!recipe.skill.isEmpty()) {
            info.append(recipe.skill);
            if (recipe.level > 0) {
                info.append(" (Lv. ").append(recipe.level).append(")");
            }
        }
        if (!recipe.facility.isEmpty()) {
            if (info.length() > 0) {
                info.append("  \u00b7  ");
            }
            info.append(recipe.facility);
        }
        if (recipe.experience > 0) {
            if (info.length() > 0) {
                info.append("  \u00b7  ");
            }
            if (recipe.experience == Math.floor(recipe.experience)) {
                info.append((int) recipe.experience);
            } else {
                info.append(recipe.experience);
            }
            info.append(" XP");
        }
        return info.toString();
    }

    private JPanel buildIngredientRow(RecipeIngredient ingredient) {
        SearchItem found = findByName(ingredient.name);
        String text = ingredient.quantity > 1
            ? ingredient.name + " \u00d7 " + ingredient.quantity
            : ingredient.name;
        return linkedRow(found, text, null);
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
        gridCardLayout.show(gridCardPanel, "usedIn");
    }

    private void showDropsExpanded() {
        monsterGeneration.incrementAndGet();
        usedInListPanel.removeAll();
        usedInListPanel.add(backToGridButton());
        usedInListPanel.add(plainLabel("Dropped by:  " + selectedItemName, "title", Color.WHITE));
        usedInListPanel.add(plainLabel("Click a monster to see its locations", "small", MUTED));
        usedInListPanel.add(Box.createRigidArea(new Dimension(0, 6)));
        List<DropEntry> drops = shownDetails == null ? Collections.emptyList() : shownDetails.drops;
        for (DropEntry drop : drops) {
            usedInListPanel.add(buildDropRow(drop));
            usedInListPanel.add(Box.createRigidArea(new Dimension(0, 3)));
        }
        usedInListPanel.revalidate();
        usedInListPanel.repaint();
        gridCardLayout.show(gridCardPanel, "usedIn");
    }

    private JPanel buildDropRow(DropEntry drop) {
        StringBuilder detail = new StringBuilder();
        appendPiece(detail, drop.getLevel() == null || drop.getLevel().isEmpty() || "?".equals(drop.getLevel())
            ? "" : "Lv. " + drop.getLevel());
        appendPiece(detail, drop.getQuantity() == null || "?".equals(drop.getQuantity()) ? "" : drop.getQuantity());
        appendPiece(detail, drop.getRate() == null || "?".equals(drop.getRate()) ? "" : drop.getRate());
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
                if (item != null) {
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
