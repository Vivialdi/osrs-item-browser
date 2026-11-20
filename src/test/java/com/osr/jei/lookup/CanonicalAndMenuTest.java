package com.osr.jei.lookup;

import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CanonicalAndMenuTest {

    @Test
    public void notesPlaceholdersAndWornIdsResolveToTheRealItem() {
        assertEquals(2351, CanonicalIds.resolve(2351, CanonicalIds.NONE, CanonicalIds.NONE, CanonicalIds.NONE, CanonicalIds.NONE));

        int notedBar = 2352;
        assertEquals(2351, CanonicalIds.resolve(notedBar, 799, 2351, CanonicalIds.NONE, CanonicalIds.NONE));

        int placeholder = 24505;
        assertEquals(2351, CanonicalIds.resolve(placeholder, CanonicalIds.NONE, CanonicalIds.NONE, 14401, 2351));

        Map<Integer, Integer> worn = new HashMap<>();
        worn.put(11851, 11850);
        assertEquals(11850, CanonicalIds.resolve(11851, CanonicalIds.NONE, CanonicalIds.NONE, CanonicalIds.NONE, CanonicalIds.NONE, worn));

        assertEquals(-1, CanonicalIds.resolve(-1, CanonicalIds.NONE, CanonicalIds.NONE, CanonicalIds.NONE, CanonicalIds.NONE));
        assertEquals(2351, CanonicalIds.resolve(2351, CanonicalIds.NONE, CanonicalIds.NONE, CanonicalIds.NONE, 9999));
    }

    @Test
    public void lookupIsOfferedOnBankWornAndEquipmentSlotsOnly() {
        int examine = MenuAction.CC_OP_LOW_PRIORITY.getId();
        assertTrue(LookupTargets.widgetExamine(examine, "Examine", InterfaceID.INVENTORY));
        assertTrue(LookupTargets.widgetExamine(examine, "Examine", InterfaceID.BANKMAIN));
        assertTrue(LookupTargets.widgetExamine(examine, " Examine ", InterfaceID.BANKSIDE));
        assertTrue(LookupTargets.widgetExamine(examine, "Examine", InterfaceID.WORNITEMS));
        assertTrue(LookupTargets.widgetExamine(examine, "Examine", InterfaceID.EQUIPMENT));
        assertTrue(LookupTargets.widgetExamine(examine, "Examine", InterfaceID.EQUIPMENT_SIDE));

        assertFalse(LookupTargets.widgetExamine(examine, "Withdraw-1", InterfaceID.BANKMAIN));
        assertFalse(LookupTargets.widgetExamine(examine, "Examine", InterfaceID.SHOPMAIN));
        assertFalse(LookupTargets.widgetExamine(examine, "Examine", InterfaceID.SMITHING));
        assertFalse(LookupTargets.widgetExamine(MenuAction.CC_OP.getId(), "Examine", InterfaceID.BANKMAIN));
        assertFalse(LookupTargets.widgetExamine(MenuAction.EXAMINE_NPC.getId(), "Examine", InterfaceID.BANKMAIN));

        assertTrue(LookupTargets.groundExamine(MenuAction.EXAMINE_ITEM_GROUND.getId()));
        assertFalse(LookupTargets.groundExamine(MenuAction.EXAMINE_OBJECT.getId()));
    }

    @Test
    public void anExistingJeiEntryIsNotAddedAgain() {
        assertFalse(LookupTargets.alreadyOffered(Collections.emptyList()));
        assertFalse(LookupTargets.alreadyOffered(Arrays.asList("Withdraw-1", "Examine")));
        assertTrue(LookupTargets.alreadyOffered(Arrays.asList("Examine", "<col=ff9040>JEI</col> Lookup")));
    }
}
