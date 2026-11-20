package com.osr.jei.service;

import com.osr.jei.model.MonsterInfo;
import com.osr.jei.wiki.BoundedCache;
import com.osr.jei.wiki.MonsterParser;
import com.osr.jei.wiki.RequestCancelledException;
import com.osr.jei.wiki.WikiPages;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** One parse per monster page, cached after a successful read. */
@Slf4j
@Singleton
public class MonsterService {

    private final WikiPages pages;
    private final BoundedCache<String, Optional<MonsterInfo>> cache = new BoundedCache<>(64);

    @Inject
    public MonsterService(WikiPages pages) {
        this.pages = pages;
    }

    public CompletableFuture<Optional<MonsterInfo>> getMonsterInfo(String monsterName) {
        if (monsterName == null || monsterName.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        String key = monsterName.toLowerCase(Locale.ROOT);
        Optional<MonsterInfo> cached = cache.get(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return pages.load(monsterName).handle((page, error) -> {
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
            cache.put(key, info);
            return info;
        });
    }
}
