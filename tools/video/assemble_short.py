"""Builds the vertical Short from VideoShort's frames: pocket-list-short.mp4 (game sounds),
pocket-list-short-silent.mp4 (for adding music) and pocket-list-short-cover.png.

python3 -I assemble_short.py <video_short dir> <sounds dir> <out dir>
(sounds from get_sounds.py; set CAPTION_FONT to a heavy font file if none of the usual ones is found)

Captions are short, bold white words with a black outline straight on the footage, one word in the
list's green, in the middle band: clear of the top edge, the Shorts title along the bottom and the
buttons down the right. A small Pocket List badge stays in one place all the way through. There is
no end card: the last scene is back where the first began, so a replay loops.
"""
import csv, os, random, shutil, subprocess, sys
from PIL import Image, ImageDraw, ImageFont

frames_dir, sounds_dir, out = sys.argv[1:4]
FPS = 20
W, H = 1080, 1920
repo = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
GREEN = (124, 252, 106)
# Scene, caption lines ("*word*" is drawn green), and where the caption's top sits. Change a caption
# here if its scene in VideoShort changes. The chest's screen fills the middle, so its caption sits above it.
SCENES = [
    ('hook', ['YOUR *BUILD* LIST', 'TICKS ITSELF OFF'], 820),
    ('list', ['PRESS *K*,', 'ADD ANYTHING'], 860),
    ('breakdown', ['*BREAK DOWN*', 'ANY RECIPE'], 860),
    ('litematica', ['YOUR *SCHEMATIC*,', 'AS A LIST'], 860),
    ('chest', ['COUNTS YOUR', '*CHESTS* TOO'], 300),
    ('loop', None, 0),
]
FONTS = [os.environ.get('CAPTION_FONT', ''), 'C:/Windows/Fonts/ariblk.ttf', '/usr/share/fonts/truetype/msttcorefonts/Arial_Black.ttf',
         '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf']
BADGE_FONTS = [os.environ.get('BADGE_FONT', ''), 'C:/Windows/Fonts/segoeuib.ttf', '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf']

def font(paths, size):
    for p in paths:
        if p and os.path.exists(p):
            return ImageFont.truetype(p, size)
    sys.exit('no caption font found; set CAPTION_FONT')

work = os.path.join(out, 'work')
shutil.rmtree(work, ignore_errors=True)
os.makedirs(work)

def run(*args):
    subprocess.run(args, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)

MAX_WIDTH = 880  # a caption line, so it keeps clear of the buttons down the right

def caption(lines, top):
    """A transparent frame-sized layer with the caption lines, centred; the size shrinks to fit the longest line."""
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    size = 84
    while size > 48 and max(d.textlength(l.replace('*', ''), font=font(FONTS, size)) for l in lines) > MAX_WIDTH:
        size -= 2
    CAPTION = font(FONTS, size)
    y = top
    for line in lines:
        parts = [(p, GREEN if i % 2 else (255, 255, 255)) for i, p in enumerate(line.split('*')) if p]
        x = (W - sum(d.textlength(p, font=CAPTION) for p, _ in parts)) / 2
        for text, colour in parts:
            d.text((x, y), text, font=CAPTION, fill=colour, stroke_width=max(5, size // 12), stroke_fill=(0, 0, 0))
            x += d.textlength(text, font=CAPTION)
        y += int(size * 1.24)
    return layer

def badge():
    """A small mod-list style badge: the icon, the name, the loaders."""
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    box = (300, 1390, 780, 1490)
    d.rounded_rectangle(box, 18, fill=(20, 20, 24, 225))
    icon = Image.open(os.path.join(repo, 'common/src/main/resources/assets/pocket_list/icon.png')).convert('RGBA')
    icon = icon.resize((72, 72), Image.NEAREST)
    layer.alpha_composite(icon, (box[0] + 16, box[1] + 14))
    d.text((box[0] + 104, box[1] + 12), 'Pocket List', font=font(BADGE_FONTS, 40), fill=(255, 255, 255))
    d.text((box[0] + 104, box[1] + 60), 'Fabric · NeoForge · Forge', font=font(BADGE_FONTS, 24), fill=(170, 170, 180))
    return layer

captions = {s: caption(lines, top) for s, lines, top in SCENES if lines}
BADGE = badge()

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

FADE_IN = 4  # frames for a caption to fade in; scenes cut hard
counts = {}
for scene, _, _ in SCENES:
    names = sorted(n for n in os.listdir(frames_dir) if n.startswith(scene + '_') and n.endswith('.png'))
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
        img.alpha_composite(BADGE)
        if cap is not None:
            a = min(1.0, (i + 1) / FADE_IN)
            layer = cap.copy()
            layer.putalpha(layer.getchannel('A').point(lambda v: int(v * a)))
            img.alpha_composite(layer)
        img.convert('RGB').save(os.path.join(dest, '%04d.png' % i))

# The order, and where each piece starts, for the sounds.
clips, starts, t = [], {}, 0.0
for scene, _, _ in SCENES:
    clip = os.path.join(work, scene + '.mp4')
    run('ffmpeg', '-y', '-framerate', str(FPS), '-i', os.path.join(work, scene, '%04d.png'),
        '-vf', 'format=yuv420p', '-r', '30', '-c:v', 'libx264', '-preset', 'slow', '-crf', '18', clip)
    starts[scene] = t
    t += counts[scene] / FPS
    clips.append(clip)
total = t
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
