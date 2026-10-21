"""Builds the long-form guide, "one porch, start to finish", from VideoGuide's frames.

python assemble_guide.py <video_guide dir> <sounds dir> <out dir>
(sounds from get_sounds.py; set CAPTION_FONT to a heavy font file if none of the usual ones is found)

Makes, at 1920x1080: pocket-list-guide.mp4 (the game's own sounds), pocket-list-guide-silent.mp4,
pocket-list-guide-thumbnail.png (1280x720), one GIF per scene (each under 5 MB) and chapters.txt
(the YouTube chapter times). It opens on the payoff (the finished porch, the list all green) with
the title over it; each chapter's first scene names the chapter; dusk on the lawn carries the end
card.

Captions are bold white words with a black outline straight on the footage, one word in the list's
green. They sit low, between the list screen's buttons and the hotbar, since the list is top left
and its notes come in top right; the chapter's name sits bottom left. If a scene changes in
VideoGuide, change its caption here too.
"""
import csv, os, random, shutil, subprocess, sys
from PIL import Image, ImageDraw, ImageFont

# Absolute: ffmpeg's concat list resolves relative paths against its own folder.
frames_dir, sounds_dir, out = (os.path.abspath(p) for p in sys.argv[1:4])
# --reuse keeps the scene clips from the last run (only the sound and what follows is made again).
REUSE = '--reuse' in sys.argv[4:]
FPS = 20
W, H = 1920, 1080
GREEN = (124, 252, 106)  # a gathered line on the list
NAME = 'pocket-list-guide'
repo = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')

# Running order: scene, chapter it opens (or None), caption ("*word*" in green), a second caption
# from a logged moment on (kind, lines) or None, and seconds to hold the last frame. Chapter names
# are YouTube's too (chapters.txt), and each chapter must run at least 10 s.
SCENES = [
    ('w_payoff', 'Intro', None, None, 5.6),
    ('w_plan', 'Plan a list', ['Press *K* and add what the build needs'], None, 0.8),
    ('w_breakdown', 'Recipes and storage', ['*Break down* a recipe into what to gather'], None, 1.0),
    ('w_storage', None, ['Chests at home *count* too'], None, 1.0),
    ('w_paste', 'A second list', ['*Paste* a list from your notes'], None, 1.0),
    ('w_gather', 'Gather what is missing', ['Pick it up and it *ticks off*'],
     ('switch', ['Craft the last *torches*, and the porch is done']), 1.0),
    ('w_litematica', 'Build it', ['Bring in a *Litematica* material list'], None, 1.0),
    ('w_build', None, ['*Clear Done* tidies the list, then build'], None, 0.8),
    ('w_settings', 'Make it yours', ['Put the list in any *corner*'],
     ('half', ['Client-side: works on *any server*']), 1.0),
    ('w_end', 'End', None, None, 3.0),
]
TITLE = ['A gathering list', 'that *ticks itself off*']
END = ['*Pocket List*', 'Fabric and NeoForge: Minecraft 26.3, 26.2 and 1.21.1', 'Fabric and Forge: Minecraft 1.20.1']
FADE = 6  # frames: captions fade in; a chapter fades in from black and the video fades out at the end

FONTS = [os.environ.get('CAPTION_FONT', ''), 'C:/Windows/Fonts/ariblk.ttf', '/usr/share/fonts/truetype/msttcorefonts/Arial_Black.ttf',
         '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf']
TEXT_FONTS = [os.environ.get('BADGE_FONT', ''), 'C:/Windows/Fonts/segoeuib.ttf', '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf']


def font(paths, size):
    for p in paths:
        if p and os.path.exists(p):
            return ImageFont.truetype(p, size)
    sys.exit('no caption font found; set CAPTION_FONT')


def run(*args):
    # -nostdin: run in the background, ffmpeg otherwise sits waiting for keyboard input.
    if args[0] == 'ffmpeg':
        args = ('ffmpeg', '-nostdin') + args[1:]
    subprocess.run(args, check=True, stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)


def words(layer, lines, top, face, stroke, lead, centre=W / 2):
    """Draws caption lines, centred on `centre`, each "*word*" in green."""
    d = ImageDraw.Draw(layer)
    y = top
    for line in lines:
        parts = [(p, GREEN if i % 2 else (255, 255, 255)) for i, p in enumerate(line.split('*')) if p]
        x = centre - sum(d.textlength(p, font=face) for p, _ in parts) / 2
        for text, colour in parts:
            d.text((x, y), text, font=face, fill=colour, stroke_width=stroke, stroke_fill=(0, 0, 0))
            x += d.textlength(text, font=face)
        y += lead
    return layer


