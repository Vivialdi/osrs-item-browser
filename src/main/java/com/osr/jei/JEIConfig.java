package com.osr.jei;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("osrjei")
public interface JEIConfig extends Config {

    @ConfigItem(
        keyName = "showPrices",
        name = "Show GE Prices",
        description = "Fetch and display Grand Exchange prices",
        position = 1
    )
    default boolean showPrices() {
        return true;
    }

    @ConfigItem(
        keyName = "showDropSources",
        name = "Show Drop Sources",
        description = "Fetch and display which monsters drop the selected item",
        position = 2
    )
    default boolean showDropSources() {
        return true;
    }
}
