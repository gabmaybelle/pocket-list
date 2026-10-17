# Pocket List

A little checklist in the corner of your screen for the things you're gathering.

I kept losing track halfway through a build. I'd head out for logs, come back with logs, and realise I'd forgotten the glass. Pocket List keeps the list for you and ticks things off as you collect them.

This is the version for **Minecraft 1.21.1**, on Fabric and NeoForge. It does everything the newer versions do; the few small differences are under [On 1.21.1](#on-1211).

## Getting started

The first time you join a world, a note pops up: press **K** to open your lists. From there you can search for any item by name, type how many you need, and add it. Every item in the game is there, so a list from a video or a guide works on day one, before you've unlocked a single recipe.

You can also add things while you play. Open your inventory or a crafting table and press **Z** over an item to add it, or over a recipe in the recipe book to add its ingredients. Hold **Shift** to add enough for a whole stack.

While you play, the list sits in a corner of the screen (top-left, or another corner if a minimap or HUD mod is using that one) with a count for each line ("24/64 Oak Planks"). Lines turn green once you have enough, and when everything on the list is gathered, you get a little chime and a note to say so.

## Breaking things down

Each line in the list screen has a crafting table button. Click it, and the line is swapped for what it takes to make it: twenty lanterns become iron nuggets and torches. Anything you already have is taken off first, so if you've got five lanterns, you only get the ingredients for fifteen more. When something can be made more than one way (iron ingots from raw iron, nuggets or a block), you pick the recipe you want.

Pocket List finds recipes in the best place it can:

- on 1.21.1, every world and server tells your game all of its recipes when you join, so Pocket List knows every recipe, unlocked or not, in single player and on servers alike;
- with JEI or REI installed, it can also use theirs (for a machine from another mod, say);
- and failing that, the recipes in your recipe book.

## JEI and REI

If you use JEI or REI, the **Z** key works on their item lists, bookmarks or favourites, and recipe screens too. Typing **z** in their search boxes still types. Nothing needs setting up, and Pocket List works just the same without them.

## The list screen

Press **K** to open your lists. From there you can:

- rename the list, start a new one, or switch between them (one for the cottage, one for the farm);
- click an amount and type a new one, or use + and - (with Shift to change by a stack);
- search for an item by name and add it, with the amount box beside the search setting how many;
- break a line down into its ingredients;
- clear off everything you have enough of (Clear Done);
- open the settings.

## Settings

From the list screen's Settings button, or Mod Menu's Config button:

- **HUD:** on or off.
- **HUD Position:** which corner of the screen. It starts top-left, or bottom-right when a minimap mod (Xaero's Minimap, JourneyMap or VoxelMap) is installed, or top-right with Simple HUD Enhanced, whose text fills the top left. In the top-right corner the list sits below any effect icons.
- **HUD Lines:** how many lines it shows (8 by default); the rest are summed up as "and 3 more".
- **Count Nearby Chests:** on or off. This covers the storage blocks without a screen that you've used as well.

Every key can be changed in Controls, under Pocket List. There's also a key to show or hide the HUD, which starts unbound.

## What counts

Your inventory and off-hand count, and so do the shulker boxes and bundles you carry (and bundles inside those; on 1.21.1 bundles are an experiment you turn on when making a world). Whatever is open in front of you counts too: a chest, a barrel, or the crafting grid.

Pocket List also remembers what was in the chests, barrels, shulker boxes, hoppers, dispensers and droppers you've opened, as they were when you closed them. Anything within 32 blocks counts towards your list, so you can stand in your storage room and see what you still need. Ender chests are left out, since their contents are the same everywhere. This memory is kept for each world and each server, so it's still there next time you play.

Some blocks hold things without opening a screen, such as other mods' storage jars and canisters. Once you've used one, it counts too, as far as your game has been told what's inside, and it stays up to date while you're nearby. Storage from other mods that opens like a chest is remembered like a chest.

## Fair play on servers

Pocket List is client-side only. It works on any server, the server doesn't need it, and it never sends anything to the server. It only knows what you've seen yourself: it doesn't scan chests you haven't opened, and it doesn't show ores or blocks through walls.

Everything is saved on your own computer. Your lists are in `config/pocket_list.json`, so the same lists follow you from world to world. Remembered chests are in `config/pocket_list/chests`, one file per world or server address.

## Good to know

- Items are matched by type. A pinned potion counts any potion, and a pinned enchanted book counts any enchanted book.
- Chest memory goes by what was there when you last closed the chest. If a hopper or a friend moves things around, open it again to refresh it.

## On 1.21.1

Everything works here as it does on Minecraft 26.x, with these small differences, all down to the game itself:

- There are no shelves in 1.21.1 (on 26.x they count once used). Other mods' storage blocks that tell your game what they hold still count once used.
- A decorated pot counts what your game was told when you last came near it. Putting things into a pot by hand isn't passed on to players' games in 1.21.1, so a pot you've just filled shows its new contents after you leave and come back.
- Bundles are an experiment in 1.21.1. In a world with them turned on, what's in your bundles counts.
- Break down knows every recipe on servers too, not only in single player (the game sends them all on 1.21.1).

## Requirements

- Minecraft 1.21.1 (for 26.2 and 26.3, see the main page)
- Fabric: Fabric Loader 0.16 or newer and [Fabric API](https://modrinth.com/mod/fabric-api). Optional: [Mod Menu](https://modrinth.com/mod/modmenu), whose Config button opens the settings.
- NeoForge: NeoForge 21.1 or newer. The settings are on the Mods screen's Config button.

Optional on both: [JEI](https://modrinth.com/mod/jei) or [REI](https://modrinth.com/mod/rei). Pick the `fabric` or `neoforge` file for your loader.

## For developers

This is the `1.21.1` branch; `main` is Minecraft 26.x. `./gradlew :fabric:runClientGameTest` starts the game, creates a test world and plays through Pocket List: counting, chest memory, pinning, typed amounts, Break down, the list and settings screens and saving. Fabric's client game test API doesn't exist for 1.21.1, so the test brings a small harness of its own (`fabric/src/gametest/.../harness`). `./gradlew :neoforge:runClientTest` does the NeoForge jar's checks (`neoforge/src/clienttest`). Add `-PtestViewer=jei` or `-PtestViewer=rei` to either to run it with that recipe viewer installed. Screenshots land in `fabric/build/run/clientGameTest/screenshots` and `neoforge/build/run/clientTest/screenshots`. The jars are `fabric/build/libs/pocket-list-fabric-<version>.jar` and `neoforge/build/libs/pocket-list-neoforge-<version>.jar`.

## Licence

[MIT](LICENSE).

Pocket List is made by Gabby with help from Claude, an AI assistant.
