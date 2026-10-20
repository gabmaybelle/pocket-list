"""Builds the third cut of the vertical Short from VideoShort3's frames: a builder's story.

python3 -I assemble_short3.py <video_short3 dir> <sounds dir> <out dir> [--split]
(sounds from get_sounds.py; set CAPTION_FONT to a heavy font file if none of the usual ones is found)

Makes pocket-list-short.mp4 (the game's own sounds), pocket-list-short-silent.mp4 (for adding
music) and pocket-list-short-cover.png, 1080x1920. The order: an inventory of odd stacks on an
empty lot ("180 PLANKS = ___ STACKS?"), the list in the corner that says exactly, gathering the
rest at the timber house, then the cottage built, with "follow for more cozy mods".

With --split the opening stacks the two halves of the frame instead: the inventory on top under
the question, the list on the empty lot below (with Pocket List), at the same time. Each output name then
ends in -split.

Captions are bold white capitals with a black outline straight on the footage, one word in the
list's green, clear of the top edge, the Shorts title along the bottom and the buttons down the
right. A small Pocket List badge stays in one place all the way through. If a scene changes in
VideoShort3, change its captions here too.
"""
import csv, os, random, shutil, subprocess, sys
from PIL import Image, ImageDraw, ImageFont

args = [a for a in sys.argv[1:] if not a.startswith('--')]
SPLIT = '--split' in sys.argv
frames_dir, sounds_dir, out = args[:3]
FPS = 20
W, H = 1080, 1920
NAME = 'pocket-list-short' + ('-split' if SPLIT else '')
repo = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
GREEN = (124, 252, 106)
WHITE = (255, 255, 255)

FONTS = [os.environ.get('CAPTION_FONT', ''), 'C:/Windows/Fonts/ariblk.ttf', '/usr/share/fonts/truetype/msttcorefonts/Arial_Black.ttf',
         '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf']
BADGE_FONTS = [os.environ.get('BADGE_FONT', ''), 'C:/Windows/Fonts/segoeuib.ttf', '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf']


def font(paths, size):
    for p in paths:
        if p and os.path.exists(p):
            return ImageFont.truetype(p, size)
    sys.exit('no caption font found; set CAPTION_FONT')


def run(*cmd):
    subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)


MAX_WIDTH = 880  # a caption line, so it keeps clear of the buttons down the right


