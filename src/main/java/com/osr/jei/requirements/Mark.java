package com.osr.jei.requirements;

/**
 * How a requirement is shown. {@link #NEUTRAL} is used when logged out, when
 * checks are off, and when the quest or skill name cannot be matched.
 * Neutral is never displayed as met.
 */
public enum Mark {
    MET,
    UNMET,
    NEUTRAL;

    public static Mark worst(Mark left, Mark right) {
        if (left == UNMET || right == UNMET) {
            return UNMET;
        }
        if (left == NEUTRAL || right == NEUTRAL) {
            return NEUTRAL;
        }
        return MET;
    }
}
