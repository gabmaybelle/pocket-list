# Progress

What's done, what's broken, what's next. Newest entry first. Each session adds a row before it
ends, saying what changed, what the game test counted, what the proof run was (the feature off,
which checks fail, code put back), what was learned, and what was deliberately not done.

## Where things stand

- **Done:** Pocket List 0.1.0: the HUD checklist, the list screen, pinning items and recipes, break-down to ingredients, JEI and REI support, chest memory; works on 26.2 and 26.3 in one jar; CI runs the game test on its own, with JEI and with REI.
- **Broken:** Nothing known as of this writing (not re-verified).
- **Next:** Nothing planned beyond feedback; the modpacks carry it.

## Log

| Date | Session | What happened |
| --- | --- | --- |
| 2026-10-15 | Notes | `PROGRESS.md` added, with the rows below reconstructed from the commit history (one row per day, the commits' own words); the two shared rules (commits one at a time by hand, proof runs for data changes) added to `CLAUDE.md`. No code change. |
| 2026-10-14 | (from history) | Every log line starts with the mod's name; Tidy the list code: one way to top up a line, one way to merge ingredients. |
| 2026-10-13 | (from history) | Top right sits below the effect icons; starts there with Simple HUD; Works on Minecraft 26.2 as well as 26.3, in one jar; Test against JEI 31.12, the version the modpack now carries; CI runs the game test on its own, with JEI and with REI; Lists and remembered chests are saved safely; CI runs the game test on every push; Notes for Claude: how this repo works and the rules every session follows; README: the list starts bottom-right when a minimap is installed. |
| 2026-10-12 | (from history) | Start bottom-right when a minimap mod is installed; A settings screen: HUD corner, lines and chest counting; Work out search results once per query, not every frame; Screenshot tour for the mod page; Pocket List 0.1.0: a gathering checklist for your HUD. |
