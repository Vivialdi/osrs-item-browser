package com.osr.jei.search;

import java.util.Arrays;

/**
 * Item ids from the OSRS Wiki category "Items used in animations".
 * There is no category named "Animation items"; that category is the one
 * that lists cutscene props such as A chair and Beer glass (Forgettable Tale...).
 * Generated once on 2026-11-26 from MediaWiki categorymembers plus each page's
 * infobox {@code id} fields. Not loaded from the wiki at runtime.
 */
public final class AnimationItems {

    private static final int[] IDS = {
        796, 797, 798, 945, 3177, 3893, 4181, 4498, 4500, 4501, 5060, 5061,
        5614, 5732, 6122, 6123, 6864, 7004, 7118, 7121, 7414, 7682, 7684, 7686,
        7756, 7758, 8857, 8986, 8987, 9415, 9590, 9650, 9665, 9702, 10485, 10486,
        10509, 10566, 10840, 10841, 10860, 10861, 10886, 21186, 21253, 21254, 21284, 21297,
        22812, 22814, 25710, 25711, 25938, 25941, 25944, 25947, 25950, 25953, 27546, 27548,
        30320
    };

    private AnimationItems() {
    }

    public static boolean contains(int id) {
        return Arrays.binarySearch(IDS, id) >= 0;
    }

    public static int size() {
        return IDS.length;
    }
}
