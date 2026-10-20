# Pocket List

A client-side Fabric and Forge mod (mod ID `pocket_list`): a checklist of what you're
gathering, in a corner of the screen, that counts as you collect. See README.md for what
players get. **This is the `1.20.1` branch** (Minecraft 1.20.1, Java 17, versions `<v>+1.20.1`,
Fabric and Forge; NeoForge starts at 1.20.2). `main` is Minecraft 26.2 and 26.3, and `1.21.1` is
1.21.1 on Fabric and NeoForge. A change that belongs on several is made on `main` first and ported
to the older branches by hand; this branch was ported from `1.21.1`.

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
  1.20.1, so `src/gametest/.../harness` is a small stand-in with the same shape (world builder,
  server commands, key presses, typing, mouse and window size through the game's own handlers).
- `forge/`: `PocketListForge` (the `@Mod` class; a dedicated server loads it too, so it only hands
  over to the client class on a client) and `PocketListForgeClient` (Forge events, item storage
  through the block entity's `ForgeCapabilities.ITEM_HANDLER`, the Mods screen's Config button),
  `mods.toml` and `pack.mcmeta`. Built with ModDevGradle's legacy mode, which reobfuscates the jar
  and writes the mixin refmap. JEI finds `JeiPlugin` by its `@JeiPlugin` annotation there; on
  Fabric through the `jei_mod_plugin` entrypoint.

## Build and test

- Gradle runs on JDK 25 and needs a Java 17 for Forge's tools: it finds an installed one or
  downloads one itself (the foojay plugin in `settings.gradle`), so no Java path needs passing. CI
  still points it at the runner's with `-Dorg.gradle.java.installations.fromEnv=JAVA_HOME_17_X64`.
  The code is compiled for Java 17 either way. The first Forge build takes several minutes (Minecraft is set up once).
- `./gradlew build`: jars in `fabric/build/libs/` and `forge/build/libs/`.
- `./gradlew :fabric:runClientGameTest`: counting, chest memory, pinning, typed amounts, Break down, the
  list and settings screens and saving. `./gradlew :forge:runClientTest`: the Forge jar's
  checks (keys, counting, chest memory, a used storage block, the pin key, the screens). Add
  `-PtestViewer=jei` or `-PtestViewer=rei` to either to run it with that recipe viewer installed
  (JEI 15.56.0.205, REI 12.1.785 with Cloth Config 11.1.136 and Architectury 9.2.14).

## Gotchas

- **Fair play is a promise.** Pocket List never sends anything to a server and only counts what
  the player has seen: no scanning unopened chests, no finding blocks through walls. Keep it so.
  Blocks that hold things without a screen (other mods' jars and canisters: anything with item
  storage whose contents the server sends to every game) are read through the loader's item
  storage (`ItemStorage.SIDED` on Fabric, the block entity's `ForgeCapabilities.ITEM_HANDLER` on
  Forge) from `ChestMemory`, but only after the player has used
  that block, and they're kept live while the player is within 32 blocks. A chest read that way
  shows empty on the player's side, so chests stay on the screen path.
- Other mods, as of 2026-10-15: JEI and REI (pin from their screens, borrow their recipes);
  Mod Menu; the minimap and Simple HUD
  corner defaults; Mighty Mail's mailboxes open a chest menu and are remembered like chests.
  Nightfall Clock sits above the hotbar by default, so the corners are free.
- The HUD starts bottom-right when a minimap mod is installed (Xaero's, JourneyMap, VoxelMap),
  top-right with Simple HUD Enhanced, top-left otherwise. Top right sits below the effect icons.
- **What 1.20.1 does differently.** The game ships obfuscated: the code uses Mojang's names; Loom
  remaps the Fabric jar (`officialMojangMappings()`) and ModDevGradle's legacy mode reobfuscates the
  Forge jar (and writes `pocket_list.refmap.json`, named in Forge's copy of the mixin config while
  resources are copied; Fabric's Loom makes its own refmap). There are no data components: what's
  inside a carried shulker box (any block item's `BlockEntityTag.Items`) or bundle (`Items`) is read
  from the item's NBT in `Counter`. There are no recipe holders: `Recipes` reads the client's own
  `RecipeManager`, which 1.20.1 fills with every recipe on any server, and an ingredient's tag comes
  from `Ingredient.toJson()`. Smithing recipes list no ingredients, so
  `SmithingTransformRecipeAccessor` reads their three inputs. Notes in the corner come in a fixed set
  of kinds on 1.20.1 (`SystemToastIds`); Pocket List's use the tutorial hint, which the game itself
  never uses, so nothing replaces them, and `PocketList.toast` stretches the welcome note to 10
  seconds. Screens draw their own backdrop (`renderBackground`), scrolling has one amount, and the
  settings screen lays out its own option list. Java 17: no `getFirst()` or `Math.clamp`.
- **No vanilla block on 1.20.1 holds things without a screen** (no shelves; decorated pots hold
  only sherds), so the used-storage path is only for other mods' storage there. The tests stand a
  chiseled bookshelf in for one: it has item storage on both loaders, and the test puts iron ingots
  into the player's own copy of it (the server never sends a bookshelf's contents). Bundles are an
  experiment, so the tests check them through `Counter.add` on a made-up stack.
- `common` compiles against the recipe viewers' Forge builds, plain compile-only (only the viewers'
  own API names and the game's class names are used from them, the same in every naming). A Fabric
  build remapped there would lack Fabric API on the classpath and come out half-renamed; on 1.21.1
  the Fabric test run reused such a copy from Loom's shared cache and JEI crashed. After changing
  viewer dependencies, delete `.gradle/loom-cache/remapped_mods`.
- REI 12's `TextField` has no `isFocused()` of its own; `ReiViewer` asks the game's
  `GuiEventListener`, which the field is underneath, so the name is remapped on both loaders.
- JEI 15.56's recipe slots may not say their tag; `JeiViewer` asks for it only when JEI has it.
- Loom 1.18.4 (not 1.11, as Nightfall Clock's 1.20.1 branch uses): JEI 15.56's Fabric jar was built
  with Loom 1.17 and older Loom refuses it.

## This mod's job

**Pocket List**: the gathering checklist.

Gabby has other mods, each with its own job. Add only features that fit this mod's job. If an
idea doesn't fit, don't add it here or start it in another repo: ask Gabby where it goes.

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
to any other branch. (Learned on another mod, 2026-10-14, when scripted chains committed
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

**Minecraft.** This branch: Minecraft 1.20.1 (Mojang names through Loom's official mappings;
Loom 1.18.4 run by Java 25, code compiled for Java 17; Forge 47.4 through ModDevGradle's legacy
mode, whose tools need Java 17). Check the game's own classes (Loom's merged jar) rather than newer
docs: much was renamed in 1.20.2 and later.
