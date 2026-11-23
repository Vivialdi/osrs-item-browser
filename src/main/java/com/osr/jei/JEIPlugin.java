package com.osr.jei;

import com.google.inject.Provides;
import com.osr.jei.equipment.WornGear;
import com.osr.jei.ironman.IronmanAccounts;
import com.osr.jei.lookup.CanonicalIds;
import com.osr.jei.lookup.LookupTargets;
import com.osr.jei.owned.OwnedItems;
import com.osr.jei.requirements.PlayerProgress;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.events.AccountHashChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

import javax.inject.Inject;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Sidebar item browser. Right-click an inventory, bank, worn, or ground item and choose Item Browser Lookup.
 */
@Slf4j
@PluginDescriptor(
    name = "OSRS Item Browser",
    description = "Browse items, drop tables, shop prices, and sources",
    tags = {"item", "drops", "wiki", "browser", "search", "recipe"}
)
public class JEIPlugin extends Plugin {

    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ClientToolbar clientToolbar;
    @Inject private ItemManager itemManager;
    @Inject private JEIConfig config;
    @Inject private JEIPanel panel;
    @Inject private PlayerProgress progress;
    @Inject private ConfigManager configManager;

    /** Set only after InventoryID.BANK has changed, or a saved per-account flag and a loaded container. */
    private boolean bankSeen;

    private NavigationButton navButton;
    private boolean ironmanActive;
    private boolean ironmanKnown;
    private volatile OwnedItems.Snapshot owned = OwnedItems.Snapshot.NONE;
    private volatile WornGear worn = WornGear.LOGGED_OUT;

    @Override
    protected void startUp() {
        SwingUtilities.invokeLater(() -> {
            if (panel.isClosed()) {
                return;
            }
            try {
                panel.init();
                progress.setListener(snapshot -> SwingUtilities.invokeLater(() -> {
                    if (!panel.isClosed()) {
                        panel.onProgress(snapshot);
                    }
                }));
                navButton = NavigationButton.builder()
                    .tooltip("OSRS Item Browser")
                    .icon(createIcon())
                    .priority(6)
                    .panel(panel)
                    .build();
                clientToolbar.addNavigation(navButton);
                log.info("OSRS Item Browser started");
                clientThread.invokeLater(() -> {
                    if (panel.isClosed()) {
                        return;
                    }
                    panel.ensureItemIndex(client.getGameState());
                    publishIronman();
                    owned = OwnedItems.Snapshot.NONE;
                    publishOwned();
                    worn = WornGear.LOGGED_OUT;
                    publishWorn();
                    progress.refreshSkills();
                });
            } catch (Exception e) {
                log.error("OSRS Item Browser failed to start", e);
            }
        });
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (panel.isClosed()) {
            return;
        }
        GameState state = event.getGameState();
        panel.ensureItemIndex(state);
        if (state == GameState.LOGIN_SCREEN || state == GameState.LOGGING_IN || state == GameState.LOGGED_IN) {
            publishIronman();
        }
        publishOwned();
        publishWorn();
        progress.refreshSkills();
    }

    /** Real levels only. Boosts do not change the snapshot, so the sidebar stays still. */
    @Subscribe
    public void onStatChanged(StatChanged event) {
        if (!panel.isClosed()) {
            progress.refreshSkills();
        }
    }

    /** Drains a quest-state refresh queued by a varbit change. One read per tick. */
    @Subscribe
    public void onGameTick(GameTick event) {
        if (!panel.isClosed()) {
            progress.readQuestsIfDirty();
            panel.nudgeMap();
        }
    }

