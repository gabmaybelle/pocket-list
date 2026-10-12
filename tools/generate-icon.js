// Draws Pocket List's mod icon: a pinned note with ticked-off lines, as 32x32 pixel art scaled to 128.
//   node tools/generate-icon.js
const fs = require('fs'), path = require('path'), zlib = require('zlib');
const OUT = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'pocket_list', 'icon.png');

const T = []; for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; T[n] = c >>> 0; }
const crc = b => { let x = 0xffffffff; for (const v of b) x = T[(x ^ v) & 255] ^ (x >>> 8); return (x ^ 0xffffffff) >>> 0; };
const chunk = (t, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length); const td = Buffer.concat([Buffer.from(t), d]); const c = Buffer.alloc(4); c.writeUInt32BE(crc(td)); return Buffer.concat([l, td, c]); };

const S = 32, SCALE = 4;
const px = Array.from({ length: S }, () => Array(S).fill(null));
const set = (x, y, c) => { if (x >= 0 && y >= 0 && x < S && y < S) px[y][x] = c; };
const rect = (x, y, w, h, c) => { for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) set(x + i, y + j, c); };

const OUTLINE = [74, 52, 38], PAPER = [246, 236, 210], PAPER_SHADE = [226, 210, 176];
const INK = [120, 104, 88], TICK = [76, 160, 72], TICK_DARK = [48, 112, 46], BOX = [150, 130, 104];
const PIN = [214, 72, 64], PIN_LIGHT = [242, 132, 116], PIN_DARK = [150, 40, 40], SHADOW = [0, 0, 0, 60];

// Shadow, then the note (slightly taller than wide).
rect(7, 6, 21, 25, SHADOW);
rect(5, 4, 21, 25, OUTLINE);
rect(6, 5, 19, 23, PAPER);
rect(6, 25, 19, 3, PAPER_SHADE);

// Three lines: a little box, ticked for the first two, and a line of writing.
const lines = [10, 16, 22];
lines.forEach((y, i) => {
	rect(8, y, 4, 4, BOX); rect(9, y + 1, 2, 2, PAPER);
	rect(14, y + 1, i === 2 ? 6 : 9, 2, INK);
	if (i < 2) {
		set(8, y + 1, TICK); set(9, y + 2, TICK); set(10, y + 3, TICK_DARK); set(11, y + 2, TICK); set(12, y + 1, TICK); set(13, y, TICK); set(13, y - 1, TICK_DARK);
	}
});

// The pin at the top.
rect(14, 2, 4, 4, PIN_DARK); rect(14, 2, 3, 3, PIN); set(14, 2, PIN_LIGHT); set(15, 2, PIN_LIGHT); set(14, 3, PIN_LIGHT);
rect(15, 6, 1, 2, [120, 120, 130]);

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
