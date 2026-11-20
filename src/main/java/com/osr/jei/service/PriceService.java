package com.osr.jei.service;

import com.osr.jei.pricing.PriceQuote;
import com.osr.jei.pricing.PriceSnapshot;
import com.osr.jei.wiki.RequestCancelledException;
import com.osr.jei.wiki.WikiHttp;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.CompletableFuture;

/**
 * One bulk {@code /latest} download shared by every price lookup, including the nature rune.
 * The snapshot lives for {@link PriceSnapshot#TTL_MS}. A failed refresh is not stored.
 * Further clicks before {@link #FAILURE_BACKOFF_MS} reuse the previous snapshot or report
 * a miss, and do not hit the prices API again.
 */
@Slf4j
@Singleton
public class PriceService {

    static final String LATEST = "https://prices.runescape.wiki/api/v1/osrs/latest";
    static final long FAILURE_BACKOFF_MS = 8_000L;

    private final WikiHttp wikiHttp;
    private final Object lock = new Object();
    private volatile PriceSnapshot snapshot;
    private volatile long nextAttemptAt;
    private CompletableFuture<PriceSnapshot> inflight;

    @Inject
    public PriceService(WikiHttp wikiHttp) {
        this.wikiHttp = wikiHttp;
    }

    public CompletableFuture<PriceQuote> getQuote(int itemId) {
        return loadSnapshot().thenApply(loaded -> loaded.quote(itemId));
    }

    private CompletableFuture<PriceSnapshot> loadSnapshot() {
        long now = System.currentTimeMillis();
        PriceSnapshot current = snapshot;
        if (current != null && current.fresh(now)) {
            return CompletableFuture.completedFuture(current);
        }
        if (now < nextAttemptAt) {
            return CompletableFuture.completedFuture(current == null ? PriceSnapshot.empty() : current);
        }
        synchronized (lock) {
            current = snapshot;
            if (current != null && current.fresh(System.currentTimeMillis())) {
                return CompletableFuture.completedFuture(current);
            }
            if (inflight != null) {
                return inflight;
            }
            CompletableFuture<PriceSnapshot> mine = new CompletableFuture<>();
            inflight = mine;
            wikiHttp.getJson(LATEST).whenComplete((root, error) -> {
                synchronized (lock) {
                    if (inflight == mine) {
                        inflight = null;
                    }
                }
                if (error != null) {
                    Throwable cause = error.getCause() == null ? error : error.getCause();
                    if (!(cause instanceof RequestCancelledException)) {
                        nextAttemptAt = System.currentTimeMillis() + FAILURE_BACKOFF_MS;
                        log.debug("Could not refresh GE prices: {}", cause.getMessage());
                    }
                    mine.completeExceptionally(cause);
                    return;
                }
                PriceSnapshot fresh = PriceSnapshot.fromRoot(root, System.currentTimeMillis());
                snapshot = fresh;
                nextAttemptAt = 0;
                mine.complete(fresh);
            });
            return mine;
        }
    }
}
