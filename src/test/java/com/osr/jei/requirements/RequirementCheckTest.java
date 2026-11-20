package com.osr.jei.requirements;

import com.osr.jei.model.RecipeInfo;
import com.osr.jei.model.RecipeIngredient;
import com.osr.jei.service.ItemDetails;
import com.osr.jei.wiki.PageContent;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RequirementCheckTest {

    private static final String WHIP = ""
        + "{{Infobox Item\n|name = Abyssal whip\n|quest = No\n|weight = 0.453\n}}\n"
        + "The '''abyssal whip''' is a one-handed [[melee weapon]] which requires an [[Attack]] level of 70 to wield.\n"
        + "==Combat stats==\n"
        + "{{Infobox Bonuses\n|aslash = +82\n|str = +82\n|slot = weapon\n|speed = 4\n}}\n"
        + "==Upgrading==\n"
        + "An [[abyssal tentacle]] requires an [[Attack]] level of 75.\n";

    private static final String SCIMITAR = ""
        + "{{Infobox Item\n|quest = No\n}}\n"
        + "It can only be wielded by players who have 60 [[Attack]] and have completed the [[Monkey Madness I]] quest.\n"
        + "A [[monkeyspeak amulet]] does not need to be worn, unless you have completed [[Monkey Madness II]].\n";

    private static final String HELMET = ""
        + "{{Infobox Item\n|quest = No\n}}\n"
        + "[[Reinforced goggles]] are additionally required only if the quest [[A Porcine of Interest]] is completed.\n"
        + "It requires 10 [[Defence]] to equip.\n"
        + "Despite the helmet requiring a [[nose peg]], which requires 60 [[Slayer]] to equip, the Slayer helmet requires no particular Slayer level to wear.\n";

    private static final String AVA = ""
        + "{{Infobox Item\n|quest = [[Animal Magnetism]]\n}}\n"
        + "'''Ava's accumulator''' is awarded by completing the quest [[Animal Magnetism]] if the player is level 50 [[Ranged]] or above.\n"
        + "It is equipped in the [[cape slot]].\n";

    private static final String PLATE = ""
        + "{{Infobox Item\n|quest = No\n}}\n"
        + "It has no [[Defence]] requirement to equip.\n"
        + "{{Recipe\n|skill1 = Smithing\n|skill1lvl = 33\n|skill1exp = 125\n|mat1 = Iron bar\n|mat1quantity = 5\n}}\n";

    private static final String STAFF = ""
        + "{{Infobox Item\n|quest = No\n}}\n"
        + "A staff.\n"
        + "==Requirements==\n"
        + "{{SCP|Magic|75}}\n"
        + "Completion of [[Desert Treasure I]] is required to wield this staff.\n"
        + "==Combat stats==\n";

    @Test
    public void parsesWieldLinesAndIgnoresOtherSections() {
        ItemRequirements whip = RequirementParser.parse(WHIP);
        assertEquals(1, whip.skills.size());
        assertEquals("Attack", whip.skills.get(0).name);
        assertEquals(70, whip.skills.get(0).level);
        assertTrue(whip.quests.isEmpty());

        ItemRequirements scimitar = RequirementParser.parse(SCIMITAR);
        assertEquals("Attack", scimitar.skills.get(0).name);
        assertEquals(60, scimitar.skills.get(0).level);
        assertEquals(Collections.singletonList("Monkey Madness I"), scimitar.quests);

        ItemRequirements helmet = RequirementParser.parse(HELMET);
        assertEquals(1, helmet.skills.size());
        assertEquals("Defence", helmet.skills.get(0).name);
        assertEquals(10, helmet.skills.get(0).level);
        assertTrue(helmet.quests.isEmpty());

        ItemRequirements ava = RequirementParser.parse(AVA);
        assertTrue(ava.skills.isEmpty());
        assertEquals(Collections.singletonList("Animal Magnetism"), ava.quests);

        ItemRequirements plate = RequirementParser.parse(PLATE);
        assertTrue(plate.isEmpty());

        ItemRequirements staff = RequirementParser.parse(STAFF);
        assertEquals("Magic", staff.skills.get(0).name);
        assertEquals(75, staff.skills.get(0).level);
        assertEquals("Desert Treasure I", staff.quests.get(0));
    }

    @Test
    public void pageParseKeepsRequirementsWithTheRecipe() {
        ItemDetails details = ItemDetails.from(new PageContent(PLATE, "", false));
        assertEquals("Smithing", details.recipes.get(0).skill);
        assertEquals(33, details.recipes.get(0).level);
        assertTrue(details.requirements.isEmpty());
    }

    @Test
    public void questNamesMatchExactly() {
        Map<String, String> index = QuestNames.index(Arrays.asList(
            "Dragon Slayer I",
            "Dragon Slayer II",
            "Monkey Madness I",
            "Animal Magnetism"
        ));
        assertEquals("Dragon Slayer I", QuestNames.canonical(index, "Dragon Slayer I"));
        assertNull(QuestNames.canonical(index, "Dragon Slayer"));
        assertNull(QuestNames.canonical(index, "Monkey Madness"));
        assertNull(QuestNames.canonical(index, "Fight Cave"));
        assertEquals("Animal Magnetism", QuestNames.canonical(index, "Animal Magnetism quest"));
    }

    @Test
    public void ambiguousQuestNamesAreNeverMet() {
        Map<String, String> index = QuestNames.index(Arrays.asList("Twin Quest", "Twin-Quest"));
        assertNull(QuestNames.canonical(index, "Twin Quest"));
        ProgressSnapshot loggedIn = snapshot(99, QuestProgress.FINISHED, "twin quest");
        assertEquals(Mark.NEUTRAL, RequirementJudge.quest("Twin Quest", index, loggedIn, true));
    }

    @Test
    public void comparisonUsesRealLevelsAndNeverMarksUnknownAsMet() {
        Map<String, String> quests = QuestNames.index(Collections.singletonList("Monkey Madness I"));
        ProgressSnapshot ready = snapshot(60, QuestProgress.FINISHED, "monkey madness i");
        ProgressSnapshot shortLevel = snapshot(59, QuestProgress.FINISHED, "monkey madness i");
        ProgressSnapshot inProgress = snapshot(60, QuestProgress.IN_PROGRESS, "monkey madness i");

        assertEquals(Mark.MET, RequirementJudge.skill("Attack", 60, ready, true));
        assertEquals(Mark.UNMET, RequirementJudge.skill("Defense", 61, ready, true));
        assertEquals(Mark.NEUTRAL, RequirementJudge.skill("Woodworking", 10, ready, true));
        assertEquals(Mark.MET, RequirementJudge.quest("Monkey Madness I", quests, ready, true));
        assertEquals(Mark.UNMET, RequirementJudge.quest("Monkey Madness I", quests, inProgress, true));
        assertEquals(Mark.NEUTRAL, RequirementJudge.quest("Not A Real Quest", quests, ready, true));
        assertEquals(Mark.NEUTRAL, RequirementJudge.skill("Attack", 60, ProgressSnapshot.LOGGED_OUT, true));
        assertEquals(Mark.NEUTRAL, RequirementJudge.quest("Monkey Madness I", quests, ready, false));

        RecipeInfo recipe = new RecipeInfo(
            "Smithing", 99, "Anvil", 1,
            Collections.singletonList(new RecipeIngredient("Rune bar", 1)),
            1, "", "Crafting", 40);
        assertEquals(Mark.UNMET, RequirementJudge.recipe(recipe, shortLevel, true));
        Map<String, Integer> skilled = new HashMap<>();
        skilled.put("Smithing", 99);
        skilled.put("Crafting", 40);
        ProgressSnapshot both = new ProgressSnapshot(true, skilled, Collections.<String, QuestProgress>emptyMap());
        assertEquals(Mark.MET, RequirementJudge.recipe(recipe, both, true));
        assertEquals(Mark.NEUTRAL, RequirementJudge.recipe(recipe, both, false));
    }

    private static ProgressSnapshot snapshot(int attack, QuestProgress quest, String questKey) {
        Map<String, Integer> skills = new HashMap<>();
        skills.put("Attack", attack);
        skills.put("Defence", 60);
        skills.put("Smithing", 50);
        skills.put("Crafting", 40);
        Map<String, QuestProgress> quests = new HashMap<>();
        quests.put(questKey, quest);
        return new ProgressSnapshot(true, skills, quests);
    }
}
