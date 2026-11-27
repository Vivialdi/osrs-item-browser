# Plugin Hub submission

The listing lives on a fork of [runelite/plugin-hub](https://github.com/runelite/plugin-hub). Updates use branch `osrs-item-browser` on that fork.

## `plugins/osrs-item-browser`

The file `plugins/osrs-item-browser` (no extension) contains these two lines:

```
repository=https://github.com/Vivialdi/osrs-item-browser.git
commit=b438ba5d847f3bab963aed7182d8f5aa9cfc82d3
```

`commit` is the full 40-character hash of the `master` commit to publish.

## Steps

1. On the fork, check out branch `osrs-item-browser`.
2. Set `commit=` in `plugins/osrs-item-browser` to the full hash of the `master` commit to publish. Change nothing else in the hub repository.
3. Commit and push branch `osrs-item-browser`.
4. Open a pull request against `runelite/plugin-hub` `master`. Describe the plugin in the pull request body.
5. Read the hub CI log (`build`, and Plugin Hub Checks if it asks for changes).

## Packager

The hub CI runs the packager with `build=standard` and `verifyCore` in `package/verification-template`. The signing upload (`upload.jar` with `SIGNING_KEY`) needs the hub's secrets. Run the packager on the `master` commit named in `commit=`.

## Audit

- `runelite-plugin.properties` sets `displayName` (Item Browser), `author`, `description`, `tags`, `plugins`, and `build=standard`. `version` is empty so the hub uses the commit.
- `plugins=com.osr.jei.JEIPlugin`. There is no `META-INF/services` plugin file.
- `LICENSE` is BSD 2-Clause, copyright Vivialdi.
- `icon.png` at the repository root is 48x48, inside the 48x72 and 256 KiB limits.
- The hub standard build replaces `build.gradle` and `settings.gradle`. This repo adds no third-party dependency. Java 11, Lombok, and JUnit are the local toolchain. Compile uses `-Xlint:deprecation`.
- Config group is `osrjei`. **GE prices** is the toggle that warns it sends your IP to a third-party server. Owned counts and the bank-seen flag stay on the client.
- The right-click entry is **Item Browser Lookup** (`MenuAction.RUNELITE`). **Show on map** updates the world-map pins.
