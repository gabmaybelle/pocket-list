# Progress

What's done, what's broken, what's next. Newest entry first. Each session adds a row before it
ends, saying what changed, what the game test counted, what the proof run was (the feature off,
which checks fail, code put back), what was learned, and what was deliberately not done.

## Where things stand

- **Done:** Pocket List 0.1.0: the HUD checklist, the list screen, pinning items and recipes, break-down to ingredients, JEI and REI support, chest memory; works on 26.2 and 26.3 in one jar; CI runs the game test on its own, with JEI and with REI. Storage blocks without a screen (shelves, Espresso Flavoured's canisters and jars) count once used, read live through Fabric's item storage.
- **Broken:** Nothing known as of this writing. The game test is timing-sensitive on a slow computer; it now waits for given items to arrive.
- **Next:** Nothing planned beyond feedback; the pack carries v0.1.0, which has the shelves, canisters and jars change. Decorated pots don't tell players' games when an item goes in, so they're left out of the player docs; a pot you used counts only what your game knew at chunk load.

## Log

| Date | Session | What happened |
| --- | --- | --- |
| 2026-10-15 | Notes | **Next** brought up to date: the canisters-and-jars change is committed and in the v0.1.0 release the Espresso Flavoured pack pins. No code change. |
| 2026-10-15 | Map | The one-job-per-mod map (Cozy Core: builder rules; Espresso Flavoured Core: the café; Spawn Gazebo, Nightfall Clock, Pocket List: one thing each; the pack: the bundle) written into CLAUDE.md, the same in every repo. No code change. |
| 2026-10-15 | Other mods | Checked Pocket List against Gabby's other mods. Already fine: JEI borrows Espresso's espresso-machine recipes (its JEI page), Mighty Mail's mailboxes open a chest menu and are remembered, Matcha Companion's clock moved into Nightfall Clock (above the hotbar), so no corner clash. Gap fixed: Espresso's canisters and jars never open a screen, so chest memory never saw them. `ChestMemory` now reads a block you used that opened no screen through `ItemStorage.SIDED` ten ticks later, remembers it when it holds something, and re-reads it live while you're within 32 blocks (vanilla shelves too; chests show empty on the player's side and stay on the screen path). Game test: a shelf check (two ingots placed by hand: 6 counted, 0 with chest counting off; `item replace block` to 3 without using it again: 7; the reopened world still counts 7). Proof run with `rememberStorage` stubbed out: "expected 6, counted 4", code put back. Full test passed (26.3, no viewer); `check-linkage` against 26.2: all 323 references present. Test also made robust: the first count waits for the given items (the server here runs 60+ ticks behind at world start), and a failure is logged before leaving the world, because the test framework deadlocks on close after a failed check and the failure was never printed. Not done: no commit; the 26.2 game test and JEI/REI runs are left to CI. |
| 2026-10-15 | Notes | `PROGRESS.md` added, with the rows below reconstructed from the commit history (one row per day, the commits' own words); the two shared rules (commits one at a time by hand, proof runs for data changes) added to `CLAUDE.md`. No code change. |
| 2026-10-14 | (from history) | Every log line starts with the mod's name; Tidy the list code: one way to top up a line, one way to merge ingredients. |
| 2026-10-13 | (from history) | Top right sits below the effect icons; starts there with Simple HUD; Works on Minecraft 26.2 as well as 26.3, in one jar; Test against JEI 31.12, the version the modpack now carries; CI runs the game test on its own, with JEI and with REI; Lists and remembered chests are saved safely; CI runs the game test on every push; Notes for Claude: how this repo works and the rules every session follows; README: the list starts bottom-right when a minimap is installed. |
| 2026-10-12 | (from history) | Start bottom-right when a minimap mod is installed; A settings screen: HUD corner, lines and chest counting; Work out search results once per query, not every frame; Screenshot tour for the mod page; Pocket List 0.1.0: a gathering checklist for your HUD. |
