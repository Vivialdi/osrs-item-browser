package com.osr.jei.requirements;

import com.osr.jei.wiki.WikiText;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads skill and quest requirements that are already in an item page's wikitext.
 * The lede, a Requirements section, and the text next to {@code {{Infobox Bonuses}}
 * are the only windows. Recipe blocks are skipped so a smithing level is not
 * treated as a wield requirement.
 */
public final class RequirementParser {

    private static final Pattern WIKILINK_TARGET = Pattern.compile("\\[\\[([^|\\]]+)(?:\\|[^\\]]*)?\\]\\]");
    private static final Pattern SCP = Pattern.compile("\\{\\{\\s*(?:SCP|Skillreq)\\s*\\|\\s*([A-Za-z ]+?)\\s*\\|\\s*(\\d{1,2})\\b");
    private static final Pattern REQUIREMENTS_HEADING = Pattern.compile("(?m)^==+\\s*Requirements?\\s*==+\\s*$");
    private static final Pattern NEXT_HEADING = Pattern.compile("(?m)^==+");
    private static final Pattern COMPLETED = Pattern.compile("(?i)\\bcompleted the ([A-Za-z0-9][^.]{1,70}?) quest\\b");
    private static final Pattern COMPLETION = Pattern.compile("(?i)\\bcompletion of (?:the )?([A-Za-z0-9][^.]{1,70}?)(?=\\s+is required|\\s+quest\\b|[.])");
    private static final Pattern NEGATED = Pattern.compile("(?i)\\bno particular\\b|\\bno\\b.{0,40}\\brequirement\\b|\\bdoes not require\\b|\\bcannot be made\\b");

    private static final String[] SKILL_WORDS = {
        "Attack", "Strength", "Defence", "Defense", "Ranged", "Range", "Prayer", "Magic",
        "Hitpoints", "Constitution", "Cooking", "Woodcutting", "Fletching", "Fishing",
        "Firemaking", "Crafting", "Smithing", "Mining", "Herblore", "Agility", "Thieving",
        "Slayer", "Farming", "Runecrafting", "Runecraft", "Hunter", "Construction", "Sailing"
    };

    private static final Pattern LEVEL_OF;
    private static final Pattern LEVEL_NUM;
    private static final Pattern NUM_SKILL;

    static {
        String alt = String.join("|", SKILL_WORDS);
        LEVEL_OF = Pattern.compile("(?i)\\b(" + alt + ")\\s+level\\s+of\\s+(\\d{1,2})\\b");
        LEVEL_NUM = Pattern.compile("(?i)\\blevel\\s+(\\d{1,2})\\s+(" + alt + ")\\b");
        NUM_SKILL = Pattern.compile("(?i)\\b(\\d{1,2})\\s+(" + alt + ")\\b");
    }

    private RequirementParser() {
    }

    public static ItemRequirements parse(String wikitext) {
        if (wikitext == null || wikitext.isEmpty()) {
            return ItemRequirements.NONE;
        }
        Map<String, Integer> skills = new LinkedHashMap<>();
        Map<String, String> quests = new LinkedHashMap<>();
        readInfoboxQuest(wikitext, quests);
        for (String window : windows(wikitext)) {
            readWindow(window, skills, quests);
        }
        if (skills.isEmpty() && quests.isEmpty()) {
            return ItemRequirements.NONE;
        }
        List<ItemRequirements.SkillNeed> skillList = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : skills.entrySet()) {
            skillList.add(new ItemRequirements.SkillNeed(entry.getKey(), entry.getValue()));
        }
        return new ItemRequirements(skillList, new ArrayList<>(quests.values()));
    }

    private static void readInfoboxQuest(String wikitext, Map<String, String> quests) {
        List<Integer> hits = WikiText.findTemplates(wikitext, "infobox item");
        if (hits.isEmpty()) {
            return;
        }
        String block = WikiText.extractTemplateBlock(wikitext, hits.get(0));
        if (block == null) {
            return;
        }
        String raw = WikiText.parseParams(block).get("quest");
        if (raw == null || raw.trim().isEmpty() || isYesNo(raw)) {
            return;
        }
        Matcher links = WIKILINK_TARGET.matcher(raw);
        boolean any = false;
        while (links.find()) {
            addQuest(quests, links.group(1));
            any = true;
        }
        if (!any) {
            addQuest(quests, WikiText.clean(raw));
        }
    }

    private static List<String> windows(String wikitext) {
        List<String> windows = new ArrayList<>();
        int ledeEnd = headingAt(wikitext, 0);
        windows.add(ledeEnd < 0 ? wikitext : wikitext.substring(0, ledeEnd));
        Matcher requirements = REQUIREMENTS_HEADING.matcher(wikitext);
        while (requirements.find()) {
            int from = requirements.end();
            int to = headingAt(wikitext, from);
            windows.add(to < 0 ? wikitext.substring(from) : wikitext.substring(from, to));
        }
        for (int start : WikiText.findTemplates(wikitext, "infobox bonuses")) {
            String block = WikiText.extractTemplateBlock(wikitext, start);
            if (block == null) {
                continue;
            }
            int from = start + block.length();
            int to = headingAt(wikitext, from);
            int end = to < 0 ? wikitext.length() : to;
            if (end - from > 600) {
                end = from + 600;
            }
            if (from < end) {
                windows.add(wikitext.substring(from, end));
            }
        }
        return windows;
    }

    private static int headingAt(String text, int from) {
        Matcher matcher = NEXT_HEADING.matcher(text);
        if (from > 0) {
            matcher.region(from, text.length());
        }
        return matcher.find() ? matcher.start() : -1;
    }

    private static void readWindow(String window, Map<String, Integer> skills, Map<String, String> quests) {
        String stripped = removeBlocks(window, "recipe");
        stripped = removeBlocks(stripped, "infobox");
        Matcher scp = SCP.matcher(stripped);
        while (scp.find()) {
            addSkill(skills, scp.group(1), parseLevel(scp.group(2)));
        }
        stripped = removeAllTemplates(stripped);
        stripped = linksToTargets(stripped);
        stripped = stripped.replace("'''", "").replace("''", "");
        String[] sentences = stripped.split("(?<=[.!?])\\s+");
        for (String sentence : sentences) {
            if (sentence.trim().isEmpty() || NEGATED.matcher(sentence).find()) {
                continue;
            }
            if (!mentionsWield(sentence)) {
                continue;
            }
            String usable = sentence.replaceAll("(?i),\\s+which\\s+requires?\\b[^.]*", "");
            if (usable.toLowerCase(Locale.ROOT).contains("only if")) {
                continue;
            }
            collectSkills(usable, skills);
            collectQuests(usable, quests);
        }
    }

    private static boolean mentionsWield(String sentence) {
        String lower = sentence.toLowerCase(Locale.ROOT);
        return lower.contains("wield") || lower.contains("equip");
    }

    private static void collectSkills(String sentence, Map<String, Integer> skills) {
        Matcher levelOf = LEVEL_OF.matcher(sentence);
        while (levelOf.find()) {
            addSkill(skills, levelOf.group(1), parseLevel(levelOf.group(2)));
        }
        Matcher levelNum = LEVEL_NUM.matcher(sentence);
        while (levelNum.find()) {
            addSkill(skills, levelNum.group(2), parseLevel(levelNum.group(1)));
        }
        Matcher numSkill = NUM_SKILL.matcher(sentence);
        while (numSkill.find()) {
            addSkill(skills, numSkill.group(2), parseLevel(numSkill.group(1)));
        }
    }

    private static void collectQuests(String sentence, Map<String, String> quests) {
        Matcher completed = COMPLETED.matcher(sentence);
        while (completed.find()) {
            addQuest(quests, completed.group(1));
        }
        Matcher completion = COMPLETION.matcher(sentence);
        while (completion.find()) {
            addQuest(quests, completion.group(1));
        }
    }

    private static void addSkill(Map<String, Integer> skills, String raw, int level) {
        if (level < 1 || level > 99) {
            return;
        }
        String canonical = SkillNames.canonical(raw);
        if (canonical == null) {
            return;
        }
        Integer have = skills.get(canonical);
        if (have == null || level > have) {
            skills.put(canonical, level);
        }
    }

    private static void addQuest(Map<String, String> quests, String raw) {
        if (raw == null) {
            return;
        }
        String name = raw.replaceAll("\\s+", " ").trim();
        name = name.replaceAll("(?i)\\s+quest$", "").trim();
        if (name.isEmpty() || isYesNo(name)) {
            return;
        }
        String key = QuestNames.normalize(name);
        if (key.isEmpty() || quests.containsKey(key)) {
            return;
        }
        quests.put(key, name);
    }

    private static boolean isYesNo(String raw) {
        String plain = WikiText.clean(raw).trim();
        return plain.matches("(?i)no|yes|none|n/a|na");
    }

    private static int parseLevel(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String linksToTargets(String text) {
        Matcher matcher = WIKILINK_TARGET.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(1).trim()));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String removeBlocks(String text, String templateName) {
        String current = text;
        while (true) {
            List<Integer> hits = WikiText.findTemplates(current, templateName);
            if (hits.isEmpty()) {
                return current;
            }
            int start = hits.get(0);
            String block = WikiText.extractTemplateBlock(current, start);
            if (block == null) {
                return current;
            }
            current = current.substring(0, start) + current.substring(start + block.length());
        }
    }

    private static String removeAllTemplates(String text) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            if (i + 1 < text.length() && text.charAt(i) == '{' && text.charAt(i + 1) == '{') {
                String block = WikiText.extractTemplateBlock(text, i);
                if (block == null) {
                    out.append(text.charAt(i));
                    i++;
                } else {
                    i += block.length();
                }
            } else {
                out.append(text.charAt(i));
                i++;
            }
        }
        return out.toString();
    }
}
