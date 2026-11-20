package com.osr.jei.wiki;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MapPinsTest {

    @Test
    public void mapTemplateAndInfoboxCoordinatesAreCappedAndDeduped() {
        String page = ""
            + "{{Infobox Shop\n|name = Aubury's Rune Shop\n|mapx = 3253\n|mapy = 3401\n}}\n"
            + "{{Map|x=3253|y=3401|plane=0|mtype=square}}\n"
            + "{{Map|3222,3218|plane=1}}\n"
            + "{{Map|x=10|y=10|plane=0}}\n";
        List<MapPins.Pin> pins = MapPins.parse(page);
        assertEquals(2, pins.size());
        assertEquals(3253, pins.get(0).x);
        assertEquals(3401, pins.get(0).y);
        assertEquals(0, pins.get(0).plane);
        assertEquals(3222, pins.get(1).x);
        assertEquals(1, pins.get(1).plane);
        assertTrue(pins.size() <= MapPins.MAX);
    }

    @Test
    public void iconColumnsFillTheRow() {
        assertEquals(1, com.osr.jei.ui.IconGridLayout.columns(40, 32, 4));
        assertEquals(6, com.osr.jei.ui.IconGridLayout.columns(225, 32, 4));
        assertEquals(9, com.osr.jei.ui.IconGridLayout.columns(350, 32, 4));
    }
}
