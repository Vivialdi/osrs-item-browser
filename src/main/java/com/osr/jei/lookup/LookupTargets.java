package com.osr.jei.lookup;

import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;

/**
 * Right-click JEI Lookup is offered on ground items and on item examines in
 * the inventory, the bank (including the side inventory and placeholders),
 * the worn-equipment panel, and the equipment-stats interface.
 * Other examines are left alone.
 */
public final class LookupTargets {

    private LookupTargets() {
    }

    public static boolean isItemInterface(int interfaceGroup) {
        return interfaceGroup == InterfaceID.INVENTORY
            || interfaceGroup == InterfaceID.BANKMAIN
            || interfaceGroup == InterfaceID.BANKSIDE
            || interfaceGroup == InterfaceID.WORNITEMS
            || interfaceGroup == InterfaceID.EQUIPMENT
            || interfaceGroup == InterfaceID.EQUIPMENT_SIDE;
    }

    public static boolean groundExamine(int actionId) {
        return actionId == MenuAction.EXAMINE_ITEM_GROUND.getId();
    }

    public static boolean widgetExamine(int actionId, String option, int interfaceGroup) {
        if (actionId != MenuAction.CC_OP_LOW_PRIORITY.getId()) {
            return false;
        }
        if (option == null || !"Examine".equals(option.trim())) {
            return false;
        }
        return isItemInterface(interfaceGroup);
    }

    /** True when this menu already has a JEI Lookup row. */
    public static boolean alreadyOffered(Iterable<String> options) {
        if (options == null) {
            return false;
        }
        for (String option : options) {
            if (option != null && option.contains("JEI")) {
                return true;
            }
        }
        return false;
    }
}
