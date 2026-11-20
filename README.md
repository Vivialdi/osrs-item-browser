# OSRS Item Browser

A RuneLite sidebar plugin inspired by JEI (Just Enough Items) from Minecraft.
Search any Old School RuneScape item instantly and see everything about it in one place — no need to alt-tab to the wiki.

## Features

### Item Search
- Search the full OSRS item catalogue by name (inventory items, equipment, quest items, and more)
- Results appear as an icon grid as you type — click any item to load its detail panel

### Grand Exchange Price
- Live GE price fetched from the RuneLite pricing API
- High-Alch value and profit/loss vs. GE price shown instantly (includes Nature Rune cost)

### Crafting Recipes
- See the skill, level requirement, facility (anvil, furnace, etc.), and XP reward for crafted items
- Click any ingredient in the recipe to jump straight to that item's detail page
- Expand an ingredient, or use **Expand tree**, for a build path. Sub-pages load only when opened (depth 4, 20 items) and looping recipes stop
- A **Base materials** list totals the expanded branches
- "Used to craft" button expands a full list of every item this ingredient is used in

### Drop Sources
- Drop table for any item: monster name, combat level, drop quantity, and drop rate
  
### Equipment Stats
- Attack and Defence bonuses for every combat style (Stab, Slash, Crush, Magic, Range)
- Strength, Ranged Strength, Magic Damage %, and Prayer bonus
- Slot type and weapon speed

### Shop Sources
- Lists every in-game shop that sells the item
- Shop price coloured **green** when cheaper than GE, **red** when more expensive

### Favourites
- Star (★) any item to save it to your favourites list
- Toggle the ★ button in the search bar to view only starred items

### Recently Viewed
- The last 12 viewed items are shown as a quick-access icon row below the search bar

### Right-Click Lookup
- Right-click an item in your **inventory**, **bank** (including placeholders and the bank inventory), **worn equipment**, the **equipment stats** interface, or on the **ground** and choose **JEI Lookup**
- Noted, placeholder, and worn items open the real item

### Ironman mode
- **Auto** (the default) follows the logged-in account. Ironman, Hardcore, Ultimate, and Group Ironman accounts see shops, drop sources, and the crafting tree before Grand Exchange info
- **On** and **Off** override that, including at the login screen
- High alch and store prices stay prominent. Grand Exchange prices are a reference, and they are still skipped entirely when **Show GE Prices** is off

## Usage

1. Enable the plugin from the RuneLite Plugin Hub
2. Click the **JEI** icon in the RuneLite sidebar to open the panel
3. Start typing in the search box — results appear immediately
4. Click any item icon to load its full detail
5. Right-click items in-game and choose **JEI Lookup** for instant access

## Credits

Inspired by the [JEI mod](https://www.curseforge.com/minecraft/mc-mods/jei) for Minecraft.
Item data sourced from the [OSRS Wiki](https://oldschool.runescape.wiki/).
