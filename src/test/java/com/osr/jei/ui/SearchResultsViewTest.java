package com.osr.jei.ui;

import org.junit.Test;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SearchResultsViewTest {

    @Test
    public void emptySearchRendersAsAFullWidthWrappedLabel() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            UiStyle style = UiStyle.from(16, FontStyleChoice.REGULAR, FontFamilyChoice.SANS_SERIF);
            SearchResultsView view = new SearchResultsView(style, 36, 4);
            view.showMessage(SearchResultsView.NO_MATCH);

            JScrollPane scroll = new JScrollPane(view);
            scroll.setSize(230, 160);
            scroll.getViewport().setSize(214, 160);
            view.setSize(214, 160);
            scroll.doLayout();
            layoutDeep(scroll);

            WrappingLabel label = view.emptyLabel();
            assertTrue(label.isVisible());
            assertEquals(SearchResultsView.NO_MATCH, plain(label));
            assertEquals(style.small.getSize(), label.getFont().getSize());
            assertEquals(style.small.getName(), label.getFont().getName());
            assertEquals(0, view.grid().getComponentCount());
            assertFalse(view.grid().isAncestorOf(label));
            assertTrue("message width " + label.getWidth(), label.getWidth() >= 180);
            assertTrue(label.getWidth() > 36);
            assertTrue("message height " + label.getHeight() + " preferred " + label.getPreferredSize().height,
                label.getHeight() + 2 >= label.getPreferredSize().height);
        });
    }

    @Test
    public void loadingLineUsesTheSameFullWidthLabel() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            UiStyle style = UiStyle.from(22, FontStyleChoice.REGULAR, FontFamilyChoice.SANS_SERIF);
            SearchResultsView view = new SearchResultsView(style, 36, 4);
            view.showMessage(SearchResultsView.STILL_LOADING);
            view.setSize(160, 120);
            layoutDeep(view);

            WrappingLabel label = view.emptyLabel();
            assertEquals(SearchResultsView.STILL_LOADING, plain(label));
            assertEquals(style.small.getSize(), label.getFont().getSize());
            assertTrue(label.getWidth() >= 140);
            assertTrue(label.getHeight() + 2 >= label.getPreferredSize().height);
        });
    }

    @Test
    public void iconsStayInCellsWhenTheMessageIsHidden() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SearchResultsView view = new SearchResultsView(
                UiStyle.from(12, FontStyleChoice.REGULAR, FontFamilyChoice.SANS_SERIF), 36, 4);
            view.showMessage(SearchResultsView.NO_MATCH);
            view.hideMessage();
            JPanel cell = new JPanel();
            cell.setPreferredSize(new Dimension(36, 36));
            view.grid().add(cell);
            view.setSize(230, 80);
            layoutDeep(view);

            assertFalse(view.emptyLabel().isVisible());
            assertEquals(36, cell.getWidth());
            assertEquals(36, cell.getHeight());
        });
    }

    private static String plain(WrappingLabel label) {
        String text = label.getText();
        return text == null ? "" : text.replaceAll("<[^>]+>", "");
    }

    private static void layoutDeep(Component component) {
        if (component instanceof Container) {
            Container container = (Container) component;
            container.doLayout();
            for (Component child : container.getComponents()) {
                layoutDeep(child);
            }
        }
    }
}
