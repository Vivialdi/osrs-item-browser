package com.osr.jei.search;

import com.osr.jei.model.SearchItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Filters the item index. Names that start with the query come first.
 * Within each group the original id order is preserved.
 */
public final class ItemSearch {

    private ItemSearch() {
    }

    public static List<SearchItem> rank(List<SearchItem> items, String query, int limit) {
        return rank(items, query, limit, SearchCriteria.ANY);
    }

    public static List<SearchItem> rank(List<SearchItem> items, String query, int limit, SearchCriteria criteria) {
        if (items == null || items.isEmpty() || query == null || limit <= 0) {
            return Collections.emptyList();
        }
        SearchCriteria filter = criteria == null ? SearchCriteria.ANY : criteria;
        String lower = query.trim().toLowerCase(java.util.Locale.ROOT);
        if (lower.length() < 2) {
            return Collections.emptyList();
        }
        List<SearchItem> prefix = new ArrayList<>();
        List<SearchItem> rest = new ArrayList<>();
        for (SearchItem item : items) {
            if (item == null || item.nameLower == null || !filter.matches(item)) {
                continue;
            }
            if (!item.nameLower.contains(lower)) {
                continue;
            }
            if (item.nameLower.startsWith(lower)) {
                prefix.add(item);
            } else {
                rest.add(item);
            }
        }
        List<SearchItem> out = new ArrayList<>(Math.min(limit, prefix.size() + rest.size()));
        for (SearchItem item : prefix) {
            if (out.size() >= limit) {
                return out;
            }
            out.add(item);
        }
        for (SearchItem item : rest) {
            if (out.size() >= limit) {
                break;
            }
            out.add(item);
        }
        return out;
    }
}
