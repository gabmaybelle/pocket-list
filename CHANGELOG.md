# Changelog

## Unreleased

- Pocket List now comes as a NeoForge mod as well as a Fabric one, for Minecraft 26.2 and 26.3. It looks and works the same. On NeoForge, the settings are on the Mods screen's Config button.
- On Minecraft 26.2, the default keys work again: K opens your list and Z pins. They were set to keys that don't exist there, so they had to be set by hand in Controls.

## 0.1.0

The first release.

- A HUD checklist in the top-left corner, with lines turning green as you gather them, and a chime and a note when the whole list is done.
- A list screen (K) to keep several lists, rename them, type amounts, search for any item and clear off what you've gathered.
- Pin an item, or a recipe from the recipe book, with Z. Shift adds enough for a stack.
- Break down: swap a line for its ingredients, minus what you already have, choosing the recipe when there's more than one. In single player it knows every recipe, unlocked or not.
- JEI and REI support: pin from their lists and recipe screens, and use their recipes. Both optional.
- Counts your inventory, carried shulker boxes and bundles, whatever container is open, and chests you've opened nearby. Chest memory is saved per world and per server.
- Settings (from the list screen or Mod Menu): HUD on or off, which corner it sits in, how many lines it shows, and whether nearby chests count. With a minimap mod installed (Xaero's Minimap, JourneyMap or VoxelMap), the list starts bottom-right instead, out of the minimap's corner; your own choice stands once saved.
- A one-time welcome note on first join.
- Client-side only; works on any server.
- Works on Minecraft 26.2 as well as 26.3, in the same jar.
- With Simple HUD Enhanced installed, the list starts in the top-right corner, out of the way of its text. In the top right, the list now sits below your effect icons instead of covering them.
- Your lists and remembered chests are saved safely: if the game crashes while saving, the last good copy is kept rather than a half-written file.
- Blocks that hold things without a screen count once you've used them: shelves, and other mods' storage such as Espresso Flavoured's canisters and glass jars. They show what they hold, so their count stays up to date while you're nearby. Mighty Mail's mailboxes open like chests and are remembered like chests.
