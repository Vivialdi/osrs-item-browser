package com.osr.jei.requirements;

import java.util.Collections;
import java.util.Map;

/**
 * Real skill levels and quest states captured on the client thread.
 * An empty logged-out snapshot carries no judgment.
 */
public final class ProgressSnapshot {

    public static final ProgressSnapshot LOGGED_OUT = new ProgressSnapshot(
        false,
        Collections.<String, Integer>emptyMap(),
        Collections.<String, QuestProgress>emptyMap()
    );

    public final boolean loggedIn;
    /** Canonical skill name to the real level. */
    public final Map<String, Integer> skills;
    /** Normalized official quest name to completion. */
    public final Map<String, QuestProgress> quests;

    public ProgressSnapshot(boolean loggedIn, Map<String, Integer> skills, Map<String, QuestProgress> quests) {
        this.loggedIn = loggedIn;
        this.skills = skills == null ? Collections.<String, Integer>emptyMap() : Collections.unmodifiableMap(skills);
        this.quests = quests == null ? Collections.<String, QuestProgress>emptyMap() : Collections.unmodifiableMap(quests);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProgressSnapshot)) {
            return false;
        }
        ProgressSnapshot that = (ProgressSnapshot) other;
        return loggedIn == that.loggedIn && skills.equals(that.skills) && quests.equals(that.quests);
    }

    @Override
    public int hashCode() {
        int result = loggedIn ? 1 : 0;
        result = 31 * result + skills.hashCode();
        result = 31 * result + quests.hashCode();
        return result;
    }
}
