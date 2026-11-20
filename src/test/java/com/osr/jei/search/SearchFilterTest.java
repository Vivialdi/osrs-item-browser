package com.osr.jei.search;

import com.osr.jei.model.SearchItem;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SearchFilterTest {

    @Test
    public void packsSlotZeroSeparatelyFromMissing() {
        int head = ItemTraits.pack(true, true, true, 0);
        assertTrue(ItemTraits.members(head));
        assertTrue(ItemTraits.tradeable(head));
        assertTrue(ItemTraits.equipable(head));
        assertEquals(0, ItemTraits.slot(head));

        int plain = ItemTraits.pack(false, false, false, -1);
        assertFalse(ItemTraits.equipable(plain));
        assertEquals(-1, ItemTraits.slot(plain));
        assertEquals(3, ItemTraits.slot(ItemTraits.pack(false, true, true, 3)));
    }

    @Test
    public void filtersRunBeforeTheResultLimit() {
        SearchItem whip = item(4151, "Abyssal whip", true, flags(true, true, 3));
        SearchItem coins = item(995, "Coins", false, flags(true, false, -1));
        SearchItem cape = item(6570, "Fire cape", false, flags(false, true, 1));
        SearchItem bow = item(9185, "Rune crossbow", true, flags(true, true, 3));
        SearchItem plate = item(1117, "Steel platebody", false, flags(true, true, 4));
        List<SearchItem> all = Arrays.asList(whip, coins, cape, bow, plate);

        SearchCriteria weapons = new SearchCriteria(
            SearchCriteria.Trade.ANY,
            SearchCriteria.Members.ANY,
            SearchCriteria.Equip.EQUIPABLE,
            SearchCriteria.SlotChoice.WEAPON
        );
        List<SearchItem> found = ItemSearch.rank(all, "ss", 10, weapons);
        assertEquals(2, found.size());
        assertEquals("Abyssal whip", found.get(0).name);
        assertEquals("Rune crossbow", found.get(1).name);

        SearchCriteria freeTradeable = new SearchCriteria(
            SearchCriteria.Trade.TRADEABLE,
            SearchCriteria.Members.FREE,
            SearchCriteria.Equip.ANY,
            SearchCriteria.SlotChoice.ANY
        );
        List<SearchItem> free = ItemSearch.rank(all, "co", 10, freeTradeable);
        assertEquals(1, free.size());
        assertEquals("Coins", free.get(0).name);

        SearchCriteria untradeable = new SearchCriteria(
            SearchCriteria.Trade.UNTRADEABLE,
            SearchCriteria.Members.ANY,
            SearchCriteria.Equip.ANY,
            SearchCriteria.SlotChoice.ANY
        );
        List<SearchItem> locked = ItemSearch.rank(all, "fi", 10, untradeable);
        assertEquals(1, locked.size());
        assertEquals("Fire cape", locked.get(0).name);

        assertEquals("Abyssal whip", ItemSearch.rank(all, "ab", 10, SearchCriteria.ANY).get(0).name);
    }

    private static int flags(boolean tradeable, boolean equipable, int slot) {
        return ItemTraits.pack(false, tradeable, equipable, slot);
    }

    private static SearchItem item(int id, String name, boolean members, int traits) {
        int packed = members ? traits | ItemTraits.MEMBERS : traits & ~ItemTraits.MEMBERS;
        return new SearchItem(id, name, members, 0, packed);
    }
}