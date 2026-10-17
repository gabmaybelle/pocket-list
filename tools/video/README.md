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
