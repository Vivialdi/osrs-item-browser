# Plugin Hub submission

Submit this plugin to [runelite/plugin-hub](https://github.com/runelite/plugin-hub) only after pull request #8 is merged to `master`. Do not open, fork, or push that hub repository from an automated agent. A person creates the fork and the hub pull request.

The hub descriptor below is ready to copy. Leave `commit=REPLACE_WITH_MASTER_MERGE_COMMIT` in this file until `master` has the merge. The hub pull request must use the real 40-character hash, not the placeholder.

## `plugins/osrs-item-browser`

Create a file named `plugins/osrs-item-browser` (no extension) containing exactly these two lines:

```
repository=https://github.com/Vivialdi/osrs-item-browser.git
commit=REPLACE_WITH_MASTER_MERGE_COMMIT
```

`commit` is the full 40-character hash of the commit that landed on `master` when pull request #8 was merged. If GitHub squash-merges, that squash commit is the one to use. If GitHub creates a merge commit, use that merge commit. Copy it from the `master` history after the merge. Do not use a commit that exists only on the pull request branch.

## Steps

1. Merge pull request #8 (`cursor/compact-detail-pane-7325`) into `master`. Do not merge it from this file, and do not open a hub pull request first.
2. On GitHub, open the `master` commit that the merge produced and copy its full 40-character hash.
3. Fork `https://github.com/runelite/plugin-hub` into your own account. Create a branch from that fork's `master`.
4. Add `plugins/osrs-item-browser` with the two lines above, and replace `REPLACE_WITH_MASTER_MERGE_COMMIT` with the hash from step 2. Change nothing else in the hub repository.
5. Commit, push the branch to your fork, and open a pull request against `runelite/plugin-hub` `master`. Describe the plugin in the pull request body.
6. Read the hub CI log (`build`, and Plugin Hub Checks if it asks for changes). If it fails, fix the plugin in this repository, merge that fix to `master`, and update `commit=` on the hub pull request to the new `master` hash.

## What was already checked locally

A local run of the plugin-hub v6 packager, against this repository, built the jar with `build=standard`, recorded the API surface, and accepted the icon and `runelite-plugin.properties`. `verifyCore` in `package/verification-template` passed. The signing upload (`upload.jar` with `SIGNING_KEY`) cannot be run without the hub's secrets. Re-run is not required before the steps above unless the `master` commit differs from the commit that was packaged.

## Audit

- `runelite-plugin.properties` sets `displayName` (OSRS Item Browser), `author`, `description`, `tags`, `plugins`, and `build=standard`. `version` is empty so the hub uses the commit.
- `plugins=com.osr.jei.JEIPlugin`. There is no `META-INF/services` plugin file.
- `LICENSE` is BSD 2-Clause, copyright Vivialdi.
- `icon.png` at the repository root is 48x48, inside the 48x72 and 256 KiB limits.
- The hub standard build replaces `build.gradle` and `settings.gradle`. This repo adds no third-party dependency. Java 11, Lombok, and JUnit are the local toolchain. Compile uses `-Xlint:deprecation`.
- Config group `osrjei` and its keys are unchanged. **GE prices** is the toggle that warns it sends your IP to a third-party server. Owned counts and the bank-seen flag do not send a request.
- The right-click entry is **Item Browser Lookup** (`MenuAction.RUNELITE` only). **Show on map** does not call `menuAction`.
