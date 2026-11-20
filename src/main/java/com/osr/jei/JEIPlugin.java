package com.osr.jei;

import com.google.inject.Provides;
import com.osr.jei.ironman.IronmanAccounts;
import com.osr.jei.lookup.CanonicalIds;
import com.osr.jei.lookup.LookupTargets;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.AccountHashChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.VarbitChanged;
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
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Sidebar item browser. Right-click an inventory, bank, worn, or ground item and choose JEI Lookup.
 */
@Slf4j
@PluginDescriptor(
    name = "OSRS Item Browser",
    description = "Browse items, drop tables, shop prices, and sources",
    tags = {"item", "drops", "wiki", "browser", "search", "jei"}
)
public class JEIPlugin extends Plugin {

    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ClientToolbar clientToolbar;
    @Inject private ItemManager itemManager;
    @Inject private JEIConfig config;
    @Inject private JEIPanel panel;

    private NavigationButton navButton;
    private boolean ironmanActive;
    private boolean ironmanKnown;

    @Override
    protected void startUp() {
        SwingUtilities.invokeLater(() -> {
            if (panel.isClosed()) {
                return;
            }
            try {
                panel.init();
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
    }

    /**
     * The logged-in account changed. {@code AccountHashChanged} is the client
     * event for that; the ironman varbit and game state carry the type used
     * by AUTO mode.
     */
    @Subscribe
    public void onAccountHashChanged(AccountHashChanged event) {
        if (!panel.isClosed()) {
            publishIronman();
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        if (panel.isClosed() || event.getVarbitId() != VarbitID.IRONMAN) {
            return;
        }
        publishIronman();
    }

    @Override
    protected void shutDown() {
        panel.shutdown();
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
     * Adds one JEI Lookup entry on ground items and on Examine for inventory,
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
            .setOption("<col=ff9040>JEI</col> Lookup")
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
        g.setColor(new Color(200, 160, 50));
        g.fillRoundRect(0, 0, 15, 15, 4, 4);
        g.setColor(new Color(80, 60, 10));
        g.drawRoundRect(0, 0, 15, 15, 4, 4);
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 7));
        g.drawString("JEI", 2, 11);
        g.dispose();
        return img;
    }
}
