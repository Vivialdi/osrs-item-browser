package com.osr.jei.crafting;

import com.osr.jei.service.ItemDetails;

/**
 * Recipe pages for a build path. {@link #cached} must not hit the network.
 * {@link #load} is the coalesced fetch and is only used while expanding.
 */
public interface RecipePages {

    ItemDetails cached(String itemName);

    ItemDetails load(String itemName);

    /** Item id when the search index knows this name, otherwise null. */
    default Integer idFor(String itemName) {
        return null;
    }
}