    /**
     * Inventory, worn equipment, and bank. Read on the client thread and
     * forwarded only when the canonical totals actually change. The bank
     * counts only after that container has changed, not merely because an
     * empty container object exists.
     */
    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (panel.isClosed()) {
            return;
        }
        int containerId = event.getContainerId();
        if (containerId != InventoryID.INV && containerId != InventoryID.BANK && containerId != InventoryID.WORN) {
            return;
        }
        if (containerId == InventoryID.BANK) {
            rememberBank();
        }
        publishOwned();
        if (containerId == InventoryID.WORN) {
            publishWorn();
        }
    }

    /**
     * The logged-in account changed. {@code AccountHashChanged} is the client
     * event for that; the ironman varbit and game state carry the type used
     * by AUTO mode.
     */
    @Subscribe
    public void onAccountHashChanged(AccountHashChanged event) {
        bankSeen = false;
        if (!panel.isClosed()) {
            publishIronman();
            publishOwned();
            publishWorn();
            progress.refreshSkills();
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        if (panel.isClosed()) {
            return;
        }
        progress.markQuestsDirty();
        if (event.getVarbitId() != VarbitID.IRONMAN) {
            return;
        }
        publishIronman();
    }

    @Override
    protected void shutDown() {
        panel.shutdown();
        progress.setListener(null);
        owned = OwnedItems.Snapshot.NONE;
        worn = WornGear.LOGGED_OUT;
        SwingUtilities.invokeLater(() -> {
            if (navButton != null) {
                clientToolbar.removeNavigation(navButton);
                navButton = null;
            }
            log.info("OSRS Item Browser stopped");
        });
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!"osrjei".equals(event.getGroup())) {
            return;
        }
        String key = event.getKey();
        if ("ironmanMode".equals(key)) {
            clientThread.invokeLater(this::publishIronman);
            return;
        }
        SwingUtilities.invokeLater(() -> panel.onConfigChanged(key));
    }

    /**
     * Reads the account-type varbit on the client thread and refreshes the
     * open detail view only when the effective ironman flag actually changes.
     */
    private void publishIronman() {
        if (panel.isClosed()) {
            return;
        }
        int accountType = 0;
        try {
            accountType = client.getVarbitValue(VarbitID.IRONMAN);
        } catch (Exception ignored) {
            accountType = 0;
        }
        boolean active = IronmanAccounts.effective(config.ironmanMode(), accountType);
        if (ironmanKnown && !IronmanAccounts.shouldRefreshDetail(ironmanActive, active)) {
            return;
        }
        ironmanActive = active;
        ironmanKnown = true;
        SwingUtilities.invokeLater(() -> {
            if (!panel.isClosed()) {
                panel.onIronmanChanged(active);
            }
        });
    }

    /**
     * Rebuilds the owned snapshot from the live containers. No-ops off the
     * client thread by rescheduling, and skips the Swing update when the
     * canonical map is unchanged.
     */
    private void publishOwned() {
        if (!client.isClientThread()) {
            clientThread.invokeLater(this::publishOwned);
            return;
        }
        if (panel.isClosed()) {
            return;
        }
        OwnedItems.Snapshot next = captureOwned();
        if (next.equals(owned)) {
            return;
        }
        owned = next;
        SwingUtilities.invokeLater(() -> {
            if (!panel.isClosed()) {
                panel.onOwnedChanged(next);
            }
        });
    }

    private OwnedItems.Snapshot captureOwned() {
        try {
            ItemContainer inventory = client.getItemContainer(InventoryID.INV);
            ItemContainer worn = client.getItemContainer(InventoryID.WORN);
            ItemContainer bank = client.getItemContainer(InventoryID.BANK);
            return OwnedItems.snapshot(
                readContainer(inventory),
                readContainer(worn),
                readContainer(bank),
                inventory != null,
                bankHasBeenSeen()
            );
        } catch (RuntimeException ex) {
            log.debug("Could not read item containers: {}", ex.getMessage());
            return OwnedItems.Snapshot.NONE;
        }
    }

    private void rememberBank() {
        bankSeen = true;
        if (configManager == null) {
            return;
        }
        try {
            configManager.setRSProfileConfiguration("osrjei", "bankSeen", Boolean.TRUE);
        } catch (RuntimeException ex) {
            log.debug("Could not remember that the bank was opened: {}", ex.getMessage());
        }
    }

    /**
     * A non-null bank container is not enough: the client can hold an empty
     * one before the player opens the bank. The flag is per account.
     */
    private boolean bankHasBeenSeen() {
        if (bankSeen) {
            return true;
        }
        if (configManager == null || client.getItemContainer(InventoryID.BANK) == null) {
            return false;
        }
        try {
            Boolean saved = configManager.getRSProfileConfiguration("osrjei", "bankSeen", Boolean.class);
            if (Boolean.TRUE.equals(saved)) {
                bankSeen = true;
                return true;
            }
        } catch (RuntimeException ex) {
            log.debug("Could not read the saved bank flag: {}", ex.getMessage());
        }
        return false;
    }

    private Map<Integer, Long> readContainer(ItemContainer container) {
        if (container == null) {
            return Collections.emptyMap();
        }
        Item[] items = container.getItems();
        if (items == null) {
            return Collections.emptyMap();
        }
        List<OwnedItems.Slot> slots = new ArrayList<>();
        for (int i = 0; i < items.length; i++) {
            Item item = items[i];
            if (item == null || item.getId() < 0 || item.getQuantity() <= 0) {
                continue;
            }
            try {
                ItemComposition composition = itemManager.getItemComposition(item.getId());
                int canonical = itemManager.canonicalize(item.getId());
                slots.add(new OwnedItems.Slot(canonical, item.getQuantity(), composition.getPlaceholderTemplateId()));
            } catch (RuntimeException ignored) {
                // one unreadable slot does not discard the rest of the container
            }
        }
        return OwnedItems.count(slots);
    }

    /**
     * Worn item ids for the equipment comparison. Read on the client thread.
     * This does not fetch wiki pages; the panel does that only after the
     * comparison is opened.
     */
    private void publishWorn() {
        if (!client.isClientThread()) {
            clientThread.invokeLater(this::publishWorn);
            return;
        }
        if (panel.isClosed()) {
            return;
        }
        WornGear next = captureWorn();
        if (next.equals(worn)) {
            return;
        }
        worn = next;
        SwingUtilities.invokeLater(() -> {
            if (!panel.isClosed()) {
                panel.onWornChanged(next);
            }
        });
    }

    private WornGear captureWorn() {
        int[] ids = new int[WornGear.SLOTS];
        Arrays.fill(ids, -1);
        boolean loggedIn = false;
        try {
            loggedIn = client.getGameState() == GameState.LOGGED_IN;
            if (!loggedIn) {
                return WornGear.LOGGED_OUT;
            }
            ItemContainer container = client.getItemContainer(InventoryID.WORN);
            if (container == null || container.getItems() == null) {
                return new WornGear(true, ids);
            }
            Item[] items = container.getItems();
            for (EquipmentInventorySlot slot : EquipmentInventorySlot.values()) {
                int index = slot.getSlotIdx();
                if (index < 0 || index >= items.length || index >= ids.length) {
                    continue;
                }
                Item item = items[index];
                if (item == null || item.getId() <= 0 || item.getQuantity() <= 0) {
                    continue;
                }
                ids[index] = itemManager.canonicalize(item.getId());
            }
        } catch (RuntimeException ex) {
            log.debug("Could not read worn equipment: {}", ex.getMessage());
            return loggedIn ? new WornGear(true, ids) : WornGear.LOGGED_OUT;
        }
        return new WornGear(true, ids);
    }

    /**
     * Adds one Item Browser Lookup entry on ground items and on Examine for inventory,
     * bank (including placeholders and the bank side inventory), worn equipment,
     * and the equipment stats interface.
     */
    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event) {
        if (client.getMenu() == null || panel.isClosed()) {
            return;
        }
        if (LookupTargets.alreadyOffered(optionTexts(client.getMenu().getMenuEntries()))) {
            return;
        }
        int itemId = itemIdForLookup(event);
        if (itemId < 0) {
            return;
        }
        final int lookupId = itemId;
        client.getMenu().createMenuEntry(-1)
            .setOption(LookupTargets.MENU_OPTION)
            .setTarget(event.getTarget())
            .setType(MenuAction.RUNELITE)
            .setDeprioritized(true)
            .onClick(e -> clientThread.invoke(() -> openLookup(lookupId)));
    }

    private void openLookup(int itemId) {
        int resolved = canonicalItemId(itemId);
        SwingUtilities.invokeLater(() -> {
            if (navButton != null) {
                clientToolbar.openPanel(navButton);
            }
            panel.lookupItem(resolved);
        });
    }

    /**
     * Notes and placeholders are resolved from the composition, then
     * {@code itemManager.canonicalize} maps worn variants. Client thread only.
     */
    private int canonicalItemId(int itemId) {
        if (itemId < 0) {
            return -1;
        }
        try {
            ItemComposition composition = itemManager.getItemComposition(itemId);
            int fromComposition = CanonicalIds.resolve(
                itemId,
                composition.getNote(),
                composition.getLinkedNoteId(),
                composition.getPlaceholderTemplateId(),
                composition.getPlaceholderId());
            return itemManager.canonicalize(fromComposition);
        } catch (Exception ignored) {
            return itemId;
        }
    }

    private int itemIdForLookup(MenuEntryAdded event) {
        int type = event.getType();
        if (LookupTargets.groundExamine(type)) {
            return event.getIdentifier() >= 0 ? event.getIdentifier() : -1;
        }
        int group = WidgetUtil.componentToInterface(event.getActionParam1());
        if (!LookupTargets.widgetExamine(type, event.getOption(), group)) {
            return -1;
        }
        if (event.getItemId() > 0) {
            return event.getItemId();
        }
        return itemIdFromWidget(event.getActionParam1(), event.getActionParam0());
    }

    private int itemIdFromWidget(int widgetId, int childIndex) {
        Widget widget = client.getWidget(widgetId);
        if (widget == null) {
            return -1;
        }
        if (widget.getItemId() > 0) {
            return widget.getItemId();
        }
        Widget child = widget.getChild(childIndex);
        if (child != null && child.getItemId() > 0) {
            return child.getItemId();
        }
        Widget worn = widget.getChild(1);
        if (worn != null && worn.getItemId() > 0) {
            return worn.getItemId();
        }
        return -1;
    }

    private static List<String> optionTexts(MenuEntry[] entries) {
        List<String> options = new ArrayList<>();
        if (entries == null) {
            return options;
        }
        for (MenuEntry entry : entries) {
            if (entry != null) {
                options.add(entry.getOption());
            }
        }
        return options;
    }

    @Provides
    JEIConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(JEIConfig.class);
    }

    private BufferedImage createIcon() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(200, 160, 50));
        g.drawOval(1, 1, 9, 9);
        g.setColor(new Color(230, 200, 90));
        g.fillOval(3, 3, 5, 5);
        g.setColor(new Color(80, 60, 10));
        g.drawLine(9, 9, 14, 14);
        g.dispose();
        return img;
    }
}
