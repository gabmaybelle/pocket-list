"""Renders the video's title card, one caption card per scene and the end card as 1280x720 PNGs.

python3 -I make_cards.py <out dir>   (set CHROME to a Chrome or Chromium binary if it isn't the default)
"""
import html, os, shutil, subprocess, sys

out = sys.argv[1]
os.makedirs(out, exist_ok=True)
repo = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
shutil.copy(os.path.join(repo, 'common/src/main/resources/assets/pocket_list/icon.png'), os.path.join(out, 'icon.png'))
CHROME = os.environ.get('CHROME', '/opt/pw-browsers/chromium-1194/chrome-linux/chrome')
STYLE = """
* { margin:0; box-sizing:border-box; }
html,body { width:1280px; height:720px; overflow:hidden; }
body { background:#2b241d; font-family:'Inter',sans-serif; color:#f3ead7;
  display:flex; align-items:center; justify-content:center; }
.paper { background:#efe6d2; color:#4a3d2f; border-radius:18px; box-shadow:0 18px 50px rgba(0,0,0,.45);
  padding:56px 72px; display:flex; align-items:center; gap:44px; max-width:1080px; }
img { width:160px; height:160px; image-rendering:pixelated; flex:none; }
h1 { font-family:'Inter Display','Inter',sans-serif; font-weight:800; font-size:76px; letter-spacing:-1.5px; line-height:1; }
h2 { font-weight:500; font-size:34px; margin-top:16px; color:#6b5a46; }
.step { font-weight:700; font-size:22px; letter-spacing:3px; text-transform:uppercase; color:#4f9a3c; margin-bottom:14px; }
.cap { font-family:'Inter Display','Inter',sans-serif; font-weight:700; font-size:50px; line-height:1.15; letter-spacing:-.5px; }
.end { text-align:center; flex-direction:column; gap:26px; }
.end p span.part { white-space:nowrap; }
.end p { line-height:1.5; font-size:32px; font-weight:600; color:#4a3d2f; }
.end .dot { color:#4f9a3c; padding:0 14px; }
.notes { font-family:'DejaVu Sans Mono',monospace; font-size:24px; background:#fffaf0; border:2px dashed #c9b994;
  border-radius:10px; padding:16px 22px; margin-top:22px; line-height:1.45; color:#4a3d2f; white-space:pre; }
"""
def page(body):
    return f"<!doctype html><html><head><meta charset=utf-8><style>{STYLE}</style></head><body>{body}</body></html>"

cards = {
 'title': page('<div class="paper"><img src="icon.png"><div><h1>Pocket List</h1><h2>a checklist for what you\'re gathering</h2></div></div>'),
 'end': page('<div class="paper end"><img src="icon.png" style="width:112px;height:112px"><h1 style="font-size:60px">Pocket List</h1>'
             '<p><span class="part">Fabric + NeoForge</span><span class="dot">·</span><span class="part">Minecraft 26.2 and 26.3</span><br><span class="part" style="color:#4f9a3c">client-side, works on any server</span></p></div>'),
}
captions = [
 ('add', '1', 'Press K, search for anything, and say how many you need.', None),
 ('count', '2', 'It counts as you collect, and ticks off when you have them all.', None),
 ('breakdown', '3', 'Break down: swap a thing for what it takes to make it.', None),
 # The same lines VideoTour.NOTES pastes.
 ('paste', '4', 'Paste List: copy a list from your notes, get a checklist.',
  "- 64 oak planks\n- 32 glass pane\n[ ] 12 spruce stairs\n4 flower pot"),
 ('chest', '5', 'Open a chest once and what\'s inside keeps counting.', None),
]
for name, n, text, notes in captions:
    extra = f'<div class="notes">{html.escape(notes)}</div>' if notes else ''
    cards['cap_' + name] = page(f'<div class="paper"><div><div class="step">{n} of 5</div><div class="cap">{html.escape(text)}</div>{extra}</div></div>')
for name, doc in cards.items():
    path = os.path.join(out, name + '.html')
    open(path, 'w').write(doc)
    subprocess.run([CHROME, '--headless', '--no-sandbox', '--disable-gpu', '--hide-scrollbars', '--force-device-scale-factor=1',
                    '--window-size=1280,720', '--screenshot=' + os.path.join(out, name + '.png'), 'file://' + path],
                   check=True, capture_output=True)
    print(name)
