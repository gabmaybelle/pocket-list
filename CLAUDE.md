# Pocket List

A client-side Fabric and NeoForge mod for Minecraft 26.2 and 26.3, one jar per loader for both (mod ID `pocket_list`): a checklist of what you're
gathering, in a corner of the screen, that counts as you collect. See README.md for what
players get.

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
  `PocketListClientTest`, and `ScreenshotTour`).
- `neoforge/`: `PocketListNeoForge` (NeoForge events, item storage through
  `Capabilities.Item.BLOCK`, the Mods screen's Config button) and `neoforge.mods.toml`. JEI finds
  `JeiPlugin` by its `@JeiPlugin` annotation there; on Fabric through the `jei_mod_plugin` entrypoint.

## Build and test

- `./gradlew build`: jars in `fabric/build/libs/` and `neoforge/build/libs/`.
- `./gradlew :fabric:runClientGameTest` (Fabric only): counting, chest memory, pinning, typed amounts, Break down, the
  list and settings screens and saving. Add `-PtestViewer=jei` or `-PtestViewer=rei` to run it
  again with that recipe viewer installed. Add `-Pminecraft_version=26.2 -Pfabric_api_version=0.161.0+26.2`
  for 26.2 (JEI 30.29 there; REI only on 26.3); add `-Pneoforge_version=26.2.0.93` too so the NeoForge project configures for 26.2.

## Gotchas

- **Fair play is a promise.** Pocket List never sends anything to a server and only counts what
  the player has seen: no scanning unopened chests, no finding blocks through walls. Keep it so.
  Blocks that hold things without a screen (shelves,
  anything with Fabric item storage whose contents the server sends to every
  game) are read through the loader's item storage (`ItemStorage.SIDED` on Fabric, `Capabilities.Item.BLOCK` on NeoForge) from `ChestMemory`, but only after the player has used
  that block, and they're kept live while the player is within 32 blocks. A chest read that way
  shows empty on the player's side, so chests stay on the screen path.
- Other mods, as of 2026-10-15: JEI and REI (pin from their screens, borrow their recipes);
  Mod Menu; the minimap and Simple HUD
  corner defaults; Mighty Mail's mailboxes open a chest menu and are remembered like chests.
  Nightfall Clock sits above the hotbar by default, so the corners are free.
- The HUD starts bottom-right when a minimap mod is installed (Xaero's, JourneyMap, VoxelMap),
  top-right with Simple HUD Enhanced, top-left otherwise. Top right sits below the effect icons.
- **One jar for 26.2 and 26.3.** It's built against 26.3. The few game calls that differ between
  the versions go through `Compat.java` (looked up by name). A method that kept its name but
  changed its return type compiles on both and still crashes on the other, so CI runs
  `tools/check-linkage.js` on the jar against 26.2's game code, and runs the game test on 26.2.

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

**Minecraft.** Fabric on Minecraft 26.3 (Mojang names; Loom 1.18.4, Java 25). Vanilla names
changed a lot in 26.x: check the merged jar rather than older docs (for example `iron_chain`,
`pink_petals[flower_amount=N]`, timelines instead of `time query daytime`).
