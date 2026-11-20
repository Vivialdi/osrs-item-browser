package com.osr.jei.requirements;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Exact quest-name matching. A wiki name that is not an official quest name,
 * or that matches more than one, is unknown and must not be treated as complete.
 */
public final class QuestNames {

    private QuestNames() {
    }

    /**
     * Normalized name to the official display name. Ambiguous keys are omitted.
     */
    public static Map<String, String> index(Collection<String> officialNames) {
        if (officialNames == null || officialNames.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> unique = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        for (String name : officialNames) {
            String key = normalize(name);
            if (key.isEmpty()) {
                continue;
            }
            if (unique.containsKey(key) || ambiguous.contains(key)) {
                unique.remove(key);
                ambiguous.add(key);
            } else {
                unique.put(key, name);
            }
        }
        return unique;
    }

    /** Official display name, or null when {@code wikiName} is unknown or ambiguous. */
    public static String canonical(Map<String, String> index, String wikiName) {
        if (index == null || wikiName == null) {
            return null;
        }
        return index.get(normalize(wikiName));
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.toLowerCase(Locale.ROOT)
            .replace('&', ' ')
            .replace('/', ' ')
            .replace('-', ' ')
            .replace('\u2013', ' ')
            .replace('\u2014', ' ');
        text = text.replaceAll("[^a-z0-9 ]", "");
        text = text.replaceAll("\\s+", " ").trim();
        if (text.endsWith(" quest")) {
            text = text.substring(0, text.length() - " quest".length()).trim();
        }
        return text;
    }
}
