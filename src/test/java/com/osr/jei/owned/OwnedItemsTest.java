package com.osr.jei.owned;

import com.osr.jei.lookup.CanonicalIds;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OwnedItemsTest {

    @Test
    public void notesMergeWithTheUnnotedItemAndPlaceholdersDoNotCount() {
        int noted = CanonicalIds.resolve(2352, 799, 2351, CanonicalIds.NONE, CanonicalIds.NONE);
        int plain = CanonicalIds.resolve(2351, CanonicalIds.NONE, CanonicalIds.NONE, CanonicalIds.NONE, CanonicalIds.NONE);
        Map<Integer, Integer> wornIds = new HashMap<>();
        wornIds.put(11851, 11850);
        int worn = CanonicalIds.resolve(11851, CanonicalIds.NONE, CanonicalIds.NONE, CanonicalIds.NONE, CanonicalIds.NONE, wornIds);

        Map<Integer, Long> inventory = OwnedItems.count(Arrays.asList(
            new OwnedItems.Slot(noted, 10, CanonicalIds.NONE),
            new OwnedItems.Slot(plain, 2, CanonicalIds.NONE),
            new OwnedItems.Slot(2351, 4, 14401),
            new OwnedItems.Slot(2351, 0, CanonicalIds.NONE),
            new OwnedItems.Slot(-1, 5, CanonicalIds.NONE)
        ));
        assertEquals(Long.valueOf(12L), inventory.get(2351));
        assertEquals(2351, noted);
        assertEquals(11850, worn);

        Map<Integer, Long> wornCounts = OwnedItems.count(Arrays.asList(
            new OwnedItems.Slot(worn, 1, CanonicalIds.NONE),
            new OwnedItems.Slot(plain, 1, CanonicalIds.NONE)
        ));
        Map<Integer, Long> bank = new HashMap<>();
        bank.put(2351, 20L);

        OwnedItems.Snapshot bankClosed = OwnedItems.snapshot(inventory, wornCounts, bank, true, false);
        assertFalse(bankClosed.bankKnown());
        assertTrue(bankClosed.inventoryKnown());
        assertEquals(13L, bankClosed.count(2351));
        assertEquals(1L, bankClosed.count(11850));
        assertEquals(0L, bankClosed.count(999));
        assertEquals(18L, OwnedItems.stillNeeded(bankClosed.count(2351), 31));
        assertTrue(OwnedItems.shortfall(bankClosed.count(2351), 31));
        assertFalse(OwnedItems.shortfall(13, 13));
        assertEquals(0L, OwnedItems.stillNeeded(40, 30));
        assertEquals(0L, OwnedItems.stillNeeded(-5, -1));

        assertEquals("have 13+ / need 31", strip(OwnedText.haveNeedHtml(13, 31, false)));
        assertTrue(OwnedText.haveNeedHtml(13, 31, false).contains(OwnedText.UNKNOWN_COLOR));
        assertEquals("still needed 18+", strip(OwnedText.stillNeededHtml(13, 31, false)));
        assertEquals("Owned: 13 (bank not opened)", OwnedText.header(true, false, 13));
        assertEquals("Owned: 0 (bank not opened)", OwnedText.header(true, false, 0));
        assertEquals("Owned: log in to count", OwnedText.header(false, false, 0));

        OwnedItems.Snapshot bankOpen = OwnedItems.snapshot(inventory, wornCounts, bank, true, true);
        assertEquals(33L, bankOpen.count(2351));
        assertEquals("have 2 / need 30", strip(OwnedText.haveNeedHtml(2, 30, true)));
        assertTrue(OwnedText.haveNeedHtml(2, 30, true).contains(OwnedText.SHORT_COLOR));
        assertEquals("still needed 0", strip(OwnedText.stillNeededHtml(33, 30, true)));
        assertTrue(OwnedText.haveNeedHtml(33, 30, true).contains(OwnedText.OK_COLOR));
        assertEquals("Owned: 33", OwnedText.header(true, true, 33));
        assertFalse(bankOpen.equals(bankClosed));

        OwnedItems.Snapshot again = OwnedItems.snapshot(inventory, wornCounts, bank, true, false);
        assertEquals(bankClosed, again);
        assertEquals(OwnedItems.Snapshot.NONE, OwnedItems.snapshot(null, null, null, false, false));
    }

    private static String strip(String html) {
        return html.replaceAll("<[^>]+>", "");
    }
}
