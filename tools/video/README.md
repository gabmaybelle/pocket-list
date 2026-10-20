# The mod-page video

Makes `pocket-list.mp4` (with the game's own sounds), `pocket-list-silent.mp4` (for adding music)
and one GIF per scene. Needs ffmpeg, Python 3 with Pillow, and Chrome or Chromium.

1. Film it: `./gradlew :fabric:runClientGameTest -PvideoTour` (on Linux without a screen, under
   `xvfb-run -a` with `SDL_VIDEO_FORCE_EGL=1`). `VideoTour` saves a frame per tick, `actions.tsv` and
   `frames.tsv` in `fabric/build/run/clientGameTest/video_tour`. It takes about eight minutes.
   The next game-test run clears that folder, so copy it somewhere first if you want to keep it.
2. `python3 -I tools/video/make_cards.py <cards dir>`: the title, caption and end cards.
3. `python3 -I tools/video/get_sounds.py <sounds dir>`: the sounds, from the game's asset cache.
4. `python3 -I tools/video/assemble.py <video_tour folder> <cards dir> <sounds dir> <out dir>`

Keep the frames, sounds and videos out of the repo. If a scene changes, change its caption in
`make_cards.py` too; the paste card shows the same lines as `VideoTour.NOTES`.

## The Short: a builder's story

`VideoShort3` films a 1080x1920 Short in third person with the `Film` helper (a frame per tick,
logs written even when a scene fails, a scout pass that saves each scene's first and last frames).
It needs a saved world with a cottage and a timber house and their spots kept inside it
(`-PvideoWorld=<zip>`); `-PvideoSkin=<png>` dresses the player in a slim-armed skin. Three scenes,
filmed build first because the jumble clears the cottage away:

- `s_jumble`: on the empty lot, an inventory of odd stacks; it closes and the list shows the
  cottage's real block counts against what's carried.
- `s_gather`: at the timber house, a storage chest fills most lines, two logs are chopped and two
  lanterns picked up, and the list finishes.
- `s_build`: the finished cottage, everything gathered, the camera easing back to the jumble's framing.

1. Film it: `./gradlew :fabric:runClientGameTest -PvideoShort3 -PvideoWorld=<zip> [-PvideoSkin=<png>]`
   (`-PvideoScout` for stills only, `-PvideoScenes=s_gather` for one scene). Frames land in
   `fabric/build/run/clientGameTest/video_short3`.
2. `python3 -I tools/video/get_sounds.py <sounds dir>`
3. `python3 -I tools/video/assemble_short3.py <video_short3 folder> <sounds dir> <out dir> [--split]`:
   without `--split` it opens on the inventory, then the list; with it, the first seconds show both
   at once, stacked.
4. `python3 -I tools/video/add_music.py <video> <track> <out> [start seconds]`: a track from
   YouTube's Audio Library under the game sounds, the mix at about -14 LUFS.
