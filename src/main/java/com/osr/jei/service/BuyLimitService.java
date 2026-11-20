package com.osr.jei.service;

import com.osr.jei.pricing.BuyLimitSnapshot;
import com.osr.jei.wiki.RequestCancelledException;
import com.osr.jei.wiki.WikiHttp;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * One {@code /mapping} download for GE buy limits. It is started only when a
 * caller asks, kept for {@link BuyLimitSnapshot#TTL_MS}, and not requested
 * again per item. A failed attempt waits {@link #FAILURE_BACKOFF_MS} and
 * does not replace a previous good snapshot.
 */
@Slf4j
@Singleton
public class BuyLimitService {

    static final String MAPPING = "https://prices.runescape.wiki/api/v1/osrs/mapping";
    static final long FAILURE_BACKOFF_MS = 8_000L;

    private final WikiHttp wikiHttp;
    private final Object lock = new Object();
    private volatile BuyLimitSnapshot snapshot;
    private volatile long nextAttemptAt;
    private CompletableFuture<BuyLimitSnapshot> inflight;

    @Inject
    public BuyLimitService(WikiHttp wikiHttp) {
        this.wikiHttp = wikiHttp;
    }

    public CompletableFuture<BuyLimitSnapshot> load() {
        long now = System.currentTimeMillis();
        BuyLimitSnapshot current = snapshot;
        if (current != null && current.fresh(now)) {
            return CompletableFuture.completedFuture(current);
        }
        if (now < nextAttemptAt) {
            return CompletableFuture.completedFuture(current == null ? BuyLimitSnapshot.empty() : current);
        }
        synchronized (lock) {
            current = snapshot;
            if (current != null && current.fresh(System.currentTimeMillis())) {
                return CompletableFuture.completedFuture(current);
            }
            if (inflight != null) {
                return inflight;
            }
            CompletableFuture<BuyLimitSnapshot> mine = new CompletableFuture<>();
            inflight = mine;
            wikiHttp.getStableElement(MAPPING).whenComplete((root, error) -> {
                synchronized (lock) {
                    if (inflight == mine) {
                        inflight = null;
                    }
                }
                if (error != null) {
                    Throwable cause = error.getCause() == null ? error : error.getCause();
                    if (!(cause instanceof RequestCancelledException)) {
                        nextAttemptAt = System.currentTimeMillis() + FAILURE_BACKOFF_MS;
                        log.debug("Could not refresh GE buy limits: {}", cause.getMessage());
                    }
                    mine.completeExceptionally(cause);
                    return;
                }
                if (root == null || !root.isJsonArray()) {
                    nextAttemptAt = System.currentTimeMillis() + FAILURE_BACKOFF_MS;
                    mine.completeExceptionally(new IOException("Unexpected mapping JSON"));
                    return;
                }
                BuyLimitSnapshot fresh = BuyLimitSnapshot.fromMapping(root, System.currentTimeMillis());
                snapshot = fresh;
                nextAttemptAt = 0L;
                mine.complete(fresh);
            });
            return mine;
        }
    }
}
