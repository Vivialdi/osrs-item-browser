package com.osr.jei;

import com.osr.jei.drops.DropSort;
import com.osr.jei.ironman.IronmanMode;
import com.osr.jei.search.SearchCriteria;
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

    @ConfigItem(
        keyName = "showOwned",
        name = "Show owned counts",
        description = "Show how many of each item you have in your inventory, worn equipment, and bank. The bank is included after you open it once this session. This does not contact the wiki.",
        position = 8
    )
    default boolean showOwned() {
        return true;
    }

    @ConfigItem(
        keyName = "showRequirementChecks",
        name = "Requirement checks",
        description = "Mark skill and quest requirements you do not meet, and grey those rows out. While logged out the requirements are shown with no met or unmet mark.",
        position = 9
    )
    default boolean showRequirementChecks() {
        return true;
    }

    @ConfigItem(
        keyName = "dropSort",
        name = "Drop sort",
        description = "Order drop sources by rate (most common first), combat level, or name. The same choice is in the drop list. Missing combat levels stay at the bottom.",
        position = 10
    )
    default DropSort dropSort() {
        return DropSort.RATE;
    }

    @ConfigItem(
        keyName = "showEquipmentCompare",
        name = "Compare equipment",
        description = "Compare an equipable item with what you are wearing. Worn bonuses are loaded from the wiki only after you open the comparison, and only once per worn item.",
        position = 11
    )
    default boolean showEquipmentCompare() {
        return true;
    }

    @ConfigSection(
        name = "Search filters",
        description = "Limit search results using the item cache. The same choices are in the sidebar.",
        position = 12
    )
    String filters = "filters";

    @ConfigItem(
        keyName = "filterTradeable",
        name = "Tradeable",
        description = "Limit search to tradeable or untradeable items",
        section = "filters",
        position = 13
    )
    default SearchCriteria.Trade filterTradeable() {
        return SearchCriteria.Trade.ANY;
    }

    @ConfigItem(
        keyName = "filterMembers",
        name = "Members",
        description = "Limit search to members or free-to-play items",
        section = "filters",
        position = 14
    )
    default SearchCriteria.Members filterMembers() {
        return SearchCriteria.Members.ANY;
    }

    @ConfigItem(
        keyName = "filterEquipable",
        name = "Equipable",
        description = "Limit search to items that can be equipped",
        section = "filters",
        position = 15
    )
    default SearchCriteria.Equip filterEquipable() {
        return SearchCriteria.Equip.ANY;
    }

    @ConfigItem(
        keyName = "filterSlot",
        name = "Gear slot",
        description = "Limit search to one equipment slot",
        section = "filters",
        position = 16
    )
    default SearchCriteria.SlotChoice filterSlot() {
        return SearchCriteria.SlotChoice.ANY;
    }
}
