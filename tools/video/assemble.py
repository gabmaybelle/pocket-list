"""Builds pocket-list.mp4, pocket-list-silent.mp4 and one GIF per scene from VideoTour's frames.

python3 -I assemble.py <video_tour dir> <cards dir> <sounds dir> <out dir>
"""
import csv, os, random, shutil, subprocess, sys
from PIL import Image, ImageDraw

frames_dir, cards_dir, sounds_dir, out = sys.argv[1:5]
FPS = 20
W, H = 1280, 720
SCENES = ['add', 'count', 'breakdown', 'paste', 'chest']
CARD_SECONDS = {'title': 2.2, 'cap_add': 1.7, 'cap_count': 1.7, 'cap_breakdown': 1.7, 'cap_paste': 2.7, 'cap_chest': 1.7, 'end': 3.0}
work = os.path.join(out, 'work')
shutil.rmtree(work, ignore_errors=True)
os.makedirs(work)

def run(*args):
    subprocess.run(args, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)

# A Minecraft-ish pixel arrow (1 = white, 2 = black outline), drawn at 2x.
ARROW = [
    "2",
    "22",
    "212",
    "2112",
    "21112",
    "211112",
    "2111112",
    "21111112",
    "211111112",
    "2111112222",
    "2112112",
    "212 2112",
    "22  2112",
    "2    2112",
    "     2112",
    "      22",
]
cursor = Image.new('RGBA', (20, 32), (0, 0, 0, 0))
d = ImageDraw.Draw(cursor)
for y, row in enumerate(ARROW):
    for x, c in enumerate(row):
        if c in '12':
            d.rectangle([x * 2, y * 2, x * 2 + 1, y * 2 + 1], fill=(255, 255, 255, 255) if c == '1' else (0, 0, 0, 255))

with open(os.path.join(frames_dir, 'frames.tsv')) as f:
    cursor_at = {(r[0], int(r[1])): (r[2], r[3]) for r in csv.reader(f, delimiter='\t')}
with open(os.path.join(frames_dir, 'actions.tsv')) as f:
    actions = [(r[0], int(r[1]), r[2], r[3]) for r in csv.reader(f, delimiter='\t')]

# Frames with the cursor drawn in, scaled to 1280x720 if the window wasn't.
scene_frames = {}
for scene in SCENES:
    names = sorted(n for n in os.listdir(frames_dir) if n.startswith(scene + '_') and n.endswith('.png'))
    scene_frames[scene] = len(names)
    dest = os.path.join(work, scene)
    os.makedirs(dest)
    for i, name in enumerate(names):
        img = Image.open(os.path.join(frames_dir, name)).convert('RGB')
        sx, sy = W / img.width, H / img.height
        if img.size != (W, H):
            img = img.resize((W, H), Image.LANCZOS)
        x, y = cursor_at.get((scene, i), ('-', '-'))
        if x != '-':
            img.paste(cursor, (int(int(x) * sx), int(int(y) * sy)), cursor)
        img.save(os.path.join(dest, '%04d.png' % i))

# The running order, and where each piece starts.
order = ['title']
for scene in SCENES:
    order += ['cap_' + scene, scene]
order.append('end')
FADE = 0.25
clips, starts, t = [], {}, 0.0
for piece in order:
    clip = os.path.join(work, piece + '.mp4')
    if piece in CARD_SECONDS:
        secs = CARD_SECONDS[piece]
        src = ['-loop', '1', '-t', str(secs), '-i', os.path.join(cards_dir, piece + '.png')]
    else:
        secs = scene_frames[piece] / FPS
        src = ['-i', os.path.join(work, piece, '%04d.png')]
    fades = f'fade=t=in:st=0:d={FADE},fade=t=out:st={secs - FADE:.3f}:d={FADE}'
    run('ffmpeg', '-y', '-framerate', str(FPS), *src, '-vf', f'scale={W}:{H},{fades},format=yuv420p',
        '-r', str(FPS), '-c:v', 'libx264', '-preset', 'slow', '-crf', '18', clip)
    starts[piece] = t
    t += secs
    clips.append(clip)
total = t
with open(os.path.join(work, 'list.txt'), 'w') as f:
    f.writelines(f"file '{c}'\n" for c in clips)
silent = os.path.join(out, 'pocket-list-silent.mp4')
run('ffmpeg', '-y', '-f', 'concat', '-safe', '0', '-i', os.path.join(work, 'list.txt'), '-c', 'copy', '-movflags', '+faststart', silent)

# Sounds where the game makes them: buttons click, items pop in, chests open and close, and
# Pocket List's chime (a note-block chime at pitch 1.4, volume 0.6) when the list is complete.
# Clicking into a text box, pressing K and typing make no sound in the game, so none here.
random.seed(7)
cues = []
for scene, frame, kind, what in actions:
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
    '-shortest', '-movflags', '+faststart', os.path.join(out, 'pocket-list.mp4'))
with open(os.path.join(out, 'sound-cues.tsv'), 'w') as f:
    f.write('seconds\tsound\tpitch\tvolume\n')
    f.writelines(f'{a:.2f}\t{s}\t{p:.2f}\t{v}\n' for a, s, p, v in cues)

# One GIF per scene: its caption for a moment, then the scene, at 640x360 and 15 fps.
for scene in SCENES:
    gif = os.path.join(out, f'pocket-list-{scene}.gif')
    for width, fps in ((640, 15), (560, 12), (480, 10)):
        chain = f'fps={fps},scale={width}:-1:flags=lanczos'
        run('ffmpeg', '-y', '-i', os.path.join(work, f'cap_{scene}.mp4'), '-i', os.path.join(work, f'{scene}.mp4'),
            '-filter_complex', f'[0:v]trim=0:1.6,setpts=PTS-STARTPTS[c];[c][1:v]concat=n=2:v=1[v];[v]{chain},split[a][b];'
            '[a]palettegen=max_colors=192:stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=4:diff_mode=rectangle',
            gif)
        if os.path.getsize(gif) < 5 * 1024 * 1024:
            break
    print(scene, width, fps, os.path.getsize(gif))
print('total seconds', round(total, 2))
