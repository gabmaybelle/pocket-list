"""Builds the vertical Short from VideoShort's frames: pocket-list-short.mp4 (game sounds),
pocket-list-short-silent.mp4 (for adding music) and pocket-list-short-cover.png.

python3 -I assemble_short.py <video_short dir> <sounds dir> <out dir>
(set CHROME to a Chrome or Chromium binary if it isn't the default; sounds from get_sounds.py)

Each scene's caption is drawn on the footage below the HUD (which keeps its top-left corner),
clear of the Shorts title and buttons along the bottom and right edge.
"""
import csv, html, os, random, shutil, subprocess, sys
from PIL import Image, ImageDraw

frames_dir, sounds_dir, out = sys.argv[1:4]
FPS = 20
W, H = 1080, 1920
CHROME = os.environ.get('CHROME', '/opt/pw-browsers/chromium-1194/chrome-linux/chrome')
repo = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
# Scene, caption. Change a caption here if its scene in VideoShort changes.
SCENES = [
    ('hook', 'Never forget what you came for'),
    ('list', 'Your gathering list, always on screen'),
    ('breakdown', 'Breaks recipes into raw materials'),
    ('paste', 'Paste a list from your notes'),
    ('litematica', 'Turns a Litematica schematic into your list'),
    ('pin', 'Pin anything with Z'),
    ('chest', 'Remembers your chests'),
    ('outro', None),
]
END_SECONDS = 2.5
work = os.path.join(out, 'work')
shutil.rmtree(work, ignore_errors=True)
os.makedirs(work)

def run(*args):
    subprocess.run(args, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)

STYLE = """
* { margin:0; box-sizing:border-box; }
html,body { width:%dpx; height:%dpx; overflow:hidden; background:transparent; }
body { font-family:'Inter','Segoe UI',sans-serif; display:flex; justify-content:center; align-items:flex-start; }
.cap { margin-top:%dpx; max-width:880px; background:rgba(239,230,210,.94); color:#3f3427; border-radius:28px;
  padding:30px 44px; font-weight:800; font-size:64px; line-height:1.1; letter-spacing:-1px; text-align:center;
  box-shadow:0 14px 40px rgba(0,0,0,.4); }
.end { width:100%%; height:100%%; background:#2b241d; flex-direction:column; align-items:center; justify-content:center; gap:40px; display:flex; }
.end img { width:260px; height:260px; image-rendering:pixelated; }
.end h1 { color:#f3ead7; font-size:110px; font-weight:800; letter-spacing:-2px; }
.end p { color:#e4d8be; font-size:46px; font-weight:600; text-align:center; line-height:1.45; }
.end .g { color:#7bc35f; }
"""

# Where each caption's top sits: below the HUD in the world, and in the empty middle of the list
# screen (whose lines are near the top) for the scenes filmed on it.
SCREEN_SCENES = {'list', 'breakdown', 'paste', 'litematica'}

def shot(name, body, transparent, top=300):
    path = os.path.join(work, name + '.html')
    with open(path, 'w', encoding='utf-8') as f:
        f.write('<!doctype html><html><head><meta charset=utf-8><style>' + STYLE % (W, H, top) + '</style></head><body>' + body + '</body></html>')
    png = os.path.join(work, name + '.png')
    extra = ['--default-background-color=00000000'] if transparent else []
    run(CHROME, '--headless', '--no-sandbox', '--disable-gpu', '--hide-scrollbars', '--force-device-scale-factor=1', *extra,
        f'--window-size={W},{H}', '--screenshot=' + png, 'file:///' + path.replace('\\', '/'))
    return Image.open(png).convert('RGBA')

shutil.copy(os.path.join(repo, 'common/src/main/resources/assets/pocket_list/icon.png'), os.path.join(work, 'icon.png'))
captions = {s: shot('cap_' + s, f'<div class="cap">{html.escape(c)}</div>', True, 860 if s in SCREEN_SCENES else 300)
            for s, c in SCENES if c}
end = shot('end', '<div class="end"><img src="icon.png"><h1>Pocket List</h1>'
           '<p>Fabric, NeoForge and Forge<br>Minecraft 1.20.1 to 26.3<br><span class="g">on CurseForge</span></p></div>', False)
end.convert('RGB').save(os.path.join(work, 'end.png'))

# A Minecraft-ish pixel arrow, as in assemble.py, drawn at 3x for the tall frames.
ARROW = ["2", "22", "212", "2112", "21112", "211112", "2111112", "21111112", "211111112", "2111112222",
         "2112112", "212 2112", "22  2112", "2    2112", "     2112", "      22"]
cursor = Image.new('RGBA', (30, 48), (0, 0, 0, 0))
d = ImageDraw.Draw(cursor)
for y, row in enumerate(ARROW):
    for x, c in enumerate(row):
        if c in '12':
            d.rectangle([x * 3, y * 3, x * 3 + 2, y * 3 + 2], fill=(255, 255, 255, 255) if c == '1' else (0, 0, 0, 255))

with open(os.path.join(frames_dir, 'frames.tsv')) as f:
    cursor_at = {(r[0], int(r[1])): (r[2], r[3]) for r in csv.reader(f, delimiter='\t')}
with open(os.path.join(frames_dir, 'actions.tsv')) as f:
    actions = [(r[0], int(r[1]), r[2], r[3]) for r in csv.reader(f, delimiter='\t')]

