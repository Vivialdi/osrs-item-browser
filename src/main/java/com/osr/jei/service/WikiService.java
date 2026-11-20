package com.osr.jei.service;

import com.osr.jei.model.DropEntry;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Drop sources from the shared item-page parse. */
@Singleton
public class WikiService {

    private final ItemDetailsService items;

    @Inject
    public WikiService(ItemDetailsService items) {
        this.items = items;
    }

    public CompletableFuture<List<DropEntry>> getDropSources(String itemName) {
        return items.load(itemName).thenApply(details -> details.drops);
    }
}
