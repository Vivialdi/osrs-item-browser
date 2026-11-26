package com.osr.jei.map;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import org.junit.Test;

import java.awt.Color;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class MapMarkersTest {

    @Test
    public void mediumPinIsTheOriginalSizeAndUsesTheConfiguredFill() {
        MapMarkers markers = new MapMarkers(null, null, null, null, null);
        assertEquals(16, markers.icon().getWidth());
        assertEquals(16, markers.icon().getHeight());
        assertEquals(MapMarkers.DEFAULT_PIN.getRGB(), markers.icon().getRGB(8, 8));

        WorldMapPoint point = new WorldMapPoint(new WorldPoint(3200, 3200, 0), markers.icon());
        markers.activePins().add(point);
        markers.applyAppearance(new Color(255, 105, 180), PinSize.LARGE);
        assertEquals(24, point.getImage().getWidth());
        assertEquals(24, point.getImage().getHeight());
        assertEquals(new Color(255, 105, 180).getRGB(), point.getImage().getRGB(12, 12));
        assertSame(markers.icon(), point.getImage());

        markers.applyAppearance(new Color(10, 20, 30), PinSize.SMALL);
        assertEquals(12, point.getImage().getWidth());
        assertEquals(new Color(10, 20, 30).getRGB(), point.getImage().getRGB(6, 6));
        assertSame(markers.icon(), point.getImage());
    }

    @Test
    public void outlineStaysDarkWhenTheFillChanges() {
        int edge = MapMarkers.pinIcon(Color.WHITE, 16).getRGB(8, 2);
        Color outline = new Color(edge, true);
        assertEquals(MapMarkers.OUTLINE.getRed(), outline.getRed());
        assertEquals(MapMarkers.OUTLINE.getGreen(), outline.getGreen());
        assertEquals(MapMarkers.OUTLINE.getBlue(), outline.getBlue());
    }
}
