# Pocket List

A client-side Fabric and NeoForge mod (mod ID `pocket_list`): a checklist of what you're
gathering, in a corner of the screen, that counts as you collect. See README.md for what
players get. **This is the `1.21.1` branch** (Minecraft 1.21.1, Java 21, versions `<v>+1.21.1`);
`main` is Minecraft 26.2 and 26.3. A change that belongs on both is made on `main` first and
ported here by hand.

`PROGRESS.md` says what's done, what's broken and what's next, with a row per session: read
it first, and add this session's row before ending.

## Layout

- `common/src/main/java/com/gabbymaybelle/pocketlist/`: `Lists.java` (the lists and saving them),
  the list and settings screens, counting (inventory, open containers, remembered chests),
  recipe lookup, and the JEI and REI hooks (optional, compile-only). `Platform` is what the
  loader answers (config folder, installed mods, a block's item storage); `PocketList` and
  `ChestMemory` have plain handlers each loader's events call.
- `fabric/`: `PocketListFabric` (Fabric API events, item storage through `ItemStorage.SIDED`),
  `ModMenuIntegration`, `fabric.mod.json`, and `src/gametest/` (the client game test,
  `PocketListClientTest`, and `ScreenshotTour`). Fabric's client game test API doesn't exist for
  1.21.1, so `src/gametest/.../harness` is a small stand-in with the same shape (world builder,
  server commands, key presses, typing, mouse and window size through the game's own handlers).
- `neoforge/`: `PocketListNeoForge` (NeoForge events, item storage through
  `Capabilities.Item.BLOCK`, the Mods screen's Config button) and `neoforge.mods.toml`. JEI finds
  `JeiPlugin` by its `@JeiPlugin` annotation there; on Fabric through the `jei_mod_plugin` entrypoint.

## Build and test

- `./gradlew build`: jars in `fabric/build/libs/` and `neoforge/build/libs/`.
- `./gradlew :fabric:runClientGameTest`: counting, chest memory, pinning, typed amounts, Break down, the
  list and settings screens and saving. `./gradlew :neoforge:runClientTest`: the NeoForge jar's
  checks (keys, counting, chest memory, a used storage block, the pin key, the screens). Add
  `-PtestViewer=jei` or `-PtestViewer=rei` to either to run it with that recipe viewer installed
  (JEI 19.27.0.346, REI 16.0.799 with Cloth Config and Architectury).

## Gotchas

- **Fair play is a promise.** Pocket List never sends anything to a server and only counts what
  the player has seen: no scanning unopened chests, no finding blocks through walls. Keep it so.
  Blocks that hold things without a screen (decorated pots, other mods' jars and canisters:
  anything with item storage whose contents the server sends to every game) are read through the
  loader's item storage (`ItemStorage.SIDED` on Fabric, `Capabilities.ItemHandler.BLOCK` on
  NeoForge) from `ChestMemory`, but only after the player has used
  that block, and they're kept live while the player is within 32 blocks. A chest read that way
  shows empty on the player's side, so chests stay on the screen path.
- Other mods, as of 2026-10-15: JEI and REI (pin from their screens, borrow their recipes, which
  covers Espresso's espresso machine through its JEI page); Mod Menu; the minimap and Simple HUD
  corner defaults; Mighty Mail's mailboxes open a chest menu and are remembered like chests.
  Nightfall Clock sits above the hotbar by default, so the corners are free.
- The HUD starts bottom-right when a minimap mod is installed (Xaero's, JourneyMap, VoxelMap),
  top-right with Simple HUD Enhanced, top-left otherwise. Top right sits below the effect icons.
- The Espresso Flavoured modpack carries a copy of this jar in its `client-overrides/mods/`.
- **What 1.21.1 does differently.** The game ships obfuscated: the code uses Mojang's names and
  Loom remaps the Fabric jar (`fabric-loom` with `officialMojangMappings()`; NeoForge runs on
  Mojang names already). There are no recipe displays: `Recipes` reads the client's own
  `RecipeManager`, which 1.21.1 fills with every recipe on any server, and an ingredient's tag is
  read by writing it out with `Ingredient.CODEC_NONEMPTY` (the game keeps it private). Smithing
  recipes list no ingredients, so `SmithingTransformRecipeAccessor` reads their three inputs.
  There are no shelves: the used-storage test uses a decorated pot, whose contents reach the
  player's game at chunk load or from `data merge`, not when filled by hand. Bundles are an
  experiment, so the tests check them through `Counter.add` on a made-up stack.
- `common` compiles against the recipe viewers' NeoForge builds (Mojang names, nothing to remap).
  Remapped there, a Fabric build lacked Fabric API on the classpath, came out half-renamed, and the
  Fabric test run reused that copy from Loom's shared cache (JEI crashed with an AbstractMethodError
  on `reload`). After changing viewer dependencies, delete `.gradle/loom-cache/remapped_mods`.
- JEI 19.27 has no `IRecipeSlotView.getTagKey()`; `JeiViewer` calls it when a newer JEI has it.

## Gabby's mods: who does what (the same in every repo)

One job per mod, decided 2026-10-15, so nothing is done twice and each repo tells the same story:

- **Cozy Core** (cozy-core): the builder rules, each an ordinary game rule: builds stay built (mob
  explosions, endermen, fire), no phantoms, keep your hotbar on death, the Hearth Lantern, cheap
  wood in the stonecutter, re-dyeing and builder trades. Fabric and NeoForge jars, plus a plain
  data pack of the recipes.
- **Espresso Flavoured Core** (espresso-flavoured): the café: coffee you grow and brew, mugs,
  baristas, Bean Coins, the café charms, and Drowsy (coffee's reason to exist). Longer days stay a
  Core setting for now. The survival rules Cozy Core now covers left Core in 0.4.0 (its PLAN-0.4.0.md).
- **Spawn Gazebo** (spawn-gazebo): the gazebo at the world spawn and its starter chest.
- **Nightfall Clock** (nightfall-clock): the clock above the hotbar and the dusk warning.
- **Pocket List** (pocket-list): the gathering checklist.
- **Not Now, Shield** (not-now-shield): the shield drawn lower in first person; on 26.x also
  tools that change blocks while a shield is in the off-hand.
- **Beyond the Dragon** (beyond-the-dragon): the end-game after the Ender Dragon: ruins at the
  edge of the End, a dragon that comes back harder, and the aphelite metal.
- **Forgemark** (forgemark): tempering the tools, weapons and armour you already have, from any
  mod, for a small capped quality bonus.
- **Espresso Flavoured** (espresso-flavoured-pack): the modpack that puts them together. Since
  pack 0.5.0 (2026-10-15) it carries all five: Cozy Core, Espresso Flavoured Core (back as the café
  once it stopped doing Cozy Core's jobs), Spawn Gazebo, Nightfall Clock and Pocket List.
- **Cozy Core Pack** (cozy-core-pack): the builder-friendly modpack around Cozy Core, with Spawn
  Gazebo, Nightfall Clock, Pocket List and Not Now, Shield.

A new feature goes in the mod whose job it is. If none fits, ask Gabby before adding it anywhere.

## Working on Gabby's mods (the same in every repo)

Gabby makes these mods with help from Claude. Several Claude sessions work on them: one on her
computer and others in the cloud. These rules keep their work fitting together.

**One session per repo at a time.**
- A cloud session works on a `claude/...` branch and never pushes to `main`. Gabby's local
  session reviews the branch, runs its tests and merges it as one commit.
- Start from the latest `main` (`git pull`), and say in the branch's last commit or a short note
  what is finished and what isn't.

**Commits are made one at a time, by hand.** Never commit or push from a chained script. Run
the test, read its result (and the screenshot, for anything visual), then commit that one
branch, then push it. A cherry-pick that conflicts stops the work until it is resolved by hand.
A worldgen or balance change is tested on every terrain in the branch's matrix before it goes
to any other branch. (Learned on Beyond the Dragon, 2026-10-14, when scripted chains committed
a conflicted cherry-pick, put one branch's files on another, and sent an untested ore change to
all three branches.) For a Gradle game test use a game watcher that reports a verdict (in the world, error screen, crashed or hung),
not a wait loop.

**Commits.**
- Author and committer are always `gabmaybelle <211247938+gabmaybelle@users.noreply.github.com>`:
  `git -c user.name=gabmaybelle -c user.email=211247938+gabmaybelle@users.noreply.github.com commit ...`.
  Never a personal email, and never `Claude <noreply@anthropic.com>` as the author. Claude goes in
  a `Co-Authored-By:` line at the end of the message.
- Ask Gabby before committing or pushing when she's around. Finish the build and the tests first.
- Commit messages say what changed for players, in plain sentences.

**Privacy.** These repos are public. Don't add personal email addresses, real names other than
"Gabby", or anything about her other private projects and modpacks: not in code, docs, commits
or test names. Check the diff for these before every push.

**Tests.**
- `./gradlew build` and `./gradlew runClientGameTest` (where the repo has one) must pass before
  a commit. The game test opens a real game window for a minute or two.
- A new or changed check should fail with the feature switched off: run it that way once, then
  put the code back. A check that compares against the setting it tests proves nothing.
- A data change (worldgen, loot, a template, a pack's mod list) gets the same proof: run the part
  of the test that sees it, and quote the numbers in the progress row.
- On Linux without a screen, run game tests under `xvfb-run -a`. 26.3 also needs
  `SDL_VIDEO_FORCE_EGL=1` with `libegl1 libegl-mesa0` installed, or the game hangs at startup.

**Writing.**
- README and CHANGELOG are for players: plain, friendly, no jargon. New player-facing changes
  go in the CHANGELOG's newest section.
- Keep the credit line: "made by Gabby with help from Claude, an AI assistant".
- Gabby publishes on Modrinth herself. Don't upload anything there.

**Minecraft.** This branch: Minecraft 1.21.1 (Mojang names through Loom's official mappings;
Loom 1.18.4 run by Java 25, code compiled for Java 21; NeoForge 21.1 through ModDevGradle). Check
the game's own classes (Loom's merged jar) rather than newer docs: much was renamed in 26.x.
