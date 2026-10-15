// Draws Pocket List's mod icon: a pinned sheet of paper with ticked-off lines, as a 16x16 Minecraft-style
// item scaled to 128 with hard pixel edges.
//   node tools/generate-icon.js
const fs = require('fs'), path = require('path'), zlib = require('zlib');
const OUT = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'pocket_list', 'icon.png');

const T = []; for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; T[n] = c >>> 0; }
const crc = b => { let x = 0xffffffff; for (const v of b) x = T[(x ^ v) & 255] ^ (x >>> 8); return (x ^ 0xffffffff) >>> 0; };
const chunk = (t, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length); const td = Buffer.concat([Buffer.from(t), d]); const c = Buffer.alloc(4); c.writeUInt32BE(crc(td)); return Buffer.concat([l, td, c]); };

const S = 16, SCALE = 8;
const px = Array.from({ length: S }, () => Array(S).fill(null));
const set = (x, y, c) => { if (x >= 0 && y >= 0 && x < S && y < S) px[y][x] = c; };
const rect = (x, y, w, h, c) => { for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) set(x + i, y + j, c); };

const EDGE = [150, 132, 104], PAPER = [240, 234, 214], SHADE = [214, 204, 178], INK = [112, 98, 84], BOX = [160, 146, 120];
const TICK = [92, 176, 72], TICK_DARK = [52, 116, 44];
const PIN = [206, 64, 56], PIN_LIGHT = [240, 128, 112], PIN_DARK = [138, 36, 36];

// The sheet: rounded corners all round, shaded along the right and bottom.
rect(2, 1, 12, 15, EDGE); rect(3, 2, 10, 13, PAPER);
rect(12, 2, 1, 13, SHADE); rect(3, 14, 10, 1, SHADE);
for (const [x, y] of [[2, 1], [13, 1], [2, 15], [13, 15]]) set(x, y, null);

// Three evenly spaced lines on one grid: two ticked, one empty box still to go.
const tick = y => { set(4, y + 1, TICK); set(5, y + 2, TICK_DARK); set(6, y + 1, TICK); set(7, y, TICK); };
const box = y => { rect(4, y, 3, 3, BOX); set(5, y + 1, PAPER); };
// The writing runs to the shaded edge, a little shorter on the middle line, so it reads as words.
[3, 7, 11].forEach((y, i) => { if (i < 2) tick(y); else box(y); rect(8, y + 1, i === 1 ? 3 : 4, 1, INK); });

// The pin: a round head with a highlight, pressed into the middle of the top edge, and its shadow.
rect(7, 0, 2, 3, PIN); rect(6, 1, 4, 1, PIN);
set(7, 0, PIN_LIGHT); set(6, 1, PIN_LIGHT);
set(9, 1, PIN_DARK); set(8, 2, PIN_DARK);
set(9, 2, SHADE);

const W = S * SCALE, rows = [];
for (let y = 0; y < W; y++) {
	const r = Buffer.alloc(1 + W * 4);
	for (let x = 0; x < W; x++) {
		const c = px[Math.floor(y / SCALE)][Math.floor(x / SCALE)];
		if (c) { r[1 + x * 4] = c[0]; r[2 + x * 4] = c[1]; r[3 + x * 4] = c[2]; r[4 + x * 4] = c[3] ?? 255; }
	}
	rows.push(r);
}
const ih = Buffer.alloc(13); ih.writeUInt32BE(W, 0); ih.writeUInt32BE(W, 4); ih[8] = 8; ih[9] = 6;
fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ih), chunk('IDAT', zlib.deflateSync(Buffer.concat(rows), { level: 9 })), chunk('IEND', Buffer.alloc(0))]));
console.log('wrote', OUT);
