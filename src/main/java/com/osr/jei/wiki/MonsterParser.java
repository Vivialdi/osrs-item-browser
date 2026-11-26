package com.osr.jei.wiki;

import com.osr.jei.model.MonsterInfo;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Combat level and {@code {{LocLine}}} locations from a monster page. */
public final class MonsterParser {

    private MonsterParser() {
    }

    public static Optional<MonsterInfo> parse(String monsterName, String wikitext) {
        if (wikitext == null || wikitext.isEmpty()) {
            return Optional.empty();
        }
        int combat = parseCombatLevel(wikitext);
        List<String> locations = parseLocLines(wikitext);
        if (locations.isEmpty() && combat == 0) {
            return Optional.empty();
        }
        boolean mapped = !MapPins.parse(wikitext).isEmpty();
        return Optional.of(new MonsterInfo(monsterName, combat, locations, mapped));
    }

    static int parseCombatLevel(String wikitext) {
        List<Integer> hits = WikiText.findTemplates(wikitext, "infobox monster");
        if (hits.isEmpty()) {
            return 0;
        }
        String block = WikiText.extractTemplateBlock(wikitext, hits.get(0));
        if (block == null) {
            return 0;
        }
        Map<String, String> params = WikiText.parseParams(block);
        return WikiText.firstInt(WikiText.clean(WikiText.coalesce(params, "combat1", "combat", "combat2")));
    }

    static List<String> parseLocLines(String wikitext) {
        Set<String> seen = new LinkedHashSet<>();
        for (int idx : WikiText.findTemplates(wikitext, "locline")) {
            String block = WikiText.extractTemplateBlock(wikitext, idx);
            if (block == null) {
                continue;
            }
            Map<String, String> params = WikiText.parseParams(block);
            String location = LocationNames.clean(params.getOrDefault("location", ""));
            if (!location.isEmpty()
                && !location.equalsIgnoreCase("none")
                && !location.equalsIgnoreCase("n/a")) {
                seen.add(location);
            }
        }
        return new ArrayList<>(seen);
    }
}
