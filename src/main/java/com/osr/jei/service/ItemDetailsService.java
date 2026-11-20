package com.osr.jei.service;

import com.osr.jei.wiki.TtlCache;
import com.osr.jei.wiki.WikiPages;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Loads an item page once and reuses the parsed result for {@link WikiPages#TTL_MS}.
 * Confirmed missing pages stay cached. Transport errors do not.
 */
@Slf4j
@Singleton
public class ItemDetailsService {

    static final int CACHE_SIZE = 96;

    private final WikiPages pages;
    private final TtlCache<String, ItemDetails> cache = new TtlCache<>(CACHE_SIZE, WikiPages.TTL_MS);

    @Inject
    public ItemDetailsService(WikiPages pages) {
        this.pages = pages;
    }

    /** Cached parse, or null when this name has not been loaded. Does not fetch. */
    public ItemDetails peek(String itemName) {
        if (itemName == null || itemName.isEmpty()) {
            return null;
        }
        return cache.get(itemName.toLowerCase(Locale.ROOT), System.currentTimeMillis());
    }

    /** Drops a parsed page and its failure holdoff so the next load can fetch it. */
    public void retry(String itemName) {
        if (itemName == null || itemName.isEmpty()) {
            return;
        }
        cache.remove(itemName.toLowerCase(Locale.ROOT));
        pages.allowRetry(itemName);
    }

    public CompletableFuture<ItemDetails> load(String itemName) {
        if (itemName == null || itemName.isEmpty()) {
            return CompletableFuture.completedFuture(ItemDetails.EMPTY);
        }
        String key = itemName.toLowerCase(Locale.ROOT);
        ItemDetails cached = cache.get(key, System.currentTimeMillis());
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
            cache.put(key, details, System.currentTimeMillis());
            return details;
        });
    }
}
