package com.osr.jei.owned;

import com.osr.jei.lookup.CanonicalIds;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Inventory, worn equipment, and bank totals. Notes are stored under the
 * unnoted id. Placeholders are not owned. The bank map is included only
 * after that container has been seen; an unseen bank is not zero.
 */
public final class OwnedItems {

    /**
     * One container slot after the client has resolved notes and worn variants.
     * {@code placeholderTemplateId != -1} means the slot is a bank placeholder.
     */
    public static final class Slot {
        public final int itemId;
        public final int quantity;
        public final int placeholderTemplateId;

        public Slot(int itemId, int quantity, int placeholderTemplateId) {
            this.itemId = itemId;
            this.quantity = quantity;
            this.placeholderTemplateId = placeholderTemplateId;
        }
    }

    /**
     * Counts the player can show. {@code inventoryKnown} is false at the login
     * screen. {@code bankKnown} is false until the bank container exists.
     */
    public static final class Snapshot {
        public static final Snapshot NONE = new Snapshot(false, false, Collections.emptyMap());

        private final boolean inventoryKnown;
        private final boolean bankKnown;
        private final Map<Integer, Long> counts;

        private Snapshot(boolean inventoryKnown, boolean bankKnown, Map<Integer, Long> counts) {
            this.inventoryKnown = inventoryKnown;
            this.bankKnown = bankKnown;
            this.counts = counts;
        }

        public boolean inventoryKnown() {
            return inventoryKnown;
        }

        public boolean bankKnown() {
            return bankKnown;
        }

        /** Owned amount of a canonical id. Zero when that id was not seen. */
        public long count(int itemId) {
            Long value = counts.get(itemId);
            return value == null ? 0L : value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Snapshot)) {
                return false;
            }
            Snapshot snapshot = (Snapshot) other;
            return inventoryKnown == snapshot.inventoryKnown
                && bankKnown == snapshot.bankKnown
                && counts.equals(snapshot.counts);
        }

        @Override
        public int hashCode() {
            return Objects.hash(inventoryKnown, bankKnown, counts);
        }
    }

    private OwnedItems() {
    }

    /**
     * Sums stacks by id. Non-positive quantities and placeholders are skipped.
     * Callers pass ids that already went through note and worn canonicalization.
     */
    public static Map<Integer, Long> count(Iterable<Slot> slots) {
        Map<Integer, Long> totals = new HashMap<>();
        if (slots == null) {
            return totals;
        }
        for (Slot slot : slots) {
            if (slot == null || slot.itemId < 0 || slot.quantity <= 0) {
                continue;
            }
            if (slot.placeholderTemplateId != CanonicalIds.NONE) {
                continue;
            }
            totals.merge(slot.itemId, (long) slot.quantity, Long::sum);
        }
        return totals;
    }

    /**
     * @param inventoryKnown false when the inventory container does not exist yet
     * @param bankKnown      false when the bank has not been opened this session
     */
    public static Snapshot snapshot(
        Map<Integer, Long> inventory,
        Map<Integer, Long> worn,
        Map<Integer, Long> bank,
        boolean inventoryKnown,
        boolean bankKnown
    ) {
        Map<Integer, Long> totals = new HashMap<>();
        if (inventoryKnown) {
            addAll(totals, inventory);
            addAll(totals, worn);
        }
        if (bankKnown) {
            addAll(totals, bank);
        }
        return new Snapshot(inventoryKnown, bankKnown, Collections.unmodifiableMap(totals));
    }

    /** Items still required. Never negative. */
    public static long stillNeeded(long have, long need) {
        long owned = Math.max(0L, have);
        long required = Math.max(0L, need);
        if (owned >= required) {
            return 0L;
        }
        return required - owned;
    }

    public static boolean shortfall(long have, long need) {
        return stillNeeded(have, need) > 0L;
    }

    private static void addAll(Map<Integer, Long> into, Map<Integer, Long> from) {
        if (from == null) {
            return;
        }
        for (Map.Entry<Integer, Long> entry : from.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0L) {
                continue;
            }
            into.merge(entry.getKey(), entry.getValue(), Long::sum);
        }
    }
}
