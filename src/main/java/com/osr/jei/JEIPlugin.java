package com.osr.jei;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
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

/**
 * Sidebar item browser. Right-click an inventory item or a ground item and choose JEI Lookup.
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
    @Inject private JEIPanel panel;

    private NavigationButton navButton;

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
                    if (!panel.isClosed()) {
                        panel.ensureItemIndex(client.getGameState());
                    }
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
        SwingUtilities.invokeLater(() -> panel.onConfigChanged(key));
    }

    /**
     * Adds JEI Lookup above Examine on ground items and on widget item examines.
     * Inventory examines use {@link MenuAction#CC_OP_LOW_PRIORITY}; the old
     * {@code EXAMINE_ITEM} action is deprecated.
     */
    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event) {
        if (client.getMenu() == null || panel.isClosed()) {
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
            .onClick(e -> SwingUtilities.invokeLater(() -> {
                if (navButton != null) {
                    clientToolbar.openPanel(navButton);
                }
                panel.lookupItem(lookupId);
            }));
    }

    private int itemIdForLookup(MenuEntryAdded event) {
        int type = event.getType();
        if (type == MenuAction.EXAMINE_ITEM_GROUND.getId()) {
            return event.getIdentifier() >= 0 ? event.getIdentifier() : -1;
        }
        if (type == MenuAction.CC_OP_LOW_PRIORITY.getId() && "Examine".equals(event.getOption())) {
            if (event.getItemId() > 0) {
                return event.getItemId();
            }
            return itemIdFromWidget(event.getActionParam1(), event.getActionParam0());
        }
        return -1;
    }

    private int itemIdFromWidget(int widgetId, int childIndex) {
        Widget widget = client.getWidget(widgetId);
        if (widget == null) {
            return -1;
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
