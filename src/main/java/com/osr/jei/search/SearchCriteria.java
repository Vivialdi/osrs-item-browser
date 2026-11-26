package com.osr.jei.search;

import com.osr.jei.model.SearchItem;

/** Sidebar filters. All of them default to allowing every item. */
public final class SearchCriteria {

    public enum Trade {
        ANY("Any trade"),
        TRADEABLE("Tradeable"),
        UNTRADEABLE("Untradeable");

        private final String label;

        Trade(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum Members {
        ANY("Any world"),
        MEMBERS("Members"),
        FREE("Free-to-play");

        private final String label;

        Members(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum Equip {
        ANY("Any item"),
        EQUIPABLE("Equipable"),
        NOT_EQUIPABLE("Not equipable");

        private final String label;

        Equip(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum SlotChoice {
        ANY("Any slot", -1),
        HEAD("Head", 0),
        CAPE("Cape", 1),
        AMULET("Amulet", 2),
        WEAPON("Weapon", 3),
        BODY("Body", 4),
        SHIELD("Shield", 5),
        LEGS("Legs", 7),
        GLOVES("Gloves", 9),
        BOOTS("Boots", 10),
        RING("Ring", 12),
        AMMO("Ammo", 13);

        private final String label;
        private final int containerIndex;

        SlotChoice(String label, int containerIndex) {
            this.label = label;
            this.containerIndex = containerIndex;
        }

        public int containerIndex() {
            return containerIndex;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static final SearchCriteria ANY = new SearchCriteria(Trade.ANY, Members.ANY, Equip.ANY, SlotChoice.ANY);

    public final Trade trade;
    public final Members members;
    public final Equip equip;
    public final SlotChoice slot;
    /** When false, cutscene props from the animation-item list are left out. */
    public final boolean showAnimationItems;
    /** When false, sailing courier crates from the task-carry list are left out. */
    public final boolean showTaskCarryItems;

    public SearchCriteria(Trade trade, Members members, Equip equip, SlotChoice slot) {
        this(trade, members, equip, slot, true, true);
    }

    public SearchCriteria(
        Trade trade,
        Members members,
        Equip equip,
        SlotChoice slot,
        boolean showAnimationItems,
        boolean showTaskCarryItems
    ) {
        this.trade = trade == null ? Trade.ANY : trade;
        this.members = members == null ? Members.ANY : members;
        this.equip = equip == null ? Equip.ANY : equip;
        this.slot = slot == null ? SlotChoice.ANY : slot;
        this.showAnimationItems = showAnimationItems;
        this.showTaskCarryItems = showTaskCarryItems;
    }

    public boolean matches(SearchItem item) {
        if (item == null) {
            return false;
        }
        int flags = item.traits;
        if (trade == Trade.TRADEABLE && !ItemTraits.tradeable(flags)) {
            return false;
        }
        if (trade == Trade.UNTRADEABLE && ItemTraits.tradeable(flags)) {
            return false;
        }
        if (members == Members.MEMBERS && !item.members) {
            return false;
        }
        if (members == Members.FREE && item.members) {
            return false;
        }
        if (equip == Equip.EQUIPABLE && !ItemTraits.equipable(flags)) {
            return false;
        }
        if (equip == Equip.NOT_EQUIPABLE && ItemTraits.equipable(flags)) {
            return false;
        }
        if (slot != SlotChoice.ANY && ItemTraits.slot(flags) != slot.containerIndex()) {
            return false;
        }
        if (!showAnimationItems && AnimationItems.contains(item.id)) {
            return false;
        }
        if (!showTaskCarryItems && TaskCarryItems.contains(item.id)) {
            return false;
        }
        return true;
    }
}
