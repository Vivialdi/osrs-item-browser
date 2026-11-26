package com.osr.jei.wiki;

import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;

/**
 * One {@code action=parse} per page. Item pages ask for rendered HTML.
 * Monster and map reads ask for wikitext only, which is enough for
 * {@code {{LocLine}}} and {@code {{Map}}} and is much smaller.
 * Callers that ask for the same page at the same time share one future.
 * A successful parse, including a confirmed missing page, is cached.
 * Transport failures are not cached; the same page is not requested again
 * until {@link #FAILURE_BACKOFF_MS} has passed or {@link #allowRetry(String)} runs.
 */
@Slf4j
@Singleton
public class WikiPages {

    public static final int CACHE_SIZE = 48;
    public static final long TTL_MS = 30L * 60L * 1000L;
    public static final long FAILURE_BACKOFF_MS = 8_000L;

    static final String API =
        "https://oldschool.runescape.wiki/api.php?action=parse&prop=wikitext|text&format=json&redirects=true&page=";
    /** No rendered HTML. Used for monster locations and map pins. */
    static final String WIKITEXT_API =
        "https://oldschool.runescape.wiki/api.php?action=parse&prop=wikitext&format=json&redirects=true&page=";

    private final WikiHttp wikiHttp;
    private final LongSupplier clock;
    private final TtlCache<String, PageContent> cache = new TtlCache<>(CACHE_SIZE, TTL_MS);
    private final TtlCache<String, PageContent> wikitextOnly = new TtlCache<>(CACHE_SIZE, TTL_MS);
    private final Map<String, CompletableFuture<PageContent>> inflight = new HashMap<>();
    private final LinkedHashMap<String, Long> retryNotBefore = new LinkedHashMap<>(16, 0.75f, true);
    private final Object lock = new Object();

    @Inject
    public WikiPages(WikiHttp wikiHttp) {
        this(wikiHttp, System::currentTimeMillis);
    }

    public WikiPages(WikiHttp wikiHttp, LongSupplier clock) {
        this.wikiHttp = wikiHttp;
        this.clock = clock == null ? System::currentTimeMillis : clock;
    }

    /** Drop a cached page and the failure holdoff so the next load hits the network once. */
    public void allowRetry(String title) {
        if (title == null || title.isEmpty()) {
            return;
        }
        synchronized (lock) {
            String key = key(title);
            retryNotBefore.remove(key);
            cache.remove(key);
            wikitextOnly.remove(key);
        }
    }

    public int size() {
        synchronized (lock) {
            return cache.size();
        }
    }

    public CompletableFuture<PageContent> load(String title) {
        return load(title, true);
    }

    /**
     * Wikitext only. A page already fetched with HTML is reused. A wikitext-only
     * result is not reused by {@link #load(String)}, which still needs the tables.
     */
    public CompletableFuture<PageContent> loadWikitext(String title) {
        return load(title, false);
    }

    private CompletableFuture<PageContent> load(String title, boolean rendered) {
        if (title == null || title.trim().isEmpty()) {
            return CompletableFuture.completedFuture(PageContent.MISSING);
        }
        String key = key(title);
        String flight = (rendered ? "f:" : "w:") + key;
        CompletableFuture<PageContent> created = new CompletableFuture<>();
        synchronized (lock) {
            long now = clock.getAsLong();
            PageContent hit = rendered ? cache.get(key, now) : cachedWikitext(key, now);
            if (hit != null) {
                return CompletableFuture.completedFuture(hit);
            }
            Long until = retryNotBefore.get(key);
            if (until != null && now < until) {
                created.completeExceptionally(new WikiBackoffException());
                return created;
            }
            retryNotBefore.remove(key);
            CompletableFuture<PageContent> existing = inflight.get(flight);
            if (existing != null) {
                return existing;
            }
            inflight.put(flight, created);
        }
        String encoded = URLEncoder.encode(title.replace(' ', '_'), StandardCharsets.UTF_8);
        String url = (rendered ? API : WIKITEXT_API) + encoded;
        final long sent = System.nanoTime();
        if (!rendered) {
            log.debug("Wiki wikitext request sent for {}", title);
        }
        try {
            wikiHttp.getWiki(url).whenComplete((root, error) -> {
                if (!rendered) {
                    log.debug("Wiki wikitext response for {} in {} ms", title, (System.nanoTime() - sent) / 1_000_000L);
                }
                finish(key, flight, title, created, root, error, rendered);
            });
        } catch (RuntimeException e) {
            finish(key, flight, title, created, null, e, rendered);
        }
        return created;
    }

    private PageContent cachedWikitext(String key, long now) {
        PageContent full = cache.get(key, now);
        if (full != null) {
            return full;
        }
        return wikitextOnly.get(key, now);
    }

    private void finish(
        String key,
        String flight,
        String title,
        CompletableFuture<PageContent> created,
        JsonObject root,
        Throwable error,
        boolean rendered
    ) {
        if (error != null) {
            Throwable cause = unwrap(error);
            boolean cancelled = cause instanceof RequestCancelledException;
            synchronized (lock) {
                if (inflight.get(flight) == created) {
                    inflight.remove(flight);
                }
                if (!cancelled) {
                    noteFailure(key, clock.getAsLong());
                }
            }
            created.completeExceptionally(cause);
            return;
        }
        PageContent page = toPage(title, root);
        synchronized (lock) {
            if (rendered) {
                cache.put(key, page, clock.getAsLong());
            } else {
                wikitextOnly.put(key, page, clock.getAsLong());
            }
            retryNotBefore.remove(key);
            if (inflight.get(flight) == created) {
                inflight.remove(flight);
            }
        }
        created.complete(page);
    }

    private void noteFailure(String key, long now) {
        retryNotBefore.put(key, now + FAILURE_BACKOFF_MS);
        while (retryNotBefore.size() > CACHE_SIZE) {
            Iterator<String> it = retryNotBefore.keySet().iterator();
            it.next();
            it.remove();
        }
    }

    private static String key(String title) {
        return title.toLowerCase(Locale.ROOT);
    }

    private static PageContent toPage(String title, JsonObject root) {
        if (root == null || !root.has("parse")) {
            log.debug("[Item Browser] Wiki page missing: {}", title);
            return PageContent.MISSING;
        }
        JsonObject parse = root.getAsJsonObject("parse");
        String wikitext = "";
        String html = "";
        if (parse.has("wikitext") && parse.get("wikitext").isJsonObject()) {
            wikitext = parse.getAsJsonObject("wikitext").get("*").getAsString();
        }
        if (parse.has("text") && parse.get("text").isJsonObject()) {
            html = parse.getAsJsonObject("text").get("*").getAsString();
        }
        log.debug("[Item Browser] Wiki page fetched: {}", title);
        return new PageContent(wikitext, html, false);
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof java.util.concurrent.CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
