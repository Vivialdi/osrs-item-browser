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
- A **Base materials** list totals the expanded branches, including how many you still need
- "Used to craft" button expands a full list of every item this ingredient is used in

### Owned counts
- The detail header shows how many of the open item you have in your inventory, worn equipment, and bank
- Recipe rows and build-path nodes show `have / need`, and shortfalls are highlighted
- The base-materials rollup adds a **still needed** figure
- The bank is included only after you open it once this session. Until then the count says the bank has not been opened, instead of treating it as zero
- Turn this off with **Show owned counts**. It does not request anything from the wiki

### Grand Exchange limits
- With **Show GE Prices** on, the first lookup also loads buy limits from the wiki `/mapping` list (once per session, or again after 24 hours)
- The detail line shows the buy limit, instant sell, the margin after the 2% GE tax (capped at 5m per item, with the wiki's exempt items untaxed), and the result across one 4-hour limit
- Ironman mode keeps the same figures, labelled as a reference only

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
- High alch and store prices stay prominent. Grand Exchange prices, limits, and margins are a reference, and they are still skipped entirely when **Show GE Prices** is off

## Screenshots

In-game screenshots are not in this repository yet. Add them here before a Plugin Hub submission:

- Search results and the item detail header, including an owned count
- A crafting tree with `have / need` on a shortfall, and the base-materials **still needed** line
- Grand Exchange instant prices, post-tax margin, and the 4-hour limit
- The same item on an Ironman account, with GE figures marked as a reference
- The detail header before the bank has been opened this session

## Usage

1. Enable the plugin from the RuneLite Plugin Hub
2. Click the **JEI** icon in the RuneLite sidebar to open the panel
3. Start typing in the search box — results appear immediately
4. Click any item icon to load its full detail
5. Right-click items in-game and choose **JEI Lookup** for instant access

## Credits

Inspired by the [JEI mod](https://www.curseforge.com/minecraft/mc-mods/jei) for Minecraft.
Item data sourced from the [OSRS Wiki](https://oldschool.runescape.wiki/).
