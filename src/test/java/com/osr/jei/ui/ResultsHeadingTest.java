package com.osr.jei.ui;

import org.junit.Test;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Component;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ResultsHeadingTest {

    @Test
    public void headingIsASmallLabelWithAGapAndNoRule() {
        UiStyle style = UiStyle.from(20, FontStyleChoice.REGULAR, FontFamilyChoice.SANS_SERIF);
        JPanel heading = ResultsHeading.create(style);
        heading.setSize(220, 80);
        heading.doLayout();

        assertEquals(1, heading.getComponentCount());
        for (Component child : heading.getComponents()) {
            assertTrue(!"resultsRule".equals(child.getName()));
        }

        JLabel label = ResultsHeading.label(heading);
        assertEquals(ResultsHeading.LABEL, label.getText().replaceAll("<[^>]+>", ""));
        assertEquals(style.small.getSize(), label.getFont().getSize());
        assertEquals(6, heading.getInsets().top);
        assertTrue(heading.getPreferredSize().height < 40);
        assertTrue(heading.getPreferredSize().height > heading.getInsets().top);
    }
}
