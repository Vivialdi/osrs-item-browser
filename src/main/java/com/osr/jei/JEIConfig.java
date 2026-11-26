package com.osr.jei;

import com.osr.jei.drops.DropSort;
import com.osr.jei.ironman.IronmanMode;
import com.osr.jei.map.PinSize;
import com.osr.jei.search.SearchCriteria;
import com.osr.jei.ui.FontFamilyChoice;
import com.osr.jei.ui.FontStyleChoice;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

import java.awt.Color;

@ConfigGroup("osrjei")
public interface JEIConfig extends Config {

    @ConfigSection(
        name = "Search",
        description = "Filters for the sidebar search. They use the game cache and do not call the wiki.",
        position = 0
    )
    String filters = "filters";

    @ConfigSection(
        name = "Prices",
        description = "Grand Exchange prices, margins, and buy limits",
        position = 1
    )
    String prices = "prices";

    @ConfigSection(
        name = "Ironman",
        description = "How the sidebar orders tradeable sources for ironman accounts",
        position = 2
    )
    String ironman = "ironman";

    @ConfigSection(
        name = "Requirements",
        description = "Skill and quest requirement marks",
        position = 3
    )
    String requirements = "requirements";

    @ConfigSection(
        name = "Display",
        description = "Text, layout, owned counts, drop sources, and equipment comparison",
        position = 4
    )
    String display = "display";

    @ConfigSection(
        name = "Map",
        description = "Pins placed on the world map from a shop or a monster. They do not open the map.",
        position = 5
    )
    String map = "map";

    @ConfigSection(
        name = "Lookup",
        description = "Right-click Item Browser Lookup on items in the game.",
        position = 6
    )
    String lookup = "lookup";

    @ConfigItem(
        keyName = "filterTradeable",
        name = "Tradeable",
        description = "Limit search to tradeable items, untradeable items, or both. The same choice is in the sidebar.",
        section = "filters",
        position = 0
    )
    default SearchCriteria.Trade filterTradeable() {
        return SearchCriteria.Trade.ANY;
    }

    @ConfigItem(
        keyName = "filterMembers",
        name = "Members",
        description = "Limit search to members items, free-to-play items, or both. The same choice is in the sidebar.",
        section = "filters",
        position = 1
    )
    default SearchCriteria.Members filterMembers() {
        return SearchCriteria.Members.ANY;
    }

    @ConfigItem(
        keyName = "filterEquipable",
        name = "Equipable",
        description = "Limit search to items that can be equipped, items that cannot, or both.",
        section = "filters",
        position = 2
    )
    default SearchCriteria.Equip filterEquipable() {
        return SearchCriteria.Equip.ANY;
    }

    @ConfigItem(
        keyName = "filterSlot",
        name = "Gear slot",
        description = "Limit search to one equipment slot, or any slot. The slot comes from the game cache.",
        section = "filters",
        position = 3
    )
    default SearchCriteria.SlotChoice filterSlot() {
        return SearchCriteria.SlotChoice.ANY;
    }

    @ConfigItem(
        keyName = "showPrices",
        name = "GE prices",
        description = "Show Grand Exchange prices, the post-tax margin, and the 4-hour buy limit. Prices come from one shared snapshot. Limits come from one mapping list.",
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
        section = "prices",
        position = 4
    )
    default boolean showPrices() {
        return true;
    }

    @ConfigItem(
        keyName = "ironmanMode",
        name = "Ironman mode",
        description = "Auto follows the logged-in account. Ironman, Hardcore, Ultimate, and Group Ironman accounts list shops, drops, and crafting before Grand Exchange info. On and Off override that, including at the login screen. GE prices stay hidden when GE prices is off.",
        section = "ironman",
        position = 5
    )
    default IronmanMode ironmanMode() {
        return IronmanMode.AUTO;
    }

    @ConfigItem(
        keyName = "showRequirementChecks",
        name = "Requirement checks",
        description = "Mark skill and quest requirements you do not meet, and grey those rows out. While logged out, requirements stay visible with no met or unmet mark. Levels are your real levels, not boosted ones.",
        section = "requirements",
        position = 6
    )
    default boolean showRequirementChecks() {
        return true;
    }

