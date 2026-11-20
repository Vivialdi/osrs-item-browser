package com.osr.jei.model;

/**
 * A single item in our search index.
 *
 * The {@code members} and {@code haPrice} fields come directly from
 * {@code ItemComposition} in the game cache — no network call is needed.
 * They are captured once during {@code buildItemIndex()} and reused freely.
 */
public class SearchItem {
    public final int     id;
    public final String  name;
    /** Lowercase name computed once while the index is built. */
    public final String  nameLower;
    /** True if this is a members-only item. */
    public final boolean members;
    /** High-alch value in coins (0 = item cannot be alched or has no store price). */
    public final int     haPrice;

    public SearchItem(int id, String name, boolean members, int haPrice) {
        this.id        = id;
        this.name      = name;
        this.nameLower = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        this.members   = members;
        this.haPrice   = haPrice;
    }
}
