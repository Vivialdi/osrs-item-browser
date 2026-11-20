package com.osr.jei.requirements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Skill and quest requirements stated on one item page. */
public final class ItemRequirements {

    public static final ItemRequirements NONE = new ItemRequirements(
        Collections.<SkillNeed>emptyList(),
        Collections.<String>emptyList()
    );

    public final List<SkillNeed> skills;
    /** Quest names as written on the wiki, before client matching. */
    public final List<String> quests;

    public ItemRequirements(List<SkillNeed> skills, List<String> quests) {
        this.skills = skills == null
            ? Collections.<SkillNeed>emptyList()
            : Collections.unmodifiableList(new ArrayList<SkillNeed>(skills));
        this.quests = quests == null
            ? Collections.<String>emptyList()
            : Collections.unmodifiableList(new ArrayList<String>(quests));
    }

    public boolean isEmpty() {
        return skills.isEmpty() && quests.isEmpty();
    }

    public static final class SkillNeed {
        public final String name;
        public final int level;

        public SkillNeed(String name, int level) {
            this.name = name;
            this.level = level;
        }
    }
}
