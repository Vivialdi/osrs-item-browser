package com.osr.jei;

import com.osr.jei.ironman.IronmanMode;
import com.osr.jei.ui.FontFamilyChoice;
import com.osr.jei.ui.FontStyleChoice;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("osrjei")
public interface JEIConfig extends Config {

    @ConfigSection(
        name = "Display",
        description = "Font and how the sidebar splits search results from item details",
        position = 0
    )
    String display = "display";

    @ConfigItem(
        keyName = "showPrices",
        name = "Show GE Prices",
        description = "Fetch and display Grand Exchange prices",
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
        position = 1
    )
    default boolean showPrices() {
        return true;
    }

    @ConfigItem(
        keyName = "showDropSources",
        name = "Show Drop Sources",
        description = "Show which monsters drop the selected item",
        position = 2
    )
    default boolean showDropSources() {
        return true;
    }

    @ConfigItem(
        keyName = "fontSize",
        name = "Font size",
        description = "Base size of text in the sidebar",
        section = "display",
        position = 3
    )
    @Range(min = 10, max = 22)
    default int fontSize() {
        return 12;
    }

    @ConfigItem(
        keyName = "fontStyle",
        name = "Font style",
        description = "Regular or bold text",
        section = "display",
        position = 4
    )
    default FontStyleChoice fontStyle() {
        return FontStyleChoice.REGULAR;
    }

    @ConfigItem(
        keyName = "fontFamily",
        name = "Font",
        description = "RuneLite's interface font, or a standard Java family",
        section = "display",
        position = 5
    )
    default FontFamilyChoice fontFamily() {
        return FontFamilyChoice.DEFAULT;
    }

    @ConfigItem(
        keyName = "detailRatio",
        name = "Detail pane share",
        description = "Percentage of the sidebar height used by the item detail pane",
        section = "display",
        position = 6
    )
    @Range(min = 25, max = 75)
    default int detailRatio() {
        return 45;
    }

    @ConfigItem(
        keyName = "ironmanMode",
        name = "Ironman mode",
        description = "Auto follows the logged-in account: Ironman, Hardcore, Ultimate, and Group Ironman accounts show shops, drops, and crafting before Grand Exchange info. On and Off override that, including at the login screen. Grand Exchange prices stay hidden when Show GE Prices is off.",
        position = 7
    )
    default IronmanMode ironmanMode() {
        return IronmanMode.AUTO;
    }
}
