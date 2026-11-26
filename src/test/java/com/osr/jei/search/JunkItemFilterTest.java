package com.osr.jei.search;

import com.osr.jei.model.SearchItem;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JunkItemFilterTest {

    private static final int CHAIR = 6122;
    private static final int BEER_GLASS = 6123;
    private static final int YAK_CRATE = 32559;

    /** Cosmetics and holiday gear that must stay searchable. */
    private static final int[] KEPT = {
        1050, // Santa hat
        1037, // Bunny ears
        1038, // Red partyhat
        1040, // Yellow partyhat
        1042, // Blue partyhat
        1044, // Green partyhat
        1046, // Purple partyhat
        1048, // White partyhat
        11862, // Black partyhat
        11863, // Rainbow partyhat
        9925, // Skeleton mask
        9924, // Skeleton shirt
        9923, // Skeleton leggings
        3057, // Mime mask
        3058, // Mime top
        3059, // Mime legs
        11850, // Graceful hood
        11854, // Graceful top
        12888, // Santa jacket
        13663 // Bunny top
    };

    @Test
    public void listsHoldTheExampleItemsAndNotCosmetics() {
        assertTrue(AnimationItems.contains(CHAIR));
        assertTrue(AnimationItems.contains(BEER_GLASS));
        assertEquals(61, AnimationItems.size());
        assertTrue(TaskCarryItems.contains(YAK_CRATE));
        assertEquals(374, TaskCarryItems.size());
        assertFalse(AnimationItems.contains(YAK_CRATE));
        assertFalse(TaskCarryItems.contains(CHAIR));
        for (int id : KEPT) {
            assertFalse("animation list has " + id, AnimationItems.contains(id));
            assertFalse("task carry list has " + id, TaskCarryItems.contains(id));
        }
    }

    @Test
    public void searchHidesBothListsUntilTheSettingsAreOn() {
        SearchItem chair = item(CHAIR, "A chair");
        SearchItem glass = item(BEER_GLASS, "Beer glass");
        SearchItem crate = item(YAK_CRATE, "Crate of yak hair");
        SearchItem whip = item(4151, "Abyssal whip");
        SearchItem santa = item(1050, "Santa hat");
        List<SearchItem> all = Arrays.asList(chair, glass, crate, whip, santa);

        SearchCriteria hidden = criteria(false, false);
        assertTrue(ItemSearch.rank(all, "chair", 10, hidden).isEmpty());
        assertTrue(ItemSearch.rank(all, "glass", 10, hidden).isEmpty());
        assertTrue(ItemSearch.rank(all, "yak", 10, hidden).isEmpty());
        assertEquals("Abyssal whip", ItemSearch.rank(all, "whip", 10, hidden).get(0).name);
        assertEquals("Santa hat", ItemSearch.rank(all, "santa", 10, hidden).get(0).name);

        SearchCriteria equip = new SearchCriteria(
            SearchCriteria.Trade.ANY,
            SearchCriteria.Members.ANY,
            SearchCriteria.Equip.EQUIPABLE,
            SearchCriteria.SlotChoice.ANY,
            false,
            false
        );
        assertTrue(ItemSearch.rank(all, "crate", 10, equip).isEmpty());
        assertEquals("Abyssal whip", ItemSearch.rank(all, "whip", 10, equip).get(0).name);

        SearchCriteria shown = criteria(true, true);
        assertEquals("A chair", ItemSearch.rank(all, "chair", 10, shown).get(0).name);
        assertEquals("Beer glass", ItemSearch.rank(all, "glass", 10, shown).get(0).name);
        assertEquals("Crate of yak hair", ItemSearch.rank(all, "yak", 10, shown).get(0).name);
    }

    private static SearchCriteria criteria(boolean animation, boolean taskCarry) {
        return new SearchCriteria(
            SearchCriteria.Trade.ANY,
            SearchCriteria.Members.ANY,
            SearchCriteria.Equip.ANY,
            SearchCriteria.SlotChoice.ANY,
            animation,
            taskCarry
        );
    }

    private static SearchItem item(int id, String name) {
        return new SearchItem(id, name, true, 0, ItemTraits.pack(true, false, true, 3));
    }
}
