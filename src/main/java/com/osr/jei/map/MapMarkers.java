package com.osr.jei.map;

import com.osr.jei.wiki.MapPins;
import com.osr.jei.wiki.TtlCache;
import com.osr.jei.wiki.WikiPages;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * On-demand world-map pins. A click loads one wiki page (or the cached parse),
 * then adds markers. Nothing is prefetched for a shop or drop list.
 */
@Singleton
public class MapMarkers {

    private static final int JUMP_TICKS = 6;

    private final Client client;
    private final ClientThread clientThread;
    private final WorldMapPointManager points;
    private final WikiPages pages;
    private final TtlCache<String, List<MapPins.Pin>> pins = new TtlCache<>(32, WikiPages.TTL_MS);
    private final List<WorldMapPoint> active = new ArrayList<>();
    private final BufferedImage icon = pinIcon();
    private WorldPoint pending;
    private int jumpTicks;

    @Inject
    public MapMarkers(Client client, ClientThread clientThread, WorldMapPointManager points, WikiPages pages) {
        this.client = client;
        this.clientThread = clientThread;
        this.points = points;
        this.pages = pages;
    }

    /** Drops every pin this plugin added. Safe from the EDT or the client thread. */
    public void clear() {
        if (clientThread == null) {
            return;
        }
        clientThread.invokeLater(this::clearOnClient);
    }

    /**
     * Loads coordinates for one page title, then shows them. {@code status} runs
     * on the EDT with "opened", "pinned", "none", or "failed". "pinned" means
     * the pins are placed but the world-map orb was not on screen to open.
     */
    public void show(String title, Consumer<String> status) {
        if (title == null || title.trim().isEmpty() || pages == null) {
            report(status, "none");
            return;
        }
        String key = title.trim().toLowerCase(java.util.Locale.ROOT);
        List<MapPins.Pin> cached = pins.get(key, System.currentTimeMillis());
        if (cached != null) {
            if (cached.isEmpty()) {
                report(status, "none");
            } else {
                place(title, cached, status);
            }
            return;
        }
        pages.load(title).whenComplete((page, error) -> {
            List<MapPins.Pin> parsed = error == null && page != null && !page.missing
                ? MapPins.parse(page.wikitext)
                : java.util.Collections.emptyList();
            if (error == null && page != null) {
                pins.put(key, parsed, System.currentTimeMillis());
            }
            if (error != null) {
                report(status, "failed");
            } else if (parsed.isEmpty()) {
                report(status, "none");
            } else {
                place(title, parsed, status);
            }
        });
    }

    /** Called from the client thread once per game tick while a jump is pending. */
    public void nudge() {
        if (pending == null || client == null || client.getWorldMap() == null) {
            return;
        }
        client.getWorldMap().setWorldMapPositionTarget(pending);
        boolean loaded = client.getWorldMap().getWorldMapRenderer() != null
            && client.getWorldMap().getWorldMapRenderer().isLoaded();
        jumpTicks--;
        if (loaded || jumpTicks <= 0) {
            pending = null;
            jumpTicks = 0;
        }
    }

    private void place(String title, List<MapPins.Pin> parsed, Consumer<String> status) {
        if (clientThread == null) {
            report(status, "pinned");
            return;
        }
        List<MapPins.Pin> copy = new ArrayList<>(parsed);
        clientThread.invokeLater(() -> {
            boolean opened = placeOnClient(title, copy);
            report(status, opened ? "opened" : "pinned");
        });
    }

    private boolean placeOnClient(String title, List<MapPins.Pin> parsed) {
        if (points == null || parsed.isEmpty()) {
            return false;
        }
        WorldPoint first = null;
        for (MapPins.Pin pin : parsed) {
            WorldPoint world = new WorldPoint(pin.x, pin.y, pin.plane);
            if (first == null) {
                first = world;
            }
            WorldMapPoint marker = WorldMapPoint.builder()
                .worldPoint(world)
                .image(icon)
                .tooltip(title)
                .name(title)
                .jumpOnClick(true)
                .build();
            points.add(marker);
            active.add(marker);
        }
        if (first == null) {
            return false;
        }
        pending = first;
        jumpTicks = JUMP_TICKS;
        return openAndJump(first);
    }

    /**
     * Opens the world map by invoking the minimap orb, then centres on the pin.
     * {@code InterfaceID.Orbs.WORLDMAP} is the orb graphic and does not take the
     * click; the button is {@code ORB_WORLDMAP}. This is the same public
     * {@code menuAction} the client uses for that orb. No reflection.
     *
     * @return false when the map is not open and the orb is not on screen, so
     *     the caller can tell the player to open the map (the pending point is
     *     still applied the next time the map is open)
     */
    private boolean openAndJump(WorldPoint world) {
        if (client == null || client.getWorldMap() == null) {
            return false;
        }
        client.getWorldMap().setWorldMapPositionTarget(world);
        Widget map = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
        if (map != null && !map.isHidden()) {
            return true;
        }
        Widget button = client.getWidget(InterfaceID.Orbs.ORB_WORLDMAP);
        if (button == null || button.isHidden()) {
            return false;
        }
        client.menuAction(button.getIndex(), button.getId(), MenuAction.CC_OP, 1, -1, "World Map", "");
        client.getWorldMap().setWorldMapPositionTarget(world);
        return true;
    }

    private void clearOnClient() {
        pending = null;
        jumpTicks = 0;
        if (points == null) {
            active.clear();
            return;
        }
        for (WorldMapPoint marker : active) {
            points.remove(marker);
        }
        active.clear();
    }

    private static void report(Consumer<String> status, String value) {
        if (status == null) {
            return;
        }
        javax.swing.SwingUtilities.invokeLater(() -> status.accept(value));
    }

    private static BufferedImage pinIcon() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(220, 185, 75));
        g.fillOval(2, 2, 12, 12);
        g.setColor(new Color(40, 30, 8));
        g.drawOval(2, 2, 12, 12);
        g.dispose();
        return image;
    }
}