def caption(lines, top, size=84, width=W, height=H):
    """A transparent layer with the caption lines, centred ("*word*" in green); shrinks to fit."""
    layer = Image.new('RGBA', (width, height), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    while size > 40 and max(d.textlength(l.replace('*', ''), font=font(FONTS, size)) for l in lines) > min(MAX_WIDTH, width - 120):
        size -= 2
    f = font(FONTS, size)
    y = top
    for line in lines:
        parts = [(p, GREEN if i % 2 else WHITE) for i, p in enumerate(line.split('*')) if p]
        x = (width - sum(d.textlength(p, font=f) for p, _ in parts)) / 2
        for text, colour in parts:
            d.text((x, y), text, font=f, fill=colour, stroke_width=max(5, size // 12), stroke_fill=(0, 0, 0))
            x += d.textlength(text, font=f)
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
    d.text((box[0] + 104, box[1] + 12), 'Pocket List', font=font(BADGE_FONTS, 40), fill=WHITE)
    d.text((box[0] + 104, box[1] + 60), 'Fabric · NeoForge · Forge', font=font(BADGE_FONTS, 24), fill=(170, 170, 180))
    return layer


BADGE = badge()

# A Minecraft-ish pixel arrow for the cursor (screenshots show none), drawn at 3x.
ARROW = ["2", "22", "212", "2112", "21112", "211112", "2111112", "21111112", "211111112", "2111112222",
         "2112112", "212 2112", "22  2112", "2    2112", "     2112", "      22"]
cursor = Image.new('RGBA', (30, 48), (0, 0, 0, 0))
d = ImageDraw.Draw(cursor)
for y, row in enumerate(ARROW):
    for x, c in enumerate(row):
        if c in '12':
            d.rectangle([x * 3, y * 3, x * 3 + 2, y * 3 + 2], fill=WHITE + (255,) if c == '1' else (0, 0, 0, 255))

with open(os.path.join(frames_dir, 'frames.tsv')) as f:
    cursor_at = {(r[0], int(r[1])): (r[2], r[3]) for r in csv.reader(f, delimiter='\t') if len(r) > 3}
with open(os.path.join(frames_dir, 'actions.tsv')) as f:
    actions = [(r[0], int(r[1]), r[2], r[3]) for r in csv.reader(f, delimiter='\t')]


def names(scene):
    return sorted(n for n in os.listdir(frames_dir) if n.startswith(scene + '_') and n.endswith('.png'))


def frame(scene, i):
    """Frame i of a scene at full size, with the drawn cursor where the game's was."""
    img = Image.open(os.path.join(frames_dir, names_of[scene][i])).convert('RGBA')
    sx, sy = W / img.width, H / img.height
    if img.size != (W, H):
        img = img.resize((W, H), Image.LANCZOS)
    x, y = cursor_at.get((scene, i), ('-', '-'))
    if x != '-':
        img.alpha_composite(cursor, (int(int(x) * sx), int(int(y) * sy)))
    return img


def mark(scene, kind):
    return next((f for s, f, k, _ in actions if s == scene and k == kind), None)


names_of = {s: names(s) for s in ('s_jumble', 's_gather', 's_build')}
for s, n in names_of.items():
    if not n:
        sys.exit(f'no frames for {s}')
close = mark('s_jumble', 'inventory_close')
if close is None:
    sys.exit('s_jumble has no inventory_close mark')

# Each piece: a list of (scene, frame index) per output frame, plus the caption layers and the
# frame (within the piece) each caption starts and stops at.
QUIZ = ['180 PLANKS =', '*___* STACKS?']
COUNTS = ['YOUR LIST', '*COUNTS* FOR YOU']
FOLLOW = ['FOLLOW FOR MORE', '*COZY* MODS']
FADE_IN = 4  # frames for a caption to fade in; scenes cut hard
pieces = []

if SPLIT:
    # Top half: the inventory (its middle band, where the screen is). Bottom half: the list on the
    # empty lot (the top of the frame, where the list is), playing at the same time.
    inv = list(range(0, close))
    hud = list(range(close, len(names_of['s_jumble'])))
    pieces.append(('split', [(('s_jumble', a), ('s_jumble', hud[min(i, len(hud) - 1)])) for i, a in enumerate(inv)], None))
    # Then the rest of the list shot, full frame, as long as the split didn't use.
    rest = hud[len(inv):]
    if rest:
        pieces.append(('s_jumble', [('s_jumble', i) for i in rest], [(COUNTS, 1100, 0, None)]))
else:
    pieces.append(('s_jumble', [('s_jumble', i) for i in range(len(names_of['s_jumble']))],
                   [(QUIZ, 300, 0, close), (COUNTS, 1100, close, None)]))
pieces.append(('s_gather', [('s_gather', i) for i in range(len(names_of['s_gather']))], []))
build = len(names_of['s_build'])
pieces.append(('s_build', [('s_build', i) for i in range(build)], [(FOLLOW, 1150, max(0, build - 30), None)]))

work = os.path.join(out, 'work-' + NAME)
shutil.rmtree(work, ignore_errors=True)
os.makedirs(work)

INVENTORY = (0, 520, W, 1400)  # the inventory screen in the full frame
BOTTOM = (0, 0, W, 960)        # the list and the lot
# The inventory is shrunk a little in the top half, so the question fits above it.
INV_SIZE, INV_AT = (864, 704), (108, 236)
split_top_caption = caption(QUIZ, 24, 72, W, H // 2)
label_with = caption(['*WITH* POCKET LIST'], 830, 44, W, H // 2)

clips, starts, t = [], {}, 0.0
for name, seq, caps in pieces:
    dest = os.path.join(work, name)
    os.makedirs(dest)
    layers = [(caption(lines, top), a, b) for lines, top, a, b in (caps or [])]
    for i, item in enumerate(seq):
        if name == 'split':
            (s1, f1), (s2, f2) = item
            img = Image.new('RGBA', (W, H), (0, 0, 0, 255))
            top = Image.new('RGBA', (W, H // 2), (16, 16, 20, 255))
            top.alpha_composite(frame(s1, f1).crop(INVENTORY).resize(INV_SIZE, Image.LANCZOS), INV_AT)
            top.alpha_composite(split_top_caption)
            bottom = frame(s2, f2).crop(BOTTOM)
            bottom.alpha_composite(label_with)
            img.paste(top, (0, 0))
            img.paste(bottom, (0, H // 2))
            ImageDraw.Draw(img).rectangle([0, H // 2 - 3, W, H // 2 + 3], fill=(0, 0, 0, 255))
        else:
            scene, f = item
            img = frame(scene, f)
            for layer, a, b in layers:
                if i >= a and (b is None or i < b):
                    alpha = 1.0 if a == 0 else min(1.0, (i - a + 1) / FADE_IN)
                    l2 = layer.copy()
                    l2.putalpha(l2.getchannel('A').point(lambda v: int(v * alpha)))
                    img.alpha_composite(l2)
        if name != 'split':
            # The badge would sit on the list in the split's lower half; it comes in after.
            img.alpha_composite(BADGE)
        img.convert('RGB').save(os.path.join(dest, '%04d.png' % i))
    clip = os.path.join(work, name + '.mp4')
    run('ffmpeg', '-y', '-framerate', str(FPS), '-i', os.path.join(dest, '%04d.png'),
        '-vf', 'format=yuv420p', '-r', '30', '-c:v', 'libx264', '-preset', 'slow', '-crf', '18', clip)
    starts[name] = (t, seq)
    t += len(seq) / FPS
    clips.append(clip)
total = t
with open(os.path.join(work, 'list.txt'), 'w') as f:
    f.writelines(f"file '{c.replace(os.sep, '/')}'\n" for c in clips)
silent = os.path.join(out, NAME + '-silent.mp4')
run('ffmpeg', '-y', '-f', 'concat', '-safe', '0', '-i', os.path.join(work, 'list.txt'), '-c', 'copy', '-movflags', '+faststart', silent)

# The game's own sounds where the actions were logged: pops, chests, a chop per log, the chime.
random.seed(7)
cues = []
for name, (start, seq) in starts.items():
    for i, item in enumerate(seq):
        scene, f = item[0] if name == 'split' else item
        for s, af, kind, what in actions:
            if s != scene or af != f:
                continue
            at = start + i / FPS
            if kind == 'pickup':
                cues.append((at, 'pop', ((random.random() - random.random()) * 0.7 + 1.0) * 2.0, 0.32))
            elif kind == 'finished':
                cues.append((at, 'chime', 1.4, 0.6))
            elif kind in ('chest_open', 'chest_close'):
                cues.append((at, kind, 0.95, 0.5))
            elif kind == 'chop':
                cues.append((at, 'chop', 0.8 + random.random() * 0.1, 0.7))
            elif kind == 'inventory_close':
                cues.append((at, 'click', 1.0, 0.2))
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
    '-shortest', '-movflags', '+faststart', os.path.join(out, NAME + '.mp4'))

# The cover: the built cottage with everything gathered, just before the follow caption.
shutil.copy(os.path.join(work, 's_build', '%04d.png' % max(0, build - 31)), os.path.join(out, NAME + '-cover.png'))
print('total seconds', round(total, 2))
for name, (start, seq) in starts.items():
    print(f'  {name}: {len(seq) / FPS:.2f} s')
