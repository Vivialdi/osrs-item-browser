# In-game test checklist

Walk through this in a logged-in RuneLite client (`gradle run`, or a sideloaded build). Headless tests do not cover menus, the bank, worn equipment, or the live config panel. Each step has the result you should see. Suggested shots are not in the repo. Do not commit a stand-in image.

Use a normal account first, then an ironman account for the ironman steps. Have one equipable item, one two-handed weapon, one shield, and one item with a recipe and a drop table (a rune platebody and an abyssal whip are enough).

## Search and filters

1. Log in and wait until the "item list is still loading" line disappears.
   - Expected: the status line goes away. The detail pane still says to search.
2. Type `ru`.
   - Expected: an icon grid appears. Fewer than two characters leaves the grid empty.
3. Type `rune plate` and set the sidebar filters to Tradeable, Members, Equipable, Body.
   - Expected: Rune platebody remains. An untradeable or a weapon does not.
   - Optional shot, not in the repo: the grid plus the detail header once the owned line is visible.
4. Change **Gear slot** in the plugin config to Weapon, then back to Any slot.
   - Expected: the sidebar slot dropdown follows the config, and the grid updates without reopening the panel.
5. Search for `zzzznotanitem`.
   - Expected: "No items match that search." Not a blank grid.

## Item Browser Lookup

6. Right-click a bank item, including a placeholder and an item in the bank inventory, and choose **Item Browser Lookup**.
   - Expected: the sidebar opens on that item. A noted or placeholder item opens the real item, not the note.
7. Right-click a worn item and an item in the equipment stats interface.
   - Expected: both open the real item.
8. Right-click a ground item.
   - Expected: **Item Browser Lookup** is on the menu and opens that item.

## Owned counts

9. Before opening the bank this session, look up an item you are holding.
   - Expected: `Owned: bank not checked` when you hold none, or `Owned: N (bank not checked)` when you hold some in the inventory or worn gear. It never says `Owned: 0` or uses a trailing `+`. A recipe row says `bank not checked / need M`, or `have N (bank not checked) / need M`, in grey. The tooltip says the bank is not included.
   - Optional shot, not in the repo: the owned line before the bank has been opened.
10. Open the bank, close it, and look at the same item.
    - Expected: the "bank not checked" text is gone and the count includes the bank, with red or green only then. No wiki request was added by opening the bank.
11. Turn **Owned counts** off and on in the config.
    - Expected: the header and the recipe `have / need` text appear and disappear immediately.

## Requirements

12. Open an item whose recipe or wield text needs a level you have and one you do not (or a finished quest and an unfinished one).
    - Expected: met rows are marked, unmet rows are grey. Boosted levels do not count.
    - Optional shot, not in the repo: one met requirement and one unmet requirement.
13. Gain a level, or complete the quest, without reloading the client.
    - Expected: the marks update on the open item.
14. Turn **Requirement checks** off.
    - Expected: the requirement text stays, with no met or unmet mark.
15. Log out.
    - Expected: requirements stay visible and are not marked met or unmet.

## Recipes, uses, and drops

16. Open an item with a recipe. Click the arrow on an ingredient that is crafted further (an orb or a staff, not rune essence).
    - Expected: that branch opens. Rune essence, an ore, or a log has no arrow. **Base materials** is one summary at the bottom and shows a still-needed figure where you are short.
17. Open **Used to craft** on an ingredient that is used in something.
   - Expected: the header is one line with a count until you click it. The product list then opens under that header in the detail pane. An item with no uses says "No uses listed."
18. Open **Dropped by** and try each sort: Rate, Combat level, Name. Click one monster, then click the locations header.
   - Expected: the drop header shows a count and the most common monster before the list is open. Rates that are numeric show as `1/X`. The hover text is the wiki's original rate. Combat level sort keeps rows with no level at the bottom. Changing **Drop sort** in the config reorders the open list. Locations stay under that monster. Collapsing them does not request the wiki page again.
   - Optional shot, not in the repo: the drop list with `1/X` rates and the sort control.
19. Turn **Drop sources** off.
    - Expected: the drop control hides. The other sections stay.

## Compare with worn

20. Equip a normal weapon and a shield. Open another weapon, then **Compare with worn**.
    - Expected: "Log in to compare…" does not appear. Deltas show after worn pages load. Green is better, red is worse. Weight and speed treat a smaller number as better.
