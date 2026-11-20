package com.osr.jei.lookup;

import java.util.Map;

/**
 * Resolves a noted, placeholder, or worn item id to the item players search for.
 *
 * <p>The rules match {@code ItemManager.canonicalize}: a note
 * ({@code ItemComposition.getNote() != -1}) becomes {@code getLinkedNoteId()};
 * a placeholder ({@code getPlaceholderTemplateId() != -1}) becomes
 * {@code getPlaceholderId()}; a worn variant can then map to its inventory id.
 * The live lookup still calls {@code itemManager.canonicalize} on the client
 * thread so the worn-item map stays the client's.
 */
public final class CanonicalIds {

    /** Sentinel used by item compositions when a link is absent. */
    public static final int NONE = -1;

    private CanonicalIds() {
    }

    public static int resolve(int itemId, int note, int linkedNoteId, int placeholderTemplateId, int placeholderId) {
        return resolve(itemId, note, linkedNoteId, placeholderTemplateId, placeholderId, null);
    }

    public static int resolve(
        int itemId,
        int note,
        int linkedNoteId,
        int placeholderTemplateId,
        int placeholderId,
        Map<Integer, Integer> wornToInventory
    ) {
        if (itemId < 0) {
            return NONE;
        }
        int resolved = itemId;
        if (note != NONE) {
            resolved = linkedNoteId;
        } else if (placeholderTemplateId != NONE) {
            resolved = placeholderId;
        }
        if (resolved < 0) {
            return itemId;
        }
        if (wornToInventory != null) {
            Integer worn = wornToInventory.get(resolved);
            if (worn != null && worn > 0) {
                return worn;
            }
        }
        return resolved;
    }
}
