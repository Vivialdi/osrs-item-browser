package com.osr.jei.service;

import com.osr.jei.pricing.BuyLimitSnapshot;
import com.osr.jei.pricing.LimitResult;
import com.osr.jei.wiki.RequestCancelledException;
import com.osr.jei.wiki.WikiHttp;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * One {@code /mapping} download for GE buy limits. It is started only when a
 * caller asks, kept for {@link BuyLimitSnapshot#TTL_MS}, and not requested
 * again per item. Concurrent callers share one request. A failed attempt
 * keeps the previous snapshot, waits {@link #FAILURE_BACKOFF_MS}, and does
 * not replace a previous good snapshot. {@link #allowRetry()} clears the wait.
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
    private CompletableFuture<LimitResult> inflight;

    @Inject
    public BuyLimitService(WikiHttp wikiHttp) {
        this.wikiHttp = wikiHttp;
    }

    /** Lets the next load contact the mapping API even if a failure holdoff is active. */
    public void allowRetry() {
        nextAttemptAt = 0L;
    }

    public CompletableFuture<LimitResult> load() {
        long now = System.currentTimeMillis();
        BuyLimitSnapshot current = snapshot;
        if (current != null && current.fresh(now)) {
            return CompletableFuture.completedFuture(new LimitResult(current, false));
        }
        if (now < nextAttemptAt) {
            return CompletableFuture.completedFuture(new LimitResult(current, true));
        }
        synchronized (lock) {
            current = snapshot;
            now = System.currentTimeMillis();
            if (current != null && current.fresh(now)) {
                return CompletableFuture.completedFuture(new LimitResult(current, false));
            }
            if (now < nextAttemptAt) {
                return CompletableFuture.completedFuture(new LimitResult(current, true));
            }
            if (inflight != null) {
                return inflight;
            }
            CompletableFuture<LimitResult> mine = new CompletableFuture<>();
            inflight = mine;
            wikiHttp.getStableElement(MAPPING).whenComplete((root, error) -> {
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
                    log.debug("Could not refresh GE buy limits: {}", cause.getMessage());
                    mine.complete(new LimitResult(snapshot, true));
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
                mine.complete(new LimitResult(fresh, false));
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
}
