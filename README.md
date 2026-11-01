# Pocket List

A little checklist in the corner of your screen that ticks itself off as you gather.

Client-side, for Fabric and NeoForge on Minecraft 26.2, 26.3 and 1.21.1, and Fabric and Forge on 1.20.1, plus a plugin for Paper servers on 26.3. Download it from [CurseForge](https://www.curseforge.com/minecraft/mc-mods/pocket-list).

▶ [See it in action: a 36-second video](https://www.youtube.com/shorts/l2mbjTvYLyE)

I kept losing track halfway through a build. I'd head out for logs, come back with logs, and realize I'd forgotten the glass. Pocket List keeps the list for you.

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
- paste a list in, or copy this one out (see below);
- open the settings.

## Lists as text

**Paste List** makes a new list from text you've copied, one item per line with how many: "64 oak planks", "Torches x16", "minecraft:glass_pane 8" or "#minecraft:logs 10" for any log. A list from your notes, a guide or a video description works as it is. Lines that aren't an item are named in a message, never guessed.

**Copy List** copies the list as text, one line per item, to keep in your notes or send to a friend for their own Pocket List.

**From Litematica** (shown when Litematica is installed): in Litematica, open a schematic's material list and press Write to file, then click From Litematica. Pocket List makes a new list named after the schematic, with each item's total, and counts your inventory and chests against it as usual. Break down still works on every line.

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

Some blocks hold things without opening a screen: shelves, and other mods' storage blocks. Once you've used one, it counts too, and since those blocks show what they hold, the count stays up to date while you're nearby, even when a hopper does the filling. Mailboxes from Mighty Mail open like chests, so they're remembered like chests.

## Fair play on servers

Pocket List is client-side only. It works on any server, the server doesn't need it, and it never sends anything to the server. It only knows what you've seen yourself: it doesn't scan chests you haven't opened, and it doesn't show ores or blocks through walls.

Everything is saved on your own computer. Your lists are in `config/pocket_list.json`, so the same lists follow you from world to world. Remembered chests are in `config/pocket_list/chests`, one file per world or server address.

## Good to know

- Items are matched by type. A pinned potion counts any potion, and a pinned enchanted book counts any enchanted book.
- On someone else's server without JEI or REI, Break down and recipe pinning only know the recipes you've unlocked. Adding items by name always works.
- Chest memory goes by what was there when you last closed the chest. If a hopper or a friend moves things around, open it again to refresh it. (Shelves and canisters keep themselves up to date.)

## On a Paper server

The `paper` file is a plugin for [Paper](https://papermc.io/) 26.3 (Java 25). Drop it in the server's `plugins` folder and every player gets a gathering list, with nothing to install. A server can't draw in the corner of your screen or listen for your keys, so the plugin works a little differently:

- The list shows as a sidebar on the right: "Cabin  2/5" in gold, then lines like "24/64 Oak Planks", green once you have enough, with the ones still to gather first. When everything is gathered, the title turns green, a chime plays and "All gathered!" shows on screen.
- `/pocket` (or `/pocketlist`) shows the list in chat with buttons: [-] [+] [+64] [break down] [x] on each line, and Clear done, New list, Copy and Delete list below.
- `/pocket add oak_log 32` adds an item (Tab lists every item), `/pocket add hand` adds what you're holding, and `/pocket add recipe lantern` adds what a lantern takes. Add `stack` for a whole stack.
- `/pocket breakdown lantern` swaps a line for its ingredients using the server's own recipes, data packs included, and asks you to pick when there's more than one.
- `/pocket new`, `use`, `next`, `rename` and `delete` handle several lists; `/pocket hud off` hides the sidebar and `/pocket hud lines 5` shows five lines. `/pocket help` lists everything.
- It counts your inventory and off-hand, the shulker boxes and bundles you carry, and whatever container is open. It doesn't remember chests yet.
- Your lists are kept on the server with your player, so they're there next time you join (but not on other servers).
- The sidebar is one spot shared with other plugins, such as a server's stats board. Pocket List uses it only while your list has something on it, and hands it back when the list is empty or you turn it off.
- Not in the plugin: the corner HUD, keys, pinning from inventory screens and the recipe book, JEI and REI, chest memory, Paste List and From Litematica, and the settings screen. If you also run the mod, you'll see both lists; `/pocket hud off` hides the plugin's.

## Requirements

- Minecraft 26.2 or 26.3 (one file per loader works on both)
- Fabric: Fabric Loader 0.19.5 or newer and [Fabric API](https://modrinth.com/mod/fabric-api). Optional: [Mod Menu](https://modrinth.com/mod/modmenu), whose Config button opens the settings.
- NeoForge: NeoForge 26.2 or newer. The settings are on the Mods screen's Config button.

Optional on both: [JEI](https://modrinth.com/mod/jei) or [REI](https://modrinth.com/mod/rei). Pick the `fabric` or `neoforge` file for your loader.

**Paper servers:** the `paper` file is a plugin for [Paper](https://papermc.io/) 26.3 (Java 25). Drop it in the server's `plugins` folder; players need nothing installed. See "On a Paper server" above for what it does and doesn't do.

## For developers

`./gradlew :fabric:runClientGameTest` starts the game, creates a test world and plays through Pocket List: counting, chest memory, pinning, typed amounts, Break down, the list screen and saving. Add `-PtestViewer=jei` or `-PtestViewer=rei` to run it again with that recipe viewer installed, and `-Pminecraft_version=26.2 -Pfabric_api_version=0.161.0+26.2` to run it on 26.2 (JEI only there). `node tools/check-linkage.js <jar> <26.2 game jar>` checks the jar, which is built against 26.3, only calls game code 26.2 has too. Screenshots land in `fabric/build/run/clientGameTest/screenshots`. The jars are `fabric/build/libs/pocket-list-fabric-<version>.jar` and `neoforge/build/libs/pocket-list-neoforge-<version>.jar`; there is no automated game test for the NeoForge jar yet.

The Paper plugin is the `paper` subproject. `./gradlew :paper:test` runs its unit tests, and `node tools/paper-server-test.js <folder> --plugin paper/build/libs/pocket-list-paper-<version>.jar --eula` starts a real Paper server with no window and runs `/pocket selftest` against the server's recipes (`--eula` says you accept the Minecraft server EULA).

## Licence

[MIT](LICENSE).

Pocket List is made by Gabby.
