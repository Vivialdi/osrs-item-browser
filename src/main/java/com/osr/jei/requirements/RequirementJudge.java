package com.osr.jei.requirements;

import com.osr.jei.model.RecipeInfo;

import java.util.Map;

/** Compares parsed requirements with a player snapshot. Unknown names are neutral, never met. */
public final class RequirementJudge {

    private RequirementJudge() {
    }

    public static Mark skill(String skillName, int level, ProgressSnapshot snapshot, boolean judge) {
        if (skillName == null || skillName.isEmpty() || level <= 0) {
            return Mark.MET;
        }
        if (!judge || snapshot == null || !snapshot.loggedIn) {
            return Mark.NEUTRAL;
        }
        String canonical = SkillNames.canonical(skillName);
        if (canonical == null) {
            return Mark.NEUTRAL;
        }
        Integer have = snapshot.skills.get(canonical);
        if (have == null) {
            return Mark.NEUTRAL;
        }
        return have >= level ? Mark.MET : Mark.UNMET;
    }

    public static Mark quest(String wikiName, Map<String, String> questIndex, ProgressSnapshot snapshot, boolean judge) {
        if (wikiName == null || wikiName.isEmpty()) {
            return Mark.MET;
        }
        if (!judge || snapshot == null || !snapshot.loggedIn) {
            return Mark.NEUTRAL;
        }
        String official = QuestNames.canonical(questIndex, wikiName);
        if (official == null) {
            return Mark.NEUTRAL;
        }
        QuestProgress state = snapshot.quests.get(QuestNames.normalize(official));
        if (state == null) {
            return Mark.NEUTRAL;
        }
        return state == QuestProgress.FINISHED ? Mark.MET : Mark.UNMET;
    }

    public static Mark recipe(RecipeInfo recipe, ProgressSnapshot snapshot, boolean judge) {
        if (recipe == null) {
            return Mark.NEUTRAL;
        }
        Mark mark = Mark.MET;
        boolean any = false;
        if (recipe.skill != null && !recipe.skill.isEmpty() && recipe.level > 0) {
            any = true;
            mark = Mark.worst(mark, skill(recipe.skill, recipe.level, snapshot, judge));
        }
        if (recipe.skill2 != null && !recipe.skill2.isEmpty() && recipe.level2 > 0) {
            any = true;
            mark = Mark.worst(mark, skill(recipe.skill2, recipe.level2, snapshot, judge));
        }
        return any ? mark : Mark.NEUTRAL;
    }

    /** HTML prefix for a marker. Empty when the requirement is not judged. */
    public static String htmlPrefix(Mark mark) {
        if (mark == Mark.MET) {
            return "<font color='#64d264'>&#10003;</font> ";
        }
        if (mark == Mark.UNMET) {
            return "<font color='#dc6464'>&#10007;</font> ";
        }
        return "";
    }
}
