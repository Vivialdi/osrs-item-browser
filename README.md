# OSRS Item Browser

A RuneLite sidebar for looking up Old School RuneScape items without leaving the client. Search the item list already in the game cache, then open recipes, a crafting build path, uses, drop tables, shop prices, equipment stats, and Grand Exchange figures from the OSRS Wiki.

The plugin is Java 11, built with Gradle, and uses config group `osrjei`. It does not add libraries. HTTP goes through RuneLite's injected OkHttp client and Gson.

## Features

- **Search and filters.** Type at least two characters to search the game-cache item list. Filter by tradeable, members, equipable, and gear slot. Filters use that same cache, so they do not call the wiki, and the choice is remembered. An empty search says what was wrong instead of leaving a blank grid.
- **Recipes and build-path trees.** A recipe shows the skill, level, facility, and experience. Open an ingredient, or use **Expand tree**, for a build path. Sub-pages load only when opened (depth 4, 20 items) and a loop stops. **Base materials** totals the expanded branches, including how many you still need.
- **Uses.** **Used to craft** lists items made with the one you have open.
- **Drop tables.** Monster, quantity, and rate. Rates that the wiki states as a fraction, a percentage, `Always`, or a range are shown as `1/X`. Words such as `Rare` stay as written, and a quantity like `1-3` is not treated as the rate. Hover a row for the wiki's original text. Sort by rate (most common first), combat level, or name. Rows with no combat level stay at the bottom of a level sort.
- **Requirement checks.** Skill levels on a recipe, and wield requirements from the item page, get a met or unmet mark. Unmet rows are grey. Levels are your real levels, not boosted ones. A quest counts only when it is finished and the wiki name matches exactly. Logged out, or with the setting off, the requirements stay visible and are not marked.
- **Compare with worn.** Attack, defence, strength, ranged strength, magic damage, prayer, weight, and attack speed against what you have equipped. Gains are green and losses are red. A lighter or faster result is green. A two-handed weapon is compared with your weapon and shield together. Equipping a shield or a one-handed weapon while you wear a two-handed weapon removes that weapon. Worn pages load only after you open the comparison, once per worn item.
- **Owned counts.** The header, recipe rows, and build-path nodes show how many you have. The bank is included only after you open it once this session. Until then the count says the bank has not been opened, and a short count can show a trailing `+` instead of pretending the bank is empty.
- **GE prices, buy limits, and margins.** One shared price snapshot shows the instant buy, instant sell, the margin after the 2% GE tax (capped at 5m per item, with the wiki's exempt items untaxed), the buy limit, and the result across one 4-hour limit. High alch uses the game-cache value and the nature-rune price. If the price data is old, the panel says how old it is. If the prices service cannot be reached, it says so and waits for **Retry prices** instead of requesting again on its own.
- **Ironman mode.** **Auto** follows the logged-in account. Ironman, Hardcore, Ultimate, and Group Ironman accounts see shops, drops, and crafting before Grand Exchange info. **On** and **Off** override that, including at the login screen. GE prices stay hidden when **GE prices** is off. When they are on, ironman copy labels them as a reference.
- **JEI Lookup.** Right-click an item in your inventory, bank (including placeholders and the bank inventory), worn equipment, the equipment stats interface, or on the ground, and choose **JEI Lookup**. Noted, placeholder, and worn items open the real item.

Favourites and the last 12 viewed items stay in the sidebar. The search grid and the detail pane share the panel height, and the text wraps when the panel is resized.

## Configuration

All of these live in the RuneLite config under group `osrjei`. The key names and defaults are unchanged from previous versions, so saved settings keep working. The descriptions below are the tooltips.

| Section | Setting | Key | Default | What it does |
| --- | --- | --- | --- | --- |
| Search | Tradeable | `filterTradeable` | Any trade | Tradeable items, untradeable items, or both. The same choice is in the sidebar. |
| Search | Members | `filterMembers` | Any world | Members items, free-to-play items, or both. |
| Search | Equipable | `filterEquipable` | Any item | Items that can be equipped, items that cannot, or both. |
| Search | Gear slot | `filterSlot` | Any slot | One equipment slot, or any slot. The slot comes from the game cache. |
| Prices | GE prices | `showPrices` | On | Grand Exchange prices, the post-tax margin, and the 4-hour buy limit. Sends your IP to a server RuneLite does not control. The config panel shows that warning. |
| Ironman | Ironman mode | `ironmanMode` | Auto | Auto follows the logged-in account. On and Off override that, including at the login screen. |
| Requirements | Requirement checks | `showRequirementChecks` | On | Mark skill and quest requirements you do not meet. Logged out, requirements stay visible with no mark. |
| Display | Owned counts | `showOwned` | On | Inventory, worn equipment, and bank. The bank joins in after you open it once this session. No wiki request. |
| Display | Drop sources | `showDropSources` | On | Monsters that drop the item, with `1/X` when the wiki states a rate. |
| Display | Drop sort | `dropSort` | Rate | Rate (most common first), combat level, or name. |
| Display | Compare with worn | `showEquipmentCompare` | On | Compare an equipable item with what you are wearing, including two-handed weapons. Worn pages load only after you open the comparison. |
| Display | Font size | `fontSize` | 12 | Base text size, from 10 to 22. The panel updates immediately. |
| Display | Font style | `fontStyle` | REGULAR | REGULAR or BOLD text in the sidebar. |
| Display | Font family | `fontFamily` | Default | RuneLite's interface font, or SANS_SERIF, SERIF, or MONOSPACED. |
| Display | Detail pane share | `detailRatio` | 45 | Percentage of the sidebar height used by the detail pane (25 to 75). Dragging the divider saves the same value. |

## How it uses the OSRS Wiki

The game cache supplies the search index, tradeable and members flags, equipable slots, and high-alch values. Owned counts and requirement progress come from the logged-in client. Nothing in those paths contacts the wiki.

Every wiki and prices request uses RuneLite's injected `OkHttpClient` (a `newBuilder()` that keeps its dispatcher and connection pool) and the injected `Gson`. The client adds timeouts of 10 seconds to connect, 20 seconds to read, and 20 seconds for the whole call. The `User-Agent` on every request is:

`OSRS-Item-Browser (RuneLite plugin; +https://github.com/Vivialdi/osrs-item-browser)`

A `429`, `503`, other `5xx`, or MediaWiki `maxlag` response schedules **one** retry on a daemon scheduler, waiting for `Retry-After` when it is present and never more than 8 seconds. There is no `Thread.sleep`, and a timeout or a dropped connection is not retried. A failed page is not requested again for 8 seconds unless you press **Retry**. Switching items cancels the previous item-page calls.

| Endpoint | When it runs | Cache |
| --- | --- | --- |
| `https://oldschool.runescape.wiki/api.php` `action=parse`, `prop=wikitext\|text`, `redirects=true`, `maxlag=5` | Opening an item (recipes, equipment, requirements, shops, drops, and uses are one parse). Expanding a build-path page that is not cached. Compare with worn, once per worn item, only after you open the comparison. A monster's locations, when you open that monster from the drop list. | 48 pages, 30 minutes. Confirmed missing pages are kept. Transport failures are not. Concurrent callers for the same page share one call. Parsed items are kept separately (96 entries, 30 minutes) and parsed monsters (64 entries, 30 minutes) so a cache hit does not download the page again. |
| `https://prices.runescape.wiki/api/v1/osrs/latest` | The first price lookup while **GE prices** is on, then again after 7 minutes, or when you press **Retry prices**. One download covers every item, including the nature rune. | One snapshot, 7 minutes. Not one request per item. Concurrent lookups share that download. A failed refresh keeps the previous snapshot, shows its age, and waits 8 seconds before another attempt. |
| `https://prices.runescape.wiki/api/v1/osrs/mapping` | The first buy-limit lookup while **GE prices** is on, then again after 24 hours, or from **Retry prices**. | One list, 24 hours. A failed refresh keeps the previous list and waits 8 seconds. |

If the wiki cannot be reached or times out, the detail pane says so and offers **Retry wiki page**. A missing page says the wiki has no page for that item. A loaded page with no recipes, shops, drop sources, or uses says that, instead of leaving the section blank. Grand Exchange prices that are missing say they are unavailable, or show how old the data is when a saved snapshot is still on screen. Monster locations that fail offer **Retry**. Worn stats that fail offer **Retry worn stats**.

## Screenshots

In-game shots are not in this repository yet. Capture the real client, then replace each TODO below. Do not commit a placeholder image.

1. **TODO:** Search results with a filter applied, and the detail header including an owned count. Suggested file: `docs/search-and-owned.png`.
2. **TODO:** A crafting tree with `have / need` on a shortfall, and the base-materials **still needed** line. Suggested file: `docs/crafting-tree.png`.
3. **TODO:** The drop list with `1/X` rates and the sort control. Suggested file: `docs/drop-rates.png`.
4. **TODO:** Compare with worn, including a two-handed weapon against a weapon and shield. Suggested file: `docs/compare-worn.png`.
5. **TODO:** GE instant price, post-tax margin, and the 4-hour limit. Suggested file: `docs/ge-margin.png`.
6. **TODO:** The same item on an ironman account, with GE figures marked as a reference. Suggested file: `docs/ironman-reference.png`.
7. **TODO:** The owned line before the bank has been opened this session. Suggested file: `docs/bank-not-opened.png`.
8. **TODO:** Requirement marks, one met and one unmet. Suggested file: `docs/requirements.png`.
9. **TODO:** The wiki-unreachable message and its Retry control. Suggested file: `docs/wiki-unreachable.png`.
10. **TODO:** The JEI Lookup entry on a bank item. Suggested file: `docs/jei-lookup-bank.png`.

`IN_GAME_TEST_CHECKLIST.md` is the click-through that produces these shots.

## Usage

1. Enable **OSRS Item Browser** from the Plugin Hub once it is published. Until then, run it from this repo with `gradle run`.
2. Open the sidebar from the JEI icon.
3. Type in the search box. Results appear as icons.
4. Click an item, or right-click an in-game item and choose **JEI Lookup**.

## Building

```
gradle test
```

Java 11 (`--release 11`). CI on pull requests runs the same command. `gradle run` starts a developer client when you want to walk through `IN_GAME_TEST_CHECKLIST.md`.

## Credits

Inspired by the [JEI mod](https://www.curseforge.com/minecraft/mc-mods/jei) for Minecraft.
Item data from the [OSRS Wiki](https://oldschool.runescape.wiki/) and the [OSRS Wiki prices API](https://prices.runescape.wiki/).