FADE_IN = 6  # frames for a caption to fade in
# Scenes cut short: (scene, the action that ends it, frames kept after it). The chest scene's
# turn away looks at whatever is behind the player, so it stops once the chest has closed.
CUTS = {'chest': ('chest_close', 9)}
counts = {}
for scene, _ in SCENES:
    names = sorted(n for n in os.listdir(frames_dir) if n.startswith(scene + '_') and n.endswith('.png'))
    if scene in CUTS:
        kind, keep = CUTS[scene]
        last = next((f for s, f, k, _ in actions if s == scene and k == kind), None)
        if last is not None:
            names = names[:last + keep + 1]
    counts[scene] = len(names)
    dest = os.path.join(work, scene)
    os.makedirs(dest)
    cap = captions.get(scene)
    for i, name in enumerate(names):
        img = Image.open(os.path.join(frames_dir, name)).convert('RGBA')
        sx, sy = W / img.width, H / img.height
        if img.size != (W, H):
            img = img.resize((W, H), Image.LANCZOS)
        x, y = cursor_at.get((scene, i), ('-', '-'))
        if x != '-':
            img.alpha_composite(cursor, (int(int(x) * sx), int(int(y) * sy)))
        if cap is not None:
            a = min(1.0, (i + 1) / FADE_IN)
            layer = cap.copy()
            layer.putalpha(layer.getchannel('A').point(lambda v: int(v * a)))
            img.alpha_composite(layer)
        img.convert('RGB').save(os.path.join(dest, '%04d.png' % i))

# The order, and where each piece starts, for the sounds.
clips, starts, t = [], {}, 0.0
for scene, _ in SCENES:
    clip = os.path.join(work, scene + '.mp4')
    run('ffmpeg', '-y', '-framerate', str(FPS), '-i', os.path.join(work, scene, '%04d.png'),
        '-vf', 'format=yuv420p', '-r', '30', '-c:v', 'libx264', '-preset', 'slow', '-crf', '18', clip)
    starts[scene] = t
    t += counts[scene] / FPS
    clips.append(clip)
end_clip = os.path.join(work, 'end.mp4')
run('ffmpeg', '-y', '-loop', '1', '-t', str(END_SECONDS), '-framerate', str(FPS), '-i', os.path.join(work, 'end.png'),
    '-vf', 'fade=t=in:st=0:d=0.3,format=yuv420p', '-r', '30', '-c:v', 'libx264', '-preset', 'slow', '-crf', '18', end_clip)
clips.append(end_clip)
total = t + END_SECONDS
with open(os.path.join(work, 'list.txt'), 'w') as f:
    f.writelines(f"file '{c.replace(os.sep, '/')}'\n" for c in clips)
silent = os.path.join(out, 'pocket-list-short-silent.mp4')
run('ffmpeg', '-y', '-f', 'concat', '-safe', '0', '-i', os.path.join(work, 'list.txt'), '-c', 'copy', '-movflags', '+faststart', silent)

# The same sound rules as assemble.py: clicks, pops, chests and Pocket List's chime.
random.seed(7)
cues = []
for scene, frame, kind, what in actions:
    if scene not in starts:
        continue
    at = starts[scene] + frame / FPS
    if kind == 'click' and what not in ('searchBox', 'amountBox'):
        cues.append((at, 'click', 1.0, 0.25))
    elif kind == 'pickup':
        cues.append((at, 'pop', ((random.random() - random.random()) * 0.7 + 1.0) * 2.0, 0.2 * 1.6))
    elif kind == 'finished':
        cues.append((at, 'chime', 1.4, 0.6))
    elif kind in ('chest_open', 'chest_close'):
        cues.append((at, kind, 0.95, 0.5))
inputs, filters = [], []
for i, (at, sound, pitch, volume) in enumerate(cues):
    inputs += ['-i', os.path.join(sounds_dir, sound + '.ogg')]
    ms = int(at * 1000)
    filters.append(f'[{i}:a]aformat=sample_rates=44100:channel_layouts=stereo,asetrate={44100 * pitch:.0f},aresample=44100,'
                   f'volume={volume},adelay={ms}|{ms}[s{i}]')
mix = ''.join(f'[s{i}]' for i in range(len(cues)))
filters.append(f'{mix}amix=inputs={len(cues)}:normalize=0,apad,atrim=0:{total:.3f}[a]')
audio = os.path.join(work, 'sound.wav')
run('ffmpeg', '-y', *inputs, '-filter_complex', ';'.join(filters), '-map', '[a]', audio)
run('ffmpeg', '-y', '-i', silent, '-i', audio, '-map', '0:v', '-map', '1:a', '-c:v', 'copy', '-c:a', 'aac', '-b:a', '160k',
    '-shortest', '-movflags', '+faststart', os.path.join(out, 'pocket-list-short.mp4'))

# The cover: the moment the hook's list finishes, with its caption.
finish = next((f for s, f, k, _ in actions if s == 'hook' and k == 'finished'), counts['hook'] - 1)
cover = min(finish + 10, counts['hook'] - 1)
shutil.copy(os.path.join(work, 'hook', '%04d.png' % cover), os.path.join(out, 'pocket-list-short-cover.png'))
print('total seconds', round(total, 2))
