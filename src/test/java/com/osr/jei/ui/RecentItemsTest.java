package com.osr.jei.ui;

import org.junit.Test;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RecentItemsTest {

    @Test
    public void defaultCapIsThePreviousTwelveAndStepsSnapToSix() {
        assertEquals(12, RecentItems.PREVIOUS_CAP);
        assertEquals(RecentLimit.TWELVE, RecentLimit.nearest(12));
        assertEquals(12, RecentLimit.nearest(12).count());
        assertEquals(RecentLimit.TWELVE, RecentLimit.nearest(13));
        assertEquals(RecentLimit.EIGHTEEN, RecentLimit.nearest(15));
        assertEquals(RecentLimit.SIX, RecentLimit.nearest(0));
        assertEquals(RecentLimit.THIRTY_SIX, RecentLimit.nearest(100));
        assertEquals(6, RecentLimit.values()[0].count());
        assertEquals(6, RecentLimit.EIGHTEEN.count() - RecentLimit.TWELVE.count());
    }

    @Test
    public void hiddenRecentLeavesTheSavedListAlone() {
        List<Integer> saved = new ArrayList<>(Arrays.asList(4, 5, 6));
        List<Integer> before = new ArrayList<>(saved);
        assertFalse(RecentItems.record(saved, 9, false, 12));
        assertEquals(before, saved);
        assertFalse(RecentItems.shows(false, true));
        assertFalse(RecentItems.shows(true, false));
        assertTrue(RecentItems.record(saved, 9, true, 12));
        assertEquals(Arrays.asList(9, 4, 5, 6), saved);
        assertTrue(RecentItems.shows(true, !saved.isEmpty()));
    }

    @Test
    public void hiddenRecentRowTakesNoVerticalSpace() {
        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        JPanel recent = new JPanel();
        recent.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        recent.add(new JLabel("Recent"));
        recent.add(new JLabel("Clear"));
        recent.setVisible(false);
        JPanel below = new JPanel();
        below.setPreferredSize(new Dimension(100, 20));
        column.add(recent);
        column.add(below);
        column.setSize(200, 80);
        column.doLayout();
        assertEquals(20, column.getPreferredSize().height);
        assertEquals(0, recent.getHeight());
    }

    @Test
    public void shrinkingDropsTheOldestAndClearEmptiesTheList() {
        List<Integer> newestFirst = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        assertTrue(RecentItems.trim(newestFirst, 6));
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6), newestFirst);
        assertFalse(RecentItems.trim(newestFirst, 6));
        RecentItems.clear(newestFirst);
        assertTrue(newestFirst.isEmpty());
    }

    @Test
    public void clearSitsOnTheRecentLineAndUsesTheSmallFont() {
        UiStyle style = UiStyle.from(22, FontStyleChoice.REGULAR, FontFamilyChoice.SANS_SERIF);
        AtomicInteger clears = new AtomicInteger();
        JPanel header = RecentHeader.create(style, clears::incrementAndGet);
        header.setSize(220, 40);
        header.doLayout();
        JLabel clear = RecentHeader.clear(header);
        JLabel title = (JLabel) header.getComponent(0);
        assertEquals("Recent", title.getText());
        assertEquals("Clear", clear.getText());
        assertEquals(style.small.getSize(), clear.getFont().getSize());
        assertEquals(style.small.getSize(), title.getFont().getSize());
        assertEquals(Cursor.HAND_CURSOR, clear.getCursor().getType());
        assertTrue(clear.getX() > title.getX());
        assertTrue(clear.getX() + clear.getWidth() <= header.getWidth() + 1);
        clear.dispatchEvent(new MouseEvent(
            clear, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0,
            2, 2, 1, false, MouseEvent.BUTTON1));
        assertEquals(1, clears.get());
        clear.setVisible(false);
        assertFalse(clear.isVisible());
    }
}