21. Equip a two-handed weapon. Compare a shield, then compare a different two-handed weapon.
    - Expected: the note says the shield replaces your two-handed weapon, and the other two-hander replaces your two-handed weapon (or your weapon and shield, if that is what you are wearing).
    - Optional shot, not in the repo: compare with worn, including a two-handed weapon.
22. Turn **Compare with worn** off.
    - Expected: the comparison control disappears immediately.

## Grand Exchange and ironman

23. With **GE prices** on, open a tradeable item.
   - Expected: the always-visible block is the GE guide price, the buy limit, and high alch. Instant sell, post-tax margin, the 4-hour total, and alch profit are under **Trading details**. High alch profit is not on the always-visible block.
    - Optional shot, not in the repo: the GE guide price, the buy limit, and high alch.
24. If a line says the price data is some number of minutes or hours old, leave it.
    - Expected: the age is on screen. The panel does not keep requesting prices while that snapshot is still in date.
25. Log into an ironman (or set **Ironman mode** to On). Open the same item.
   - Expected: shop, drop, and crafting headers sit above the GE block. GE text is labelled as a reference and is split into short lines. **Auto** does this only for an ironman account. **Off** restores the normal order even on an ironman. **On** uses the ironman order at the login screen too.
    - Optional shot, not in the repo: the same item with Grand Exchange text marked as a reference.
26. Turn **GE prices** off.
    - Expected: price, margin, and buy-limit lines hide. High alch can remain. Turning it back on loads prices without rebuilding the item list.

## Errors, retry, and layout

27. With **GE prices** off, open an item and wait until its wiki sections appear. Disconnect. Turn **GE prices** on.
    - Expected: the wiki sections stay, because the page was cached. Grand Exchange prices say they are unavailable, with **Retry prices**. The panel does not keep firing requests. Reconnect and press **Retry prices** once. Prices return.
28. Disconnect, then open an item you have not opened this session.
    - Expected: a clear wiki timeout or "could not be reached" message, plus **Retry wiki page**. Sections are not left blank. Pressing Retry while still offline does not loop. Reconnect and press Retry once. The page loads.
    - Optional shot, not in the repo: the wiki-unreachable message and its Retry control.
29. Open a monster from a drop list while offline.
    - Expected: the location view says the wiki could not be reached and offers **Retry**, rather than "No locations are listed."
30. Search for an item the wiki does not have a page for, if you have one in the index.
    - Expected: "The OSRS Wiki has no page for this item." Retry is available and does not repeat on its own.
31. With no search, then search for `rune`, then drag the sidebar narrower and wider. Open Air rune and expand **Shop sources** and the crafting tree.
   - Expected: with no search, the results area does not leave a blank block; Favourites (when you have some) and Recent stay. The search box is the full width of the row. Each filter is its own row, label plus a combo you can read (`Any trade`, not `A...`). Right-click an icon to favourite it. Icon rows fill the width. Shop names wrap, the price is its own column, and **Show on map** is the smaller gold pin action under the name (not another blue shop line). Only the latest shop or monster you pin says to open the world map; the others return to **Show on map**. The map shows that one place (every coordinate it has) and is centred there when you open it. The plugin does not open the map for you. A monster whose page has no coordinates does not offer the link. Ingredient rows show the whole item icon beside the wrapping name and have/need line, including indented nodes. Nothing is cut off. The divider share is still **Detail pane share** after you reopen the config.
32. Change font size, style, and family while an item is open, then narrow the sidebar again.
   - Expected: the sidebar text updates immediately, including collapsed headers and an open drop list, and the lines reflow to the new font and width.
33. Open Air rune, or any item with a long shop list, and do not click the section headers.
   - Expected: the detail pane shows the name, members, weight, owned count, GE buy / sell / margin / limit, and high alch. Shops, uses, drops, crafting, and equipment are one-line headers with a count or short summary. Clicking a header opens the rows already loaded. It does not ask the wiki for the item page again.

## After merge

Plugin Hub submission waits until pull request #8 is on `master`. Follow `PLUGIN_HUB_SUBMISSION.md`. Do not replace `commit=REPLACE_WITH_MASTER_MERGE_COMMIT` until that merge commit exists, and do not open the hub pull request before then.