CAPTION = font(FONTS, 54)
CHAPTER = font(TEXT_FONTS, 34)


def caption(lines):
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    return words(layer, lines, 878, CAPTION, 6, 70)


def chapter(name):
    """The chapter's name, small, bottom left, on a dark rounded tab."""
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    text = name.upper()
    width = d.textlength(text, font=CHAPTER)
    d.rounded_rectangle((40, H - 100, 40 + width + 44, H - 44), 14, fill=(20, 20, 24, 200))
    d.text((62, H - 96), text, font=CHAPTER, fill=GREEN)
    return layer


def title():
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    big = font(FONTS, 100)
    return words(layer, TITLE, H // 2 - 40, big, 9, 128)


def end_card():
    """Darkened footage with the icon, the name and where it runs."""
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 130))
    icon = Image.open(os.path.join(repo, 'common/src/main/resources/assets/pocket_list/icon.png')).convert('RGBA').resize((200, 200), Image.NEAREST)
    layer.alpha_composite(icon, ((W - 200) // 2, 170))
    words(layer, END[:1], 400, font(FONTS, 96), 8, 120)
    words(layer, END[1:], 540, font(TEXT_FONTS, 46), 4, 64)
    words(layer, ['Client-side, works on any server'], 690, font(TEXT_FONTS, 40), 4, 50)
    # The credit line goes in the description, not on the card (Gabby, 2026-10-20).
    return layer


def faded(layer, a):
    if a >= 1:
        return layer
    out = layer.copy()
    out.putalpha(out.getchannel('A').point(lambda v: int(v * a)))
    return out


# A Minecraft-ish pixel arrow (1 = white, 2 = black outline), drawn at 3x: the cursor on screens.
ARROW = ["2", "22", "212", "2112", "21112", "211112", "2111112", "21111112", "211111112", "2111112222",
         "2112112", "212 2112", "22  2112", "2    2112", "     2112", "      22"]
cursor = Image.new('RGBA', (30, 48), (0, 0, 0, 0))
d = ImageDraw.Draw(cursor)
for y, row in enumerate(ARROW):
    for x, c in enumerate(row):
        if c in '12':
            d.rectangle([x * 3, y * 3, x * 3 + 2, y * 3 + 2], fill=(255, 255, 255, 255) if c == '1' else (0, 0, 0, 255))

with open(os.path.join(frames_dir, 'frames.tsv')) as f:
    cursor_at = {(r[0], int(r[1])): (r[2], r[3]) for r in csv.reader(f, delimiter='\t') if len(r) > 3}
with open(os.path.join(frames_dir, 'actions.tsv')) as f:
    actions = [(r[0], int(r[1]), r[2], r[3]) for r in csv.reader(f, delimiter='\t')]


def first_mark(scene, kind):
    marks = [f for s, f, k, _ in actions if s == scene and k == kind]
    return marks[0] if marks else None


work = os.path.join(out, 'work-guide')
if not REUSE:
    shutil.rmtree(work, ignore_errors=True)
os.makedirs(work, exist_ok=True)
TITLE_LAYER, END_LAYER = title(), end_card()

length = {}  # scene -> frames out, holds included
filmed = {}  # scene -> frames filmed
for scene, chap, lines, change, hold in SCENES:
    names = sorted(n for n in os.listdir(frames_dir) if n.startswith(scene + '_') and n.endswith('.png') and n[len(scene) + 1:-4].isdigit())
    if not names:
        print('no frames for', scene, '(skipped)')
        continue
    n_frames = len(names)
    held = int(round(hold * FPS))
    filmed[scene] = n_frames
    length[scene] = n_frames + held
    dest = os.path.join(work, scene)
    if REUSE and os.path.exists(os.path.join(work, scene + '.mp4')):
        continue
    shutil.rmtree(dest, ignore_errors=True)
    os.makedirs(dest)
    cap = caption(lines) if lines else None
    cap2, cap2_from = None, None
    if change:
        kind, lines2 = change
        cap2 = caption(lines2)
        cap2_from = n_frames // 2 if kind == 'half' else first_mark(scene, kind)
    tab = chapter(chap) if chap and scene not in ('w_payoff', 'w_end') else None
    finished = first_mark(scene, 'finished')
    title_from = (finished + 12) if scene == 'w_payoff' and finished is not None else None
    end_from = max(0, length[scene] - 100) if scene == 'w_end' else None
    for i in range(length[scene]):
        n = min(i, n_frames - 1)
        # A held frame with nothing fading in or out is the frame before it again.
        settled = i > n_frames and (cap2_from is None or i - cap2_from > FADE) and (title_from is None or i - title_from > FADE)
        if settled and (end_from is None or i < end_from or i - end_from > FADE * 2):
            shutil.copyfile(os.path.join(dest, '%04d.png' % (i - 1)), os.path.join(dest, '%04d.png' % i))
            continue
        img = Image.open(os.path.join(frames_dir, names[n])).convert('RGBA')
        sx, sy = W / img.width, H / img.height
        if img.size != (W, H):
            img = img.resize((W, H), Image.LANCZOS)
        x, y = cursor_at.get((scene, n), ('-', '-'))
        if x != '-':
            img.alpha_composite(cursor, (int(int(x) * sx), int(int(y) * sy)))
        if cap2 is not None and cap2_from is not None and i >= cap2_from:
            img.alpha_composite(faded(cap2, min(1.0, (i - cap2_from + 1) / FADE)))
        elif cap is not None:
            img.alpha_composite(faded(cap, min(1.0, (i + 1) / FADE)))
        if tab is not None:
            img.alpha_composite(faded(tab, min(1.0, (i + 1) / FADE)))
        if title_from is not None and i >= title_from:
            img.alpha_composite(faded(TITLE_LAYER, min(1.0, (i - title_from + 1) / FADE)))
        if end_from is not None and i >= end_from:
            img.alpha_composite(faded(END_LAYER, min(1.0, (i - end_from + 1) / (FADE * 2))))
        # Light compression: these frames are read once by ffmpeg, and full compression took most of the time.
        img.convert('RGB').save(os.path.join(dest, '%04d.png' % i), compress_level=1)

# One clip per scene, cut hard inside a chapter; a chapter fades in from black, the video fades out.
order = [s for s, *_ in SCENES if s in length]
chapter_starts = {s for s, c, *_ in SCENES if c}
clips, starts, t = [], {}, 0.0
for scene in order:
    secs = length[scene] / FPS
    fades = []
    if scene in chapter_starts and scene != order[0]:
        fades.append('fade=t=in:st=0:d=0.3')
    if scene == order[-1]:
        fades.append(f'fade=t=out:st={max(0.0, secs - 1.0):.3f}:d={min(1.0, secs):.3f}')
    vf = ','.join(fades + ['format=yuv420p'])
    clip = os.path.join(work, scene + '.mp4')
    if not (REUSE and os.path.exists(clip)):
        run('ffmpeg', '-y', '-framerate', str(FPS), '-i', os.path.join(work, scene, '%04d.png'), '-vf', vf,
            '-r', '30', '-c:v', 'libx264', '-preset', 'slow', '-crf', '17', clip)
    starts[scene] = t
    t += secs
    clips.append(clip)
total = t
with open(os.path.join(work, 'list.txt'), 'w') as f:
    f.writelines(f"file '{c.replace(os.sep, '/')}'\n" for c in clips)
silent = os.path.join(out, NAME + '-silent.mp4')
run('ffmpeg', '-y', '-f', 'concat', '-safe', '0', '-i', os.path.join(work, 'list.txt'), '-c', 'copy', '-movflags', '+faststart', silent)

# The game's own sounds at the logged frames. Typing makes no sound in the game, and neither do
# clicks on inventory slots, so none here; a stack put back in the inventory is logged as a pickup,
# so pickups inside the inventory are skipped too.
random.seed(7)
SOUND = {'click': (['click'], 0.45), 'pickup': (['pop'], 0.4), 'finished': (['chime'], 0.8),
         'chest_open': (['chest_open'], 0.6), 'chest_close': (['chest_close'], 0.6),
         'barrel_open': (['barrel_open'], 0.6), 'barrel_close': (['barrel_close'], 0.6),
         'pick': (['grass'], 0.7), 'place': (['chop', 'wood2', 'wood3', 'wood4'], 0.3)}
in_inventory = set()
for scene in order:
    opened = first_mark(scene, 'inventory_open')
    closed = first_mark(scene, 'inventory_close')
    if opened is not None:
        in_inventory |= {(scene, f) for f in range(opened, (closed if closed is not None else filmed[scene]) + 1)}
cues = []
for scene, frame, kind, what in actions:
    if scene not in length or kind not in SOUND or frame >= filmed[scene]:
        continue
    if kind in ('click', 'pickup') and (scene, frame) in in_inventory:
        continue
    at = starts[scene] + frame / FPS
    names, volume = SOUND[kind]
    cues.append((at, random.choice(names), 0.9 + random.random() * 0.2, volume))
# Each cue its own input: one file split into cues with different delays makes amix wait forever.
inputs, filters = [], []
for i, (at, sound, pitch, volume) in enumerate(cues):
    inputs += ['-i', os.path.join(sounds_dir, sound + '.ogg')]
    ms = int(at * 1000)
    filters.append(f'[{i}:a]aformat=sample_rates=44100:channel_layouts=stereo,asetrate={44100 * pitch:.0f},aresample=44100,'
                   f'volume={volume},adelay={ms}|{ms}[s{i}]')
mix = ''.join(f'[s{i}]' for i in range(len(cues)))
filters.append(f'{mix}amix=inputs={len(cues)}:normalize=0,apad=whole_dur={total:.3f}[a]')
graph = os.path.join(work, 'sound-graph.txt')
with open(graph, 'w') as f:
    f.write(';'.join(filters))
audio = os.path.join(work, 'sound.wav')
# A long graph goes in a file: ffmpeg 9 reads it with -/filter_complex (it dropped -filter_complex_script).
run('ffmpeg', '-y', *inputs, '-/filter_complex', graph, '-map', '[a]', '-t', f'{total:.3f}', audio)
run('ffmpeg', '-y', '-i', silent, '-i', audio, '-map', '0:v', '-map', '1:a', '-c:v', 'copy', '-c:a', 'aac', '-b:a', '192k',
    '-shortest', '-movflags', '+faststart', os.path.join(out, NAME + '.mp4'))

# YouTube's chapters: the first must start at 0:00, each at least 10 s long.
chapters = [(scene, chap) for scene, chap, *_ in SCENES if chap and scene in length]
with open(os.path.join(out, 'chapters.txt'), 'w') as f:
    for k, (scene, chap) in enumerate(chapters):
        s = int(starts[scene])
        until = starts[chapters[k + 1][0]] if k + 1 < len(chapters) else total
        f.write(f'{s // 60}:{s % 60:02d} {chap}\n')
        if until - starts[scene] < 10:
            print(f'  warning: chapter "{chap}" runs {until - starts[scene]:.1f} s, under YouTube\'s 10 s')

# One GIF per scene: the scene with its caption, 640 wide, smaller until it is under 5 MB.
for scene in order:
    gif = os.path.join(out, f'pocket-list-guide-{scene[2:]}.gif')
    for width, fps in ((640, 15), (560, 12), (480, 10), (400, 10)):
        chain = f'fps={fps},scale={width}:-1:flags=lanczos'
        run('ffmpeg', '-y', '-i', os.path.join(work, scene + '.mp4'), '-filter_complex',
            f'[0:v]{chain},split[a][b];[a]palettegen=max_colors=192:stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=4:diff_mode=rectangle',
            gif)
        if os.path.getsize(gif) < 5 * 1024 * 1024:
            break
    print(f'  {scene}: {length[scene] / FPS:.2f} s, GIF {os.path.getsize(gif) // 1024} KB at {width}')

# The thumbnail: the porch at sunset, all gathered, with the corner list enlarged beside the name.
raw = sorted(n for n in os.listdir(frames_dir) if n.startswith('w_payoff_') and n[9:-4].isdigit())
finished = first_mark('w_payoff', 'finished')
pick = min(len(raw) - 1, (finished + 20) if finished is not None else len(raw) - 1)
frame = Image.open(os.path.join(frames_dir, raw[pick])).convert('RGBA')
thumb = frame.resize((1280, 720), Image.LANCZOS)
# The whole list box, top left of the game frame, at 1.5 times its size, laid in the same corner
# over the original so no second copy shows, with a shadow on its open sides.
box = frame.crop((0, 0, int(frame.width * 0.355), int(frame.height * 0.47)))
box = box.resize((int(1280 * 0.355 * 1.5), int(720 * 0.47 * 1.5)), Image.LANCZOS)
thumb.alpha_composite(Image.new('RGBA', (box.width + 12, box.height + 12), (0, 0, 0, 150)), (0, 0))
thumb.alpha_composite(box, (0, 0))
d = ImageDraw.Draw(thumb)
big = font(FONTS, 120)
for i, (text, colour) in enumerate([('POCKET', (255, 255, 255)), ('LIST', GREEN)]):
    x = 1280 - 40 - d.textlength(text, font=big)
    d.text((x, 60 + i * 130), text, font=big, fill=colour, stroke_width=10, stroke_fill=(0, 0, 0))
small = font(FONTS, 54)
for i, line in enumerate(['TICKS ITSELF', 'OFF']):
    x = 1280 - 40 - d.textlength(line, font=small)
    d.text((x, 470 + i * 66), line, font=small, fill=(255, 255, 255), stroke_width=6, stroke_fill=(0, 0, 0))
thumb.convert('RGB').save(os.path.join(out, NAME + '-thumbnail.png'))
print('total seconds', round(total, 2))
print(open(os.path.join(out, 'chapters.txt')).read())
