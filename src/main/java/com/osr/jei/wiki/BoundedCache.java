package com.osr.jei.wiki;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small access-ordered cache. The least recently used entry is dropped
 * when {@code maxSize} is exceeded. Session-long wiki results stay bounded.
 */
public final class BoundedCache<K, V> {

    private final int maxSize;
    private final LinkedHashMap<K, V> map;

    public BoundedCache(int maxSize) {
        this.maxSize = Math.max(1, maxSize);
        this.map = new LinkedHashMap<>(16, 0.75f, true);
    }

    public synchronized V get(K key) {
        return map.get(key);
    }

    public synchronized void put(K key, V value) {
        map.put(key, value);
        while (map.size() > maxSize) {
            Iterator<Map.Entry<K, V>> it = map.entrySet().iterator();
            it.next();
            it.remove();
        }
    }

    public synchronized int size() {
        return map.size();
    }
}
