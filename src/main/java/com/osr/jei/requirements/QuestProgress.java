package com.osr.jei.requirements;

/** Quest completion as read from {@code Quest.getState}. Only {@link #FINISHED} meets a requirement. */
public enum QuestProgress {
    NOT_STARTED,
    IN_PROGRESS,
    FINISHED
}
