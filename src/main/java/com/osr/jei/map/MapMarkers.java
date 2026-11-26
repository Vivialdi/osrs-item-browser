package com.osr.jei.map;

import com.osr.jei.JEIConfig;
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
    static final Color DEFAULT_PIN = new Color(255, 105, 180);
    static final Color OUTLINE = new Color(40, 30, 8);

    private final List<WorldMapPoint> active = new ArrayList<>();
    private final MapPinWatch mapWatch = new MapPinWatch();
    private Color pinColor = DEFAULT_PIN;
    private int pinEdge = PinSize.MEDIUM.pixels();
    private BufferedImage icon;
    private final java.util.concurrent.atomic.AtomicInteger requestId = new java.util.concurrent.atomic.AtomicInteger();
    private WorldPoint pending;

    @Inject
    public MapMarkers(Client client, ClientThread clientThread, WorldMapPointManager points, WikiPages pages, JEIConfig config) {
        this.client = client;
        this.clientThread = clientThread;
        this.points = points;
        this.pages = pages;
        Color chosen = config == null ? null : config.mapPinColor();
        PinSize size = config == null ? null : config.pinSize();
        this.pinColor = chosen == null ? DEFAULT_PIN : chosen;
        this.pinEdge = size == null ? PinSize.MEDIUM.pixels() : size.pixels();
        this.icon = pinIcon(pinColor, pinEdge);
    }

    /**
     * Rebuilds the cached pin image from the configured color and size, then
     * points every live marker at it. The image is drawn here. Swapping it
     * onto the markers runs on the client thread and does not wait.
     */
    public void applyAppearance(Color color, PinSize size) {
        pinColor = color == null ? DEFAULT_PIN : color;
        pinEdge = size == null ? PinSize.MEDIUM.pixels() : size.pixels();
        BufferedImage next = pinIcon(pinColor, pinEdge);
        if (clientThread == null) {
            applyIcon(next);
            return;
        }
        clientThread.invokeLater(() -> applyIcon(next));
    }

    private void applyIcon(BufferedImage next) {
        icon = next;
        for (WorldMapPoint marker : active) {
            if (marker != null) {
                marker.setImage(next);
            }
        }
    }

    /** Drops every pin this plugin added. Safe from the EDT or the client thread. */
    public void clear() {
        requestId.incrementAndGet();
        if (clientThread == null) {
            return;
        }
        clientThread.invokeLater(this::clearOnClient);
    }

    /**
     * True after a cached read found no coordinates, so the row can omit the
     * link instead of offering it again. Unknown pages return false.
     */
    public boolean knownWithoutPins(String title) {
        if (title == null || title.trim().isEmpty()) {
            return false;
        }
        String key = title.trim().toLowerCase(java.util.Locale.ROOT);
        List<MapPins.Pin> cached = pins.get(key, System.currentTimeMillis());
        return cached != null && cached.isEmpty();
    }

    /**
     * Loads coordinates for one page title and replaces the previous pins.
     * {@code status} runs on the EDT with "pinned", "none", or "failed".
     * Pins never open the map.
     */
    public void show(String title, Consumer<String> status) {
        if (title == null || title.trim().isEmpty() || pages == null) {
            report(status, "none");
            return;
        }
        final int id = requestId.incrementAndGet();
        String key = title.trim().toLowerCase(java.util.Locale.ROOT);
        List<MapPins.Pin> cached = pins.get(key, System.currentTimeMillis());
        if (cached != null) {
            finish(title, cached, null, status, id);
            return;
        }
        pages.loadWikitext(title).whenComplete((page, error) -> {
            List<MapPins.Pin> parsed = error == null && page != null && !page.missing
                ? MapPins.parse(page.wikitext)
                : java.util.Collections.emptyList();
            if (error == null && page != null) {
                pins.put(key, parsed, System.currentTimeMillis());
            }
            finish(title, parsed, error, status, id);
        });
    }

    private void finish(String title, List<MapPins.Pin> parsed, Throwable error, Consumer<String> status, int id) {
        boolean current = id == requestId.get();
        if (error != null) {
            if (current) {
                dropPins(id);
            }
            report(status, "failed");
            return;
        }
        if (parsed == null || parsed.isEmpty()) {
            if (current) {
                dropPins(id);
            }
            report(status, "none");
            return;
        }
        if (!current) {
            return;
        }
        place(title, parsed, status, id);
    }

    /**
     * Called from the client thread once per game tick. When the world map
     * renderer is loaded, centres it on the pending pin. Does not open the map.
     */
    public void nudge() {
        boolean open = mapLoaded();
        if (mapWatch.onTick(open, !active.isEmpty())) {
            restoreOnClient();
        }
        if (pending == null || !open) {
            return;
        }
        client.getWorldMap().setWorldMapPositionTarget(pending);
        pending = null;
    }

    /**
     * The world map renderer is unloaded while the map is closed, and the next
     * open starts at the player. Points this plugin still holds are added again
     * and the map is aimed at the first one. Runs on the client thread.
     */
    private void restoreOnClient() {
        if (points == null || active.isEmpty()) {
            return;
        }
        List<WorldMapPoint> held = new ArrayList<>(active);
        for (WorldMapPoint marker : held) {
            points.removeIf(candidate -> candidate == marker);
            if (marker != null) {
                marker.setImage(icon);
                points.add(marker);
            }
        }
        WorldPoint first = held.get(0) == null ? null : held.get(0).getWorldPoint();
        if (first != null) {
            aim(first);
        }
    }

    private void dropPins(int id) {
        if (clientThread == null) {
            return;
        }
        clientThread.invokeLater(() -> {
            if (id == requestId.get()) {
                clearOnClient();
            }
        });
    }

    private void place(String title, List<MapPins.Pin> parsed, Consumer<String> status, int id) {
        if (clientThread == null) {
            report(status, "pinned");
            return;
        }
        List<MapPins.Pin> copy = new ArrayList<>(parsed);
        clientThread.invokeLater(() -> {
            if (id != requestId.get()) {
                return;
            }
            clearOnClient();
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

    static BufferedImage pinIcon(Color fill, int size) {
        int edge = size < 8 ? 8 : size;
        int inset = Math.max(1, edge / 8);
        int diameter = Math.max(4, edge - inset * 2);
        Color color = fill == null ? DEFAULT_PIN : fill;
        BufferedImage image = new BufferedImage(edge, edge, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(color);
        g.fillOval(inset, inset, diameter, diameter);
        g.setColor(OUTLINE);
        g.drawOval(inset, inset, diameter, diameter);
        g.dispose();
        return image;
    }

    BufferedImage icon() {
        return icon;
    }

    List<WorldMapPoint> activePins() {
        return active;
    }
}
