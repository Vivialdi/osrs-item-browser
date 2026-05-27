package com.osr.jei;

import com.osr.jei.model.SearchItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.AsyncBufferedImage;

import javax.swing.*;
import java.awt.*;

/**
 * Renders each row in the search list: [icon]  Item Name
 */
public class ItemListCellRenderer implements ListCellRenderer<SearchItem> {

    private final ItemManager itemManager;

    public ItemListCellRenderer(ItemManager itemManager) {
        this.itemManager = itemManager;
    }

    @Override
    public Component getListCellRendererComponent(
            JList<? extends SearchItem> list,
            SearchItem value,
            int index,
            boolean isSelected,
            boolean cellHasFocus) {

        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(isSelected ? ColorScheme.BRAND_ORANGE : ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));

        JLabel iconLabel = new JLabel();
        AsyncBufferedImage image = itemManager.getImage(value.id);
        if (image != null) {
            image.addTo(iconLabel);
        }

        JLabel nameLabel = new JLabel(value.name);
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(nameLabel.getFont().deriveFont(12f));

        row.add(iconLabel, BorderLayout.WEST);
        row.add(nameLabel, BorderLayout.CENTER);

        return row;
    }
}
