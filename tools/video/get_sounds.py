"""Copies the game sounds the video uses out of Loom's asset cache (filled by any game-test run).

python3 -I get_sounds.py <out dir>
The sounds are Mojang's, so they're read from the cache each time rather than kept in the repo.
"""
import glob, json, os, shutil, sys

out = sys.argv[1]
os.makedirs(out, exist_ok=True)
assets = os.path.expanduser('~/.gradle/caches/fabric-loom/assets')
index = sorted(glob.glob(os.path.join(assets, 'indexes', '*.json')))[-1]
objects = json.load(open(index))['objects']
# ui.button.click, entity.item.pickup, block.note_block.chime (Pocket List's finish sound), a chest, and wood breaking.
for name, file in {'click': 'random/click_stereo', 'pop': 'random/pop', 'chime': 'note/icechime',
                   'chest_open': 'block/chest/open', 'chest_close': 'block/chest/close1', 'chop': 'dig/wood1'}.items():
    h = objects[f'minecraft/sounds/{file}.ogg']['hash']
    shutil.copy(os.path.join(assets, 'objects', h[:2], h), os.path.join(out, name + '.ogg'))
    print(name)
