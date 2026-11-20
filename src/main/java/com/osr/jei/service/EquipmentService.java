package com.osr.jei.service;

import com.osr.jei.model.EquipmentStats;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Equipment stats and weight from the shared item-page parse. */
@Singleton
public class EquipmentService {

    private final ItemDetailsService items;

    @Inject
    public EquipmentService(ItemDetailsService items) {
        this.items = items;
    }

    public CompletableFuture<Optional<EquipmentStats>> getStats(String itemName) {
        return items.load(itemName).thenApply(details -> details.stats);
    }

    public CompletableFuture<Double> getWeight(String itemName) {
        return items.load(itemName).thenApply(details -> details.weight);
    }
}
