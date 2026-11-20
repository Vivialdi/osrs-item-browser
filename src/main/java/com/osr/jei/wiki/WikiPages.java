package com.osr.jei.wiki;

import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One {@code action=parse} per page ({@code prop=wikitext|text}).
 * Callers that ask for the same page at the same time share one future.
 * Only a successful parse is worth caching; this class does not remember failures.
 */
@Slf4j
@Singleton
public class WikiPages {

    private static final String API =
        "https://oldschool.runescape.wiki/api.php?action=parse&prop=wikitext|text&format=json&redirects=true&page=";

    private final WikiHttp wikiHttp;
    private final ConcurrentHashMap<String, CompletableFuture<PageContent>> inflight = new ConcurrentHashMap<>();

    @Inject
    public WikiPages(WikiHttp wikiHttp) {
        this.wikiHttp = wikiHttp;
    }

    public CompletableFuture<PageContent> load(String title) {
        String key = title == null ? "" : title.toLowerCase(Locale.ROOT);
        CompletableFuture<PageContent> created = new CompletableFuture<>();
        CompletableFuture<PageContent> existing = inflight.putIfAbsent(key, created);
        if (existing != null) {
            return existing;
        }
        String encoded = URLEncoder.encode(title.replace(" ", "_"), StandardCharsets.UTF_8);
        wikiHttp.getWiki(API + encoded).whenComplete((root, error) -> {
            inflight.remove(key, created);
            if (error != null) {
                created.completeExceptionally(unwrap(error));
                return;
            }
            created.complete(toPage(title, root));
        });
        return created;
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
        if (error instanceof java.util.concurrent.CompletionException && error.getCause() != null) {
            return error.getCause();
        }
        return error;
    }
}
