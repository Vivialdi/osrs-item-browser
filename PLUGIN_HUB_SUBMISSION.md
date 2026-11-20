# Plugin Hub submission

This file prepares a submission to [runelite/plugin-hub](https://github.com/runelite/plugin-hub). Do not open that pull request from here. Create it yourself from a fork, after the commit below is the one you want published.

## `plugins/osrs-item-browser`

Create `plugins/osrs-item-browser` in the plugin-hub fork with exactly these two lines:

```
repository=https://github.com/Vivialdi/osrs-item-browser.git
commit=REPLACE_WITH_MASTER_MERGE_COMMIT
```

`commit` is the full 40-character hash of the `master` commit to publish. Replace `REPLACE_WITH_MASTER_MERGE_COMMIT` after this repository's pull request is merged. Do not leave the placeholder in the hub pull request.

## Checklist

- [ ] Merge the pull request that adds owned counts, GE limits, CI, and this file
- [ ] Copy that merge commit's full 40-character hash into `commit=`
- [ ] Replace the Screenshots placeholders in `README.md` with in-game shots of search, a crafting tree with owned counts, the GE limit and margin line, Ironman reference copy, and the bank-not-opened state
- [ ] Confirm `icon.png` is still the original 48×48 mark at the repository root (no Jagex artwork, within the 48×72 hub limit)
- [ ] Fork `runelite/plugin-hub`, add only `plugins/osrs-item-browser` with the text above, and open the hub pull request yourself
- [ ] Read the hub CI log (`build` and Plugin Hub Checks) and, if it asks for changes, push them here and update `commit=`

## Audit

Checked against the current plugin-hub README and `runelite/example-plugin` `AGENTS.md`:

- `runelite-plugin.properties` sets `displayName`, `author`, `description`, `tags`, `plugins`, and `build=standard`. `version` is left empty so the hub uses the commit.
- `LICENSE` is BSD-2-Clause, copyright Vivialdi.
- `icon.png` is a real PNG at the repository root, 48×48, which is inside the 48×72 limit.
- `build.gradle` matches the example-plugin template: Java 11 via `options.release`, RuneLite `latest.release`, Lombok and JUnit only, no extra libraries. This repo also compiles with `-Xlint:deprecation`. Standard hub builds replace `build.gradle` and `settings.gradle`, so that flag does not affect the hub compile.
- There is no `META-INF/services/net.runelite.client.plugins.Plugin` file. The hub loads `com.osr.jei.JEIPlugin` from `plugins=`.
- Config group `osrjei` is unchanged. The GE mapping request reuses the existing **Show GE Prices** toggle, which already warns that it sends your IP to a third-party server. Owned counts do not send any request.
