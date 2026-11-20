package com.osr.jei.service;

import com.osr.jei.model.ShopEntry;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Shop rows from the shared item-page parse. No separate section requests. */
@Singleton
public class ShopService {

    private final ItemDetailsService items;

    @Inject
    public ShopService(ItemDetailsService items) {
        this.items = items;
    }

    public CompletableFuture<List<ShopEntry>> getShops(String itemName) {
        return items.load(itemName).thenApply(details -> details.shops);
    }
}
