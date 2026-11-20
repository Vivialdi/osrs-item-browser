package com.osr.jei.service;

import com.osr.jei.pricing.PriceLookup;
import com.osr.jei.pricing.PriceSnapshot;
import com.osr.jei.wiki.RequestCancelledException;
import com.osr.jei.wiki.WikiHttp;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * One bulk {@code /latest} download shared by every price lookup, including the nature rune.
 * The snapshot lives for {@link PriceSnapshot#TTL_MS}. A failed refresh keeps the previous
 * snapshot, waits {@link #FAILURE_BACKOFF_MS}, and does not hit the prices API again until
 * that passes or {@link #allowRetry()} is called. Concurrent lookups share one request.
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
    private CompletableFuture<Load> inflight;

    @Inject
    public PriceService(WikiHttp wikiHttp) {
        this.wikiHttp = wikiHttp;
    }

    /** Lets the next lookup contact the prices API even if a failure holdoff is active. */
    public void allowRetry() {
        nextAttemptAt = 0L;
    }

    public CompletableFuture<PriceLookup> lookup(int itemId) {
        return load().thenApply(loaded ->
            PriceLookup.from(loaded.snapshot, loaded.failed, itemId, System.currentTimeMillis()));
    }

    private CompletableFuture<Load> load() {
        long now = System.currentTimeMillis();
        PriceSnapshot current = snapshot;
        if (current != null && current.fresh(now)) {
            return CompletableFuture.completedFuture(new Load(current, false));
        }
        if (now < nextAttemptAt) {
            return CompletableFuture.completedFuture(new Load(current, true));
        }
        synchronized (lock) {
            current = snapshot;
            now = System.currentTimeMillis();
            if (current != null && current.fresh(now)) {
                return CompletableFuture.completedFuture(new Load(current, false));
            }
            if (now < nextAttemptAt) {
                return CompletableFuture.completedFuture(new Load(current, true));
            }
            if (inflight != null) {
                return inflight;
            }
            CompletableFuture<Load> mine = new CompletableFuture<>();
            inflight = mine;
            wikiHttp.getJson(LATEST).whenComplete((root, error) -> {
                synchronized (lock) {
                    if (inflight == mine) {
                        inflight = null;
                    }
                }
                if (error != null) {
                    Throwable cause = unwrap(error);
                    if (cause instanceof RequestCancelledException) {
                        mine.completeExceptionally(cause);
                        return;
                    }
                    nextAttemptAt = System.currentTimeMillis() + FAILURE_BACKOFF_MS;
                    log.debug("Could not refresh GE prices: {}", cause.getMessage());
                    mine.complete(new Load(snapshot, true));
                    return;
                }
                PriceSnapshot fresh = PriceSnapshot.fromRoot(root, System.currentTimeMillis());
                snapshot = fresh;
                nextAttemptAt = 0L;
                mine.complete(new Load(fresh, false));
            });
            return mine;
        }
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static final class Load {
        private final PriceSnapshot snapshot;
        private final boolean failed;

        private Load(PriceSnapshot snapshot, boolean failed) {
            this.snapshot = snapshot == null ? PriceSnapshot.empty() : snapshot;
            this.failed = failed;
        }
    }
}
