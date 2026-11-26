package com.osr.jei.wiki;

import com.osr.jei.model.MonsterInfo;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class LocationNamesTest {

    @Test
    public void floorNumberParamsDropOutOfThePlaceName() {
        assertEquals("Lovakengj (buildings)", LocationNames.clean("Lovakengj (uk=1 buildings)"));
        assertEquals("Falador Mining Guild", LocationNames.clean("Falador Mining Guild (uk=1)"));
        assertEquals(
            "Lovakengj (buildings)",
            LocationNames.clean("[[Lovakengj]] ({{FloorNumber|uk=1}} buildings)"));
        assertEquals(
            "Falador Mining Guild",
            LocationNames.clean("[[Falador]] Mining Guild ({{FloorNumber|uk=1}})"));
    }

    @Test
    public void notesKeepAPlainHyphen() {
        assertEquals(
            "Black Knights' Base (jail - jail key required)",
            LocationNames.clean("Black Knights' Base (jail - [[jail key]] required)"));
        assertEquals(
            "Black Knights' Base (jail - jail key required)",
            LocationNames.clean("Black Knights' Base (jail \u2013 jail key required)"));
        assertFalse(LocationNames.clean("Black Knights' Base (jail \u2014 key)").contains("\u2014"));
        assertEquals(
            "Black Knights' Base (jail - key)",
            LocationNames.clean("Black Knights' Base (jail \u2014 key)"));
    }

    @Test
    public void monsterLocLinesUseTheCleanedNames() {
        String page = ""
            + "{{Infobox Monster|combat=10}}\n"
            + "{{LocLine|location=[[Lovakengj]] ({{FloorNumber|uk=1}} buildings)|x:1,y:2}}\n"
            + "{{LocLine|location=[[Falador]] Mining Guild ({{FloorNumber|uk=1}})|x:3,y:4}}\n"
            + "{{LocLine|location=[[Black Knights' Base]] (jail - [[jail key]] required)|x:5,y:6}}\n";
        MonsterInfo info = MonsterParser.parse("Dwarf", page).get();
        List<String> places = info.locations;
        assertEquals(Arrays.asList(
            "Lovakengj (buildings)",
            "Falador Mining Guild",
            "Black Knights' Base (jail - jail key required)"
        ), places);
    }
}
