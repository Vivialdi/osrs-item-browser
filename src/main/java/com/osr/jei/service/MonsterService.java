package com.osr.jei.service;

import com.osr.jei.model.MonsterInfo;
import com.osr.jei.wiki.MonsterParser;
import com.osr.jei.wiki.TtlCache;
import com.osr.jei.wiki.RequestCancelledException;
import com.osr.jei.wiki.WikiPages;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** One parse per monster page, cached after a successful read for {@link WikiPages#TTL_MS}. */
@Slf4j
@Singleton
public class MonsterService {

    static final int CACHE_SIZE = 64;

    private final WikiPages pages;
    private final TtlCache<String, Optional<MonsterInfo>> cache = new TtlCache<>(CACHE_SIZE, WikiPages.TTL_MS);

    @Inject
    public MonsterService(WikiPages pages) {
        this.pages = pages;
    }

    public CompletableFuture<Optional<MonsterInfo>> getMonsterInfo(String monsterName) {
        if (monsterName == null || monsterName.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        String key = monsterName.toLowerCase(Locale.ROOT);
        Optional<MonsterInfo> cached = cache.get(key, System.currentTimeMillis());
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return pages.loadWikitext(monsterName).handle((page, error) -> {
            if (error != null) {
                Throwable cause = error.getCause() == null ? error : error.getCause();
                if (cause instanceof RequestCancelledException) {
                    throw new CompletionException(cause);
                }
                log.debug("[JEI] Monster info failed for '{}': {}", monsterName, cause.getMessage());
                throw new CompletionException(cause);
            }
            Optional<MonsterInfo> info = page.missing
                ? Optional.empty()
                : MonsterParser.parse(monsterName, page.wikitext);
            cache.put(key, info, System.currentTimeMillis());
            return info;
        });
    }

    /** Drops a parsed monster and its failure holdoff so the next load can fetch it. */
    public void retry(String monsterName) {
        if (monsterName == null || monsterName.isEmpty()) {
            return;
        }
        cache.remove(monsterName.toLowerCase(Locale.ROOT));
        pages.allowRetry(monsterName);
    }
}
