package com.osr.jei.ui;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DropRowMarksTest {

    @Test
    public void collapsedMonsterRowShowsAChevronAndAHint() {
        String closed = DropRowMarks.html("Abyssal demon", "Lv. 124", false);
        assertTrue(closed.contains("<b>Abyssal demon</b>"));
        assertTrue(closed.contains(DropRowMarks.HINT));
        assertTrue(DropRowMarks.chevron(false).equals("\u25b8"));
        String open = DropRowMarks.html("Abyssal demon", "Lv. 124", true);
        assertFalse(open.contains(DropRowMarks.HINT));
        assertTrue(DropRowMarks.chevron(true).equals("\u25be"));
        assertFalse(DropRowMarks.HINT.contains("\u2014"));
    }
}
