# OSRS Item Browser

OSRS Item Browser is a RuneLite sidebar for looking up Old School RuneScape items while you play. Search the item list that is already in the game cache, then open one item for its crafting tree, monster drops, shops, Grand Exchange prices, high alch, and what you already own.

The plugin is written in Java 11 and built with Gradle. It does not add libraries. It is licensed under the BSD 2-Clause License in `LICENSE`.

## Features

- **Search and filters.** Type at least two characters. Filter by tradeable, members, equipable, and gear slot. Filters use the game cache, so they do not call the wiki. An empty or unmatched search says what was wrong instead of leaving a blank grid. Favourites and the last viewed items stay in the sidebar.
- **Item detail pane.** Click a result to open the item. Sections start collapsed: Crafting, Shop sources, Dropped by, Used to craft, and equipment. Each header is one line with a count or a short summary. Drag the divider to give the detail pane more or less of the sidebar.
- **Crafting tree and base materials.** A recipe shows the skill, level, facility, and experience. An ingredient that is itself crafted has an arrow. Rune essence, ores, logs, raw fish, herbs, shop stock, monster drops, and items gathered by Mining, Woodcutting, Fishing, Hunter, or Farming do not. One **Base materials** summary at the bottom lists what that recipe still needs.
- **Monster drops.** Quantity and rate. A rate the wiki states as a fraction, a percentage, Always, or a range is shown as `1/X`. Words such as Rare stay as written. Sort by rate, combat level, or name. Open a monster for its locations.
- **Shops.** Shop name, price, and stock. The name opens the wiki page. **Show on map** is offered when the page has coordinates.
- **GE prices and high alch.** The guide price, the buy limit, and high alch stay visible. Instant sell, the post-tax margin, the 4-hour total, and alch profit sit under **Trading details**.
- **Bank-aware have/need counts.** Inventory and worn gear count immediately. The bank is included only after you open it on that account. Until then the line says the bank has not been checked. It does not show 0 or a trailing plus.
- **Map pins.** **Show on map** pins that shop or monster and does not open the map for you. The row then says to open the world map. Only the latest pin stays. **Map pin color** defaults to hot pink, **Pin size** defaults to Medium, and the outline stays dark. Switching items removes the pins unless **Clear pins on item switch** is off.
- **Detail sections.** Each one starts collapsed. Under **Default open sections** you can start Trading details, Equipment, Shop sources, Used to craft, Dropped by, Crafting, or Locations open when you switch items. Opening Dropped by shows the monster list from the item page. A monster row has an arrow and says "Click for locations". That click is what loads the monster. Locations, if set to start open, only opens after that click. **Show high alch** hides the high alch row when it is off. **Right-click lookup** turns the menu entry on or off.
- **Item Browser Lookup.** Right-click an item in your inventory, bank (including placeholders and the bank inventory), worn equipment, the equipment stats interface, or on the ground, and choose **Item Browser Lookup**. Noted, placeholder, and worn items open the real item. The entry does not send an action to the game.

Ironman mode can list shops, drops, and crafting before Grand Exchange info. Auto follows the logged-in account. On and Off override that.

## How to use

1. Enable **OSRS Item Browser** from the Plugin Hub once it is published. Until then, run it from this repo with the command in Building.
2. Open the sidebar. The button tooltip is **OSRS Item Browser**.
3. Type in the search box and set any filters. Results are icons.
4. Click an item, or right-click an in-game item and choose **Item Browser Lookup**.
5. Open a section header for the full list. Click an ingredient arrow for the next crafting step. Use **Show on map** when you want a pin, then open the world map yourself.

In-game screenshots are not in this repository. Capture them from a logged-in client if you want them. Do not commit a stand-in image. `IN_GAME_TEST_CHECKLIST.md` is the click-through for a logged-in client.

## Configuration

These are the names shown in the RuneLite config panel, under **OSRS Item Browser**. The group and the stored keys are unchanged, so saved settings keep working.

| Section | Setting | Default |
| --- | --- | --- |
| Search | Tradeable | Any trade |
| Search | Members | Any world |
| Search | Equipable | Any item |
| Search | Gear slot | Any slot |
| Prices | GE prices | On |
| Ironman | Ironman mode | Auto |
| Requirements | Requirement checks | On |
| Display | Owned counts | On |
| Display | Drop sources | On |
| Display | Drop sort | Rate |
| Display | Compare with worn | On |
| Display | Font size | 12 (10 to 22) |
| Display | Font style | REGULAR |
| Display | Font family | DEFAULT |
| Display | Detail pane share | 45 (25 to 75) |
| Display | Show high alch | On |
| Default open sections | Open Trading details by default | Off |
| Default open sections | Open Equipment by default | Off |
| Default open sections | Open Shop sources by default | Off |
| Default open sections | Open Used to craft by default | Off |
| Default open sections | Open Dropped by by default | Off |
| Default open sections | Open Crafting by default | Off |
| Default open sections | Open Locations by default | Off |
| Map | Map pin color | Hot pink |
| Map | Pin size | Medium |
| Map | Clear pins on item switch | On |
| Lookup | Right-click lookup | On |

**GE prices** warns that it sends your IP address to a server RuneLite does not control. The sidebar filters and the config choices stay in step with each other.

## Data sources & privacy

Search, tradeable and members flags, equipment slots, and high-alch values come from the game cache. Owned counts and requirement checks come from the logged-in client. Those paths do not contact the wiki.

Wiki pages and Grand Exchange prices use RuneLite's injected OkHttp client and Gson. The plugin does not create its own client. Every request sends this User-Agent:

`OSRS-Item-Browser (RuneLite plugin; +https://github.com/Vivialdi/osrs-item-browser)`

| What | Where | When | Cache |
| --- | --- | --- | --- |
| Item page (recipes, drops, shops, equipment, requirements) | `https://oldschool.runescape.wiki/api.php` with `action=parse`, `prop=wikitext\|text`, `redirects=true`, `maxlag=5` | Opening an item | 48 pages, 30 minutes |
| Monster or map wikitext | Same API with `prop=wikitext` only | Opening a monster, or **Show on map** when that page is not already loaded | 48 pages, 30 minutes |
| GE prices | `https://prices.runescape.wiki/api/v1/osrs/latest` | While **GE prices** is on, then again after 7 minutes, or when you press **Retry prices** | One snapshot for every item |
| Buy limits | `https://prices.runescape.wiki/api/v1/osrs/mapping` | While **GE prices** is on, then again after 24 hours, or from **Retry prices** | One list |

A 429, 503, other 5xx, or MediaWiki maxlag response schedules one retry and does not sleep the client thread. A timeout or a dropped connection is not retried. Switching items cancels the previous item's calls. Nothing about the player (name, location, bank contents, or gear) is sent. The plugin does not click, walk, or synthesize menu actions. **Item Browser Lookup** and **Show on map** only change this sidebar and the world-map pin list.

## Building

Java 11. From the repository root:

```
gradle test
```

`gradle run` starts a developer client. CI on pull requests runs `gradle test` on Temurin 11. Compile uses `-Xlint:deprecation`.

## Credits

Item data from the [OSRS Wiki](https://oldschool.runescape.wiki/) and the [OSRS Wiki prices API](https://prices.runescape.wiki/).
