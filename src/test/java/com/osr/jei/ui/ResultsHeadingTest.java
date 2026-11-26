package com.osr.jei.ui;

import org.junit.Test;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.Dimension;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ResultsHeadingTest {

    @Test
    public void headingIsAThinRuleAndASmallLabel() {
        UiStyle style = UiStyle.from(20, FontStyleChoice.REGULAR, FontFamilyChoice.SANS_SERIF);
        JPanel heading = ResultsHeading.create(style);
        heading.setSize(220, 80);
        heading.doLayout();

        JPanel rule = null;
        for (Component child : heading.getComponents()) {
            if ("resultsRule".equals(child.getName())) {
                rule = (JPanel) child;
            }
        }
        assertTrue(rule != null);
        assertEquals(1, rule.getPreferredSize().height);
        assertEquals(1, rule.getMaximumSize().height);

        JLabel label = ResultsHeading.label(heading);
        assertEquals(ResultsHeading.LABEL, label.getText().replaceAll("<[^>]+>", ""));
        assertEquals(style.small.getSize(), label.getFont().getSize());
        assertTrue(heading.getPreferredSize().height < 48);
        assertTrue(heading.getPreferredSize().height > rule.getPreferredSize().height);
    }
}
