package com.osr.jei.wiki;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Access-ordered cache with a maximum size and a time-to-live.
 * The least recently used entry is dropped when {@code maxSize} is exceeded.
 * An entry older than the TTL is treated as absent.
 */
public final class TtlCache<K, V> {

    private static final class Timed<V> {
        private final V value;
        private final long storedAt;

        private Timed(V value, long storedAt) {
            this.value = value;
            this.storedAt = storedAt;
        }
    }

    private final int maxSize;
    private final long ttlMs;
    private final LinkedHashMap<K, Timed<V>> map;

    public TtlCache(int maxSize, long ttlMs) {
        this.maxSize = Math.max(1, maxSize);
        this.ttlMs = Math.max(1L, ttlMs);
        this.map = new LinkedHashMap<>(16, 0.75f, true);
    }

    public synchronized V get(K key, long nowMs) {
        Timed<V> entry = map.get(key);
        if (entry == null) {
            return null;
        }
        if (expired(entry, nowMs)) {
            map.remove(key);
            return null;
        }
        return entry.value;
    }

    public synchronized void put(K key, V value, long nowMs) {
        if (key == null || value == null) {
            return;
        }
        evictExpired(nowMs);
        map.put(key, new Timed<>(value, nowMs));
        while (map.size() > maxSize) {
            Iterator<Map.Entry<K, Timed<V>>> it = map.entrySet().iterator();
            it.next();
            it.remove();
        }
    }

    public synchronized void remove(K key) {
        map.remove(key);
    }

    public synchronized int size() {
        return map.size();
    }

    private void evictExpired(long nowMs) {
        Iterator<Map.Entry<K, Timed<V>>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            if (expired(it.next().getValue(), nowMs)) {
                it.remove();
            }
        }
    }

    private boolean expired(Timed<V> entry, long nowMs) {
        return nowMs >= entry.storedAt && nowMs - entry.storedAt >= ttlMs;
    }
}
