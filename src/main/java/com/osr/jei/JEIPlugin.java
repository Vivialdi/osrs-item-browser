package com.osr.jei;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Entry point for the OSRS JEI plugin.
 *
 * This plugin adds a sidebar panel to RuneLite where you can:
 *   - Search any OSRS item by name
 *   - See its Grand Exchange price
 *   - See every monster that drops it (with rates)
 *   - Open the OSRS Wiki page for full details
 *
 * Right-clicking any item in the game (inventory, bank, equipment, ground)
 * shows a "JEI Lookup" option that jumps straight to that item's detail page
 * in the sidebar.
 */
@Slf4j
@PluginDescriptor(
    name        = "OSRS JEI",
    description = "Browse items, drop tables, and sources — inspired by JEI from Minecraft",
    tags        = {"item", "drops", "wiki", "browser", "search", "jei"}
)
public class JEIPlugin extends Plugin {

    @Inject private Client        client;
    @Inject private ClientToolbar clientToolbar;
    @Inject private JEIPanel      panel;

    private NavigationButton navButton;

    @Override
    protected void startUp() {
        // Build the Swing UI on the Event Dispatch Thread (required by Java Swing)
        SwingUtilities.invokeLater(() -> {
            try {
                panel.init();

                navButton = NavigationButton.builder()
                    .tooltip("OSRS JEI — Item Browser")
                    .icon(createIcon())
                    .priority(6)
                    .panel(panel)
                    .build();

                clientToolbar.addNavigation(navButton);
                log.info("OSRS JEI plugin started successfully");
            } catch (Exception e) {
                // Without this catch, Swing swallows the error silently — you'd never know it failed
                log.error("OSRS JEI failed to start", e);
            }
        });
    }

    @Override
    protected void shutDown() {
        SwingUtilities.invokeLater(() -> {
            clientToolbar.removeNavigation(navButton);
            log.info("OSRS JEI plugin stopped");
        });
    }

    // ── Right-click item lookup ────────────────────────────────────────────────

    /**
     * Adds a "JEI Lookup" entry to the right-click menu whenever an item's
     * "Examine" option is present — i.e. when the player right-clicks an item
     * in their inventory, bank, equipment, or on the ground.
     *
     * Clicking the entry selects the JEI sidebar panel and loads that item's
     * detail page (price, recipe, drop sources, wiki link).
     */
    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event) {
        int type = event.getType();

        // Fire on item Examine — inventory items and items on the ground.
        // (Bank items use widget-based menus with a different action type and
        // cannot be intercepted the same way without widget inspection.)
        if (type != MenuAction.EXAMINE_ITEM.getId()
                && type != MenuAction.EXAMINE_ITEM_GROUND.getId()) {
            return;
        }

        final int itemId = event.getIdentifier();

        // Insert our entry just above "Examine" (position -1 = last slot)
        client.createMenuEntry(-1)
            .setOption("<col=ff9040>JEI</col> Lookup")
            .setTarget(event.getTarget())           // keeps the coloured item name
            .setType(MenuAction.RUNELITE)
            .onClick(e -> SwingUtilities.invokeLater(() -> {
                // Open the JEI sidebar panel, then load the item detail
                clientToolbar.openPanel(navButton);
                panel.lookupItem(itemId);
            }));
    }

    // ── Config ─────────────────────────────────────────────────────────────────

    @Provides
    JEIConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(JEIConfig.class);
    }

    // ── Icon ───────────────────────────────────────────────────────────────────

    /**
     * Draws a simple 16x16 icon for the navigation button.
     * It's a golden square with "JEI" — replace with a real PNG later if you want.
     */
    private BufferedImage createIcon() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // OSRS gold-ish background
        g.setColor(new Color(200, 160, 50));
        g.fillRoundRect(0, 0, 15, 15, 4, 4);

        // Dark border
        g.setColor(new Color(80, 60, 10));
        g.drawRoundRect(0, 0, 15, 15, 4, 4);

        // Label
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 7));
        g.drawString("JEI", 2, 10);

        g.dispose();
        return img;
    }
}
