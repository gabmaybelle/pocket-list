# Pocket List

A little checklist in the corner of your screen for the things you're gathering.

I kept losing track halfway through a build. I'd head out for logs, come back with logs, and realise I'd forgotten the glass. Pocket List keeps the list for you and ticks things off as you collect them.

## Getting started

The first time you join a world, a note pops up: press **K** to open your lists. From there you can search for any item by name, type how many you need, and add it. Every item in the game is there, so a list from a video or a guide works on day one, before you've unlocked a single recipe.

You can also add things while you play. Open your inventory or a crafting table and press **Z** over an item to add it, or over a recipe in the recipe book to add its ingredients. Hold **Shift** to add enough for a whole stack.

While you play, the list sits in a corner of the screen (top-left, or another corner if a minimap or HUD mod is using that one) with a count for each line ("24/64 Oak Planks"). Lines turn green once you have enough, and when everything on the list is gathered, you get a little chime and a note to say so.

## Breaking things down

Each line in the list screen has a crafting table button. Click it, and the line is swapped for what it takes to make it: twenty lanterns become iron nuggets and torches. Anything you already have is taken off first, so if you've got five lanterns, you only get the ingredients for fifteen more. When something can be made more than one way (iron ingots from raw iron, nuggets or a block), you pick the recipe you want.

Pocket List finds recipes in the best place it can:

- in single player, or a world you're hosting for friends, it knows every recipe in the game, unlocked or not;
- with JEI or REI installed, it uses their recipes;
- otherwise, it uses the recipes you've unlocked in your recipe book.

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
- **Count Nearby Chests:** on or off. This covers the shelves and canisters you've used as well.

Every key can be changed in Controls, under Pocket List. There's also a key to show or hide the HUD, which starts unbound.

## What counts

Your inventory and off-hand count, and so do the shulker boxes and bundles you carry (and bundles inside those). Whatever is open in front of you counts too: a chest, a barrel, or the crafting grid.

Pocket List also remembers what was in the chests, barrels, shulker boxes, hoppers, dispensers and droppers you've opened, as they were when you closed them. Anything within 32 blocks counts towards your list, so you can stand in your storage room and see what you still need. Ender chests are left out, since their contents are the same everywhere. This memory is kept for each world and each server, so it's still there next time you play.

Some blocks hold things without opening a screen: shelves, and other mods' storage such as Espresso Flavoured's canisters and glass jars. Once you've used one, it counts too, and since those blocks show what they hold, the count stays up to date while you're nearby, even when a hopper does the filling. Mailboxes from Mighty Mail open like chests, so they're remembered like chests.

## Fair play on servers

Pocket List is client-side only. It works on any server, the server doesn't need it, and it never sends anything to the server. It only knows what you've seen yourself: it doesn't scan chests you haven't opened, and it doesn't show ores or blocks through walls.

Everything is saved on your own computer. Your lists are in `config/pocket_list.json`, so the same lists follow you from world to world. Remembered chests are in `config/pocket_list/chests`, one file per world or server address.

## Good to know

- Items are matched by type. A pinned potion counts any potion, and a pinned enchanted book counts any enchanted book.
- On someone else's server without JEI or REI, Break down and recipe pinning only know the recipes you've unlocked. Adding items by name always works.
- Chest memory goes by what was there when you last closed the chest. If a hopper or a friend moves things around, open it again to refresh it. (Shelves and canisters keep themselves up to date.)

## Requirements

- Minecraft 26.2 or 26.3 (one jar works on both)
- Fabric Loader 0.19.5 or newer
- [Fabric API](https://modrinth.com/mod/fabric-api)

Optional: [Mod Menu](https://modrinth.com/mod/modmenu) (its Config button opens the settings), [JEI](https://modrinth.com/mod/jei) or [REI](https://modrinth.com/mod/rei).

## For developers

`./gradlew runClientGameTest` starts the game, creates a test world and plays through Pocket List: counting, chest memory, pinning, typed amounts, Break down, the list screen and saving. Add `-PtestViewer=jei` or `-PtestViewer=rei` to run it again with that recipe viewer installed, and `-Pminecraft_version=26.2 -Pfabric_api_version=0.161.0+26.2` to run it on 26.2 (JEI only there). `node tools/check-linkage.js <jar> <26.2 game jar>` checks the jar, which is built against 26.3, only calls game code 26.2 has too. Screenshots land in `build/run/clientGameTest/screenshots`.

## Licence

[MIT](LICENSE).

Pocket List is made by Gabby with help from Claude, an AI assistant.
