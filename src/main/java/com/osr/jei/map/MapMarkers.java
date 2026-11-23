package com.osr.jei.map;

import com.osr.jei.wiki.MapPins;
import com.osr.jei.wiki.TtlCache;
import com.osr.jei.wiki.WikiPages;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
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

    private final Client client;
    private final ClientThread clientThread;
    private final WorldMapPointManager points;
    private final WikiPages pages;
    private final TtlCache<String, List<MapPins.Pin>> pins = new TtlCache<>(32, WikiPages.TTL_MS);
    private final List<WorldMapPoint> active = new ArrayList<>();
    private final BufferedImage icon = pinIcon();
    private WorldPoint pending;

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
     * on the EDT with "pinned", "none", or "failed". Pins never open the map.
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

    /**
     * Called from the client thread once per game tick. When the world map
     * renderer is loaded, centres it on the pending pin. Does not open the map.
     */
    public void nudge() {
        if (pending == null || !mapLoaded()) {
            return;
        }
        client.getWorldMap().setWorldMapPositionTarget(pending);
        pending = null;
    }

    private void place(String title, List<MapPins.Pin> parsed, Consumer<String> status) {
        if (clientThread == null) {
            report(status, "pinned");
            return;
        }
        List<MapPins.Pin> copy = new ArrayList<>(parsed);
        clientThread.invokeLater(() -> {
            placeOnClient(title, copy);
            report(status, "pinned");
        });
    }

    private void placeOnClient(String title, List<MapPins.Pin> parsed) {
        if (points == null || parsed.isEmpty()) {
            return;
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
        if (first != null) {
            aim(first);
        }
    }

    /** Centres now if the map is already loaded. Otherwise waits for the next open. */
    private void aim(WorldPoint world) {
        pending = world;
        if (mapLoaded()) {
            client.getWorldMap().setWorldMapPositionTarget(pending);
            pending = null;
        }
    }

    private boolean mapLoaded() {
        return client != null
            && client.getWorldMap() != null
            && client.getWorldMap().getWorldMapRenderer() != null
            && client.getWorldMap().getWorldMapRenderer().isLoaded();
    }

    private void clearOnClient() {
        pending = null;
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
