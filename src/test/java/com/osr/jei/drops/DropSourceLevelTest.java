package com.osr.jei.drops;

import com.osr.jei.model.DropEntry;
import com.osr.jei.ui.FontFamilyChoice;
import com.osr.jei.ui.FontStyleChoice;
import com.osr.jei.ui.ScrollWidthPanel;
import com.osr.jei.ui.UiStyle;
import com.osr.jei.ui.WrappingLabel;
import com.osr.jei.wiki.DropTableParser;
import org.junit.Test;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DropSourceLevelTest {

    private static final String TABLE = ""
        + "<table class=\"item-drops\">"
        + "<tr><td><a href=\"/w/Small_salvage\">Small salvage</a></td>"
        + "<td data-sort-value=\"15\">15 <span class=\"drops-sailing\"><a href=\"/w/Sailing\" title=\"Sailing\"></a></span></td>"
        + "<td>1\u201310</td>"
        + "<td><span data-drop-oneover=\"1/17.21\">100/1,721</span></td></tr>"
        + "<tr><td>Goblin</td><td>2 <span class=\"drops-combat\"></span></td><td>1</td><td>1/128</td></tr>"
        + "<tr><td>Essence impling</td><td>42; 52 <span class=\"drops-hunter\"></span></td><td>1</td><td>1/20</td></tr>"
        + "<tr><td>Crystal chest</td><td>N/A <span class=\"drops-reward\"></span></td><td>1</td><td>1/25</td></tr>"
        + "<tr><td>Chest</td><td>52 <span class=\"drops-thieving\"></span></td><td>1</td><td>1/40</td></tr>"
        + "</table>";

    @Test
    public void skillAndRewardSourcesAreNotCombatLevels() {
        List<DropEntry> drops = DropTableParser.parse(TABLE);
        assertEquals(5, drops.size());

        DropEntry salvage = drops.get(0);
        assertEquals("sailing", salvage.getLevelKind());
        assertEquals("15", salvage.getLevel());
        assertEquals("1-10", salvage.getQuantity());
        assertEquals("Sailing 15 \u00b7 1-10 at 1/17.21", DropMerger.detail(salvage));
        assertFalse(DropSources.monster(salvage));
        assertFalse(DropSources.showMap(salvage, true, 0));
        assertFalse(DropSources.showMap(salvage, false, 2));
        assertEquals(DropSources.OTHER_EMPTY, DropSources.emptyLocations(salvage));

        DropEntry goblin = drops.get(1);
        assertEquals("combat", goblin.getLevelKind());
        assertEquals("Lv. 2 \u00b7 1 at 1/128", DropMerger.detail(goblin));
        assertTrue(DropSources.monster(goblin));
        assertFalse(DropSources.showMap(goblin, true, 0));
        assertTrue(DropSources.showMap(goblin, true, 1));
        assertEquals(DropSources.MONSTER_EMPTY, DropSources.emptyLocations(goblin));

        assertEquals("Hunter 42; 52 \u00b7 1 at 1/20", DropMerger.detail(drops.get(2)));
        assertEquals("1 at 1/25", DropMerger.detail(drops.get(3)));
        assertEquals("Thieving 52 \u00b7 1 at 1/40", DropMerger.detail(drops.get(4)));
        assertFalse(DropSources.showMap(drops.get(2), false, 3));
    }

    @Test
    public void emptyLocationLineUsesTheFullPaneWidth() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            UiStyle style = UiStyle.from(16, FontStyleChoice.REGULAR, FontFamilyChoice.SANS_SERIF);
            ScrollWidthPanel box = new ScrollWidthPanel();
            WrappingLabel label = new WrappingLabel();
            label.setName("emptyLocations");
            style.apply(label, "small");
            label.setHtml(DropSources.MONSTER_EMPTY);
            label.setAlignmentX(WrappingLabel.LEFT_ALIGNMENT);
            box.add(label);

            JScrollPane scroll = new JScrollPane(box);
            scroll.setSize(220, 140);
            scroll.getViewport().setSize(204, 140);
            box.setSize(204, 80);
            layoutDeep(scroll);
            layoutDeep(scroll);

            assertEquals(DropSources.MONSTER_EMPTY, label.getText().replaceAll("<[^>]+>", ""));
            assertEquals(style.small.getSize(), label.getFont().getSize());
            assertTrue("width " + label.getWidth(), label.getWidth() >= 160);
            assertTrue("height " + label.getHeight() + " preferred " + label.getPreferredSize().height,
                label.getHeight() + 2 >= label.getPreferredSize().height);
        });
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
