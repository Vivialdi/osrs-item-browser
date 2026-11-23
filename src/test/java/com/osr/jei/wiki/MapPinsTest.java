package com.osr.jei.wiki;

import com.osr.jei.model.MonsterInfo;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
    public void locLinePairsKeepEverySpawnAndItsPlane() {
        String page = ""
            + "{{LocLine|name=Wizard|location=[[Wizards' Tower]] basement|plane=0|x:3109,y:9558}}\n"
            + "{{LocLine|location=[[Wizards' Tower]]|plane=0|x:3107,y:3158|x:3110,y:3158|x:3111,y:3166}}\n"
            + "{{LocLine|location=[[Wizards' Guild]]|plane=1|x:3107,y:3157|x:3112,y:3157}}\n"
            + "{{LocLine|location=[[Wizards' Guild]]|plane=2|x:2586,y:3088|x:2586,y:3088}}\n";
        List<MapPins.Pin> pins = MapPins.parse(page);
        assertEquals(7, pins.size());
        assertEquals(3109, pins.get(0).x);
        assertEquals(9558, pins.get(0).y);
        assertEquals(0, pins.get(0).plane);
        assertEquals(1, pins.get(4).plane);
        assertEquals(2, pins.get(6).plane);
    }

    @Test
    public void monsterWithoutCoordinatesDoesNotOfferAMap() {
        String bare = "{{Infobox Monster|combat=9}}\n{{LocLine|location=[[Wizards' Tower]]|levels=9}}";
        MonsterInfo info = MonsterParser.parse("Wizard", bare).get();
        assertFalse(info.mapped);
        assertEquals("Wizards' Tower", info.locations.get(0));
        String placed = bare + "\n{{LocLine|location=[[Wizards' Tower]] basement|plane=0|x:3109,y:9558}}";
        assertTrue(MonsterParser.parse("Wizard", placed).get().mapped);
    }

    @Test
    public void iconColumnsFillTheRow() {
        assertEquals(1, com.osr.jei.ui.IconGridLayout.columns(40, 32, 4));
        assertEquals(6, com.osr.jei.ui.IconGridLayout.columns(225, 32, 4));
        assertEquals(9, com.osr.jei.ui.IconGridLayout.columns(350, 32, 4));
    }
}
