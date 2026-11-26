package com.osr.jei.ui;

import com.osr.jei.model.ShopEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shop rows for the item currently open. Each row is that item's stock in
 * that shop. A zero-stock row is omitted. The same shop still appears on
 * another item when that item's stock there is above zero or unlimited.
 */
public final class ShopSources {

    private ShopSources() {
    }

    public static List<ShopEntry> visible(List<ShopEntry> shops) {
        if (shops == null || shops.isEmpty()) {
            return Collections.emptyList();
        }
        List<ShopEntry> shown = new ArrayList<>();
        for (ShopEntry shop : shops) {
            if (shop == null || !shop.sells || shop.stock == 0) {
                continue;
            }
            shown.add(shop);
        }
        return shown;
    }
}