    @ConfigItem(
        keyName = "showOwned",
        name = "Owned counts",
        description = "Show how many of each item you have in your inventory, worn equipment, and bank. The bank is included after you open it once this session. This does not contact the wiki.",
        section = "display",
        position = 7
    )
    default boolean showOwned() {
        return true;
    }

    @ConfigItem(
        keyName = "showDropSources",
        name = "Drop sources",
        description = "Show which monsters drop the selected item. Rates are written as 1/X when the wiki states one.",
        section = "display",
        position = 8
    )
    default boolean showDropSources() {
        return true;
    }

    @ConfigItem(
        keyName = "dropSort",
        name = "Drop sort",
        description = "Order drop sources by rate (most common first), combat level, or name. The same choice is in the drop list. Rows with no combat level stay at the bottom of a level sort.",
        section = "display",
        position = 9
    )
    default DropSort dropSort() {
        return DropSort.RATE;
    }

    @ConfigItem(
        keyName = "showEquipmentCompare",
        name = "Compare with worn",
        description = "Compare an equipable item with what you are wearing, including a two-handed weapon against your weapon and shield. Worn bonuses are loaded from the wiki only after you open the comparison, once per worn item.",
        section = "display",
        position = 10
    )
    default boolean showEquipmentCompare() {
        return true;
    }

    @ConfigItem(
        keyName = "fontSize",
        name = "Font size",
        description = "Base size of text in the sidebar. The panel updates as soon as you change this.",
        section = "display",
        position = 11
    )
    @Range(min = 10, max = 22)
    default int fontSize() {
        return 12;
    }

    @ConfigItem(
        keyName = "fontStyle",
        name = "Font style",
        description = "Regular or bold text in the sidebar.",
        section = "display",
        position = 12
    )
    default FontStyleChoice fontStyle() {
        return FontStyleChoice.REGULAR;
    }

    @ConfigItem(
        keyName = "fontFamily",
        name = "Font family",
        description = "RuneLite's interface font, or a standard Java family.",
        section = "display",
        position = 13
    )
    default FontFamilyChoice fontFamily() {
        return FontFamilyChoice.DEFAULT;
    }

    @ConfigItem(
        keyName = "detailRatio",
        name = "Detail pane share",
        description = "Percentage of the sidebar height used by the item detail pane. Dragging the divider updates this too.",
        section = "display",
        position = 14
    )
    @Range(min = 25, max = 75)
    default int detailRatio() {
        return 45;
    }

    @ConfigItem(
        keyName = "sectionsOpenByDefault",
        name = "Sections open by default",
        description = "Open the detail sections when you switch items. Monster locations still load only when you click a monster.",
        section = "display",
        position = 15
    )
    default boolean sectionsOpenByDefault() {
        return false;
    }

    @ConfigItem(
        keyName = "showHighAlch",
        name = "Show high alch",
        description = "Show the high alch value under the price block. Turning this off hides that row.",
        section = "display",
        position = 16
    )
    default boolean showHighAlch() {
        return true;
    }

    @ConfigItem(
        keyName = "mapPinColor",
        name = "Map pin color",
        description = "Color of the world map pins this plugin places",
        section = "map",
        position = 0
    )
    default Color mapPinColor() {
        return new Color(255, 105, 180);
    }

    @ConfigItem(
        keyName = "pinSize",
        name = "Pin size",
        description = "Size of the world map pins. Medium is the standard size.",
        section = "map",
        position = 1
    )
    default PinSize pinSize() {
        return PinSize.MEDIUM;
    }

    @ConfigItem(
        keyName = "clearPinsOnSwitch",
        name = "Clear pins on item switch",
        description = "Remove this plugin's world map pins when you switch items. When off, pins stay until you turn the plugin off.",
        section = "map",
        position = 2
    )
    default boolean clearPinsOnSwitch() {
        return true;
    }

    @ConfigItem(
        keyName = "rightClickLookup",
        name = "Right-click lookup",
        description = "Add Item Browser Lookup to the right-click menu of inventory, bank, worn, and ground items.",
        section = "lookup",
        position = 0
    )
    default boolean rightClickLookup() {
        return true;
    }
}
