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
 * One {@code action=parse} per page ({@code prop=wikitext|text}).
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

    private static final String API =
        "https://oldschool.runescape.wiki/api.php?action=parse&prop=wikitext|text&format=json&redirects=true&page=";

    private final WikiHttp wikiHttp;
    private final LongSupplier clock;
    private final TtlCache<String, PageContent> cache = new TtlCache<>(CACHE_SIZE, TTL_MS);
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
        }
    }

    public int size() {
        synchronized (lock) {
            return cache.size();
        }
    }

    public CompletableFuture<PageContent> load(String title) {
        if (title == null || title.trim().isEmpty()) {
            return CompletableFuture.completedFuture(PageContent.MISSING);
        }
        String key = key(title);
        CompletableFuture<PageContent> created = new CompletableFuture<>();
        synchronized (lock) {
            long now = clock.getAsLong();
            PageContent hit = cache.get(key, now);
            if (hit != null) {
                return CompletableFuture.completedFuture(hit);
            }
            Long until = retryNotBefore.get(key);
            if (until != null && now < until) {
                created.completeExceptionally(new WikiBackoffException());
                return created;
            }
            retryNotBefore.remove(key);
            CompletableFuture<PageContent> existing = inflight.get(key);
            if (existing != null) {
                return existing;
            }
            inflight.put(key, created);
        }
        String encoded = URLEncoder.encode(title.replace(' ', '_'), StandardCharsets.UTF_8);
        try {
            wikiHttp.getWiki(API + encoded).whenComplete((root, error) -> finish(key, title, created, root, error));
        } catch (RuntimeException e) {
            finish(key, title, created, null, e);
        }
        return created;
    }

    private void finish(String key, String title, CompletableFuture<PageContent> created, JsonObject root, Throwable error) {
        if (error != null) {
            Throwable cause = unwrap(error);
            boolean cancelled = cause instanceof RequestCancelledException;
            synchronized (lock) {
                if (inflight.get(key) == created) {
                    inflight.remove(key);
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
            cache.put(key, page, clock.getAsLong());
            retryNotBefore.remove(key);
            if (inflight.get(key) == created) {
                inflight.remove(key);
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
            log.debug("[JEI] Wiki page missing: {}", title);
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
        log.debug("[JEI] Wiki page fetched: {}", title);
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
