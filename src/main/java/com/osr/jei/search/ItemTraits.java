package com.osr.jei.search;

/**
 * Compact flags stored on each index entry.
 * Bit 0 members, bit 1 tradeable, bit 2 equipable, bit 3 slot present,
 * bits 4-7 the worn-container slot index (0 is a real slot, so presence is separate).
 */
public final class ItemTraits {

    public static final int MEMBERS = 1;
    public static final int TRADEABLE = 1 << 1;
    public static final int EQUIPABLE = 1 << 2;
    public static final int HAS_SLOT = 1 << 3;
    public static final int SLOT_SHIFT = 4;
    public static final int SLOT_MASK = 0xF;

    private ItemTraits() {
    }

    public static int pack(boolean members, boolean tradeable, boolean equipable, int slotIndex) {
        int flags = 0;
        if (members) {
            flags |= MEMBERS;
        }
        if (tradeable) {
            flags |= TRADEABLE;
        }
        if (equipable) {
            flags |= EQUIPABLE;
        }
        if (slotIndex >= 0 && slotIndex <= SLOT_MASK) {
            flags |= HAS_SLOT;
            flags |= (slotIndex & SLOT_MASK) << SLOT_SHIFT;
        }
        return flags;
    }

    public static boolean members(int flags) {
        return (flags & MEMBERS) != 0;
    }

    public static boolean tradeable(int flags) {
        return (flags & TRADEABLE) != 0;
    }

    public static boolean equipable(int flags) {
        return (flags & EQUIPABLE) != 0;
    }

    /** Worn-container index, or -1 when the item has no gear slot. */
    public static int slot(int flags) {
        if ((flags & HAS_SLOT) == 0) {
            return -1;
        }
        return (flags >> SLOT_SHIFT) & SLOT_MASK;
    }
}
