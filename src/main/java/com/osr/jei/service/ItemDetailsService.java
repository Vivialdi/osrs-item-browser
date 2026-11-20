package com.osr.jei.service;

import com.osr.jei.wiki.BoundedCache;
import com.osr.jei.wiki.WikiPages;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Loads an item page once and reuses the parsed result.
 * Confirmed missing pages stay cached. Transport errors do not.
 */
@Slf4j
@Singleton
public class ItemDetailsService {

    static final int CACHE_SIZE = 96;

    private final WikiPages pages;
    private final BoundedCache<String, ItemDetails> cache = new BoundedCache<>(CACHE_SIZE);

    @Inject
    public ItemDetailsService(WikiPages pages) {
        this.pages = pages;
    }

    public CompletableFuture<ItemDetails> load(String itemName) {
        if (itemName == null || itemName.isEmpty()) {
            return CompletableFuture.completedFuture(ItemDetails.EMPTY);
        }
        String key = itemName.toLowerCase(Locale.ROOT);
        ItemDetails cached = cache.get(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return pages.load(itemName).handle((page, error) -> {
            if (error != null) {
                Throwable cause = error.getCause() == null ? error : error.getCause();
                if (cause instanceof com.osr.jei.wiki.RequestCancelledException) {
                    throw new java.util.concurrent.CompletionException(cause);
                }
                log.debug("[JEI] Item page failed for '{}': {}", itemName, cause.getMessage());
                throw new java.util.concurrent.CompletionException(cause);
            }
            ItemDetails details = ItemDetails.from(page);
            cache.put(key, details);
            return details;
        });
    }
}
