// Checks that a mod jar built against one Minecraft version only calls game methods and fields that
// exist in another, so one jar can run on both. Compiling can't catch this: a method that kept its
// name but changed its return type compiles fine on both versions and still crashes on the other one.
//
//   node tools/check-linkage.js <mod.jar> <minecraft-merged jar of the other version> [package prefix ...]
//
// Package prefixes default to net/minecraft/ and com/mojang/. Classes outside net/minecraft/ that
// aren't in the game jar come from libraries shipped beside it (DataFixerUpper's
// com/mojang/serialization, Brigadier...) and are assumed present. Prints each missing method or field
// with the mod classes that use it; exits 1 if any are missing. Calls a mod makes only by name at
// runtime (reflection, Compat-style lookups) aren't seen, which is the point of making them that way.
const fs = require('fs'), zlib = require('zlib');
const [modJar, gameJar, ...prefixArgs] = process.argv.slice(2);
if (!modJar || !gameJar) {
	console.error('usage: node check-linkage.js <mod.jar> <minecraft jar> [package prefix ...]');
	process.exit(2);
}
const prefixes = prefixArgs.length ? prefixArgs : ['net/minecraft/', 'com/mojang/'];

// A small zip reader (stored and deflated entries).
function readZip(file) {
	const b = fs.readFileSync(file), entries = new Map();
	let eocd = b.length - 22;
	while (b.readUInt32LE(eocd) !== 0x06054b50) eocd--;
	let p = b.readUInt32LE(eocd + 16);
	for (let n = b.readUInt16LE(eocd + 10); n > 0; n--) {
		const method = b.readUInt16LE(p + 10), size = b.readUInt32LE(p + 20), nameLen = b.readUInt16LE(p + 28);
		const extra = b.readUInt16LE(p + 30), comment = b.readUInt16LE(p + 32), local = b.readUInt32LE(p + 42);
		const name = b.toString('utf8', p + 46, p + 46 + nameLen);
		entries.set(name, () => {
			const start = local + 30 + b.readUInt16LE(local + 26) + b.readUInt16LE(local + 28);
			const data = b.subarray(start, start + size);
			return method === 8 ? zlib.inflateRawSync(data) : data;
		});
		p += 46 + nameLen + extra + comment;
	}
	return entries;
}

// Just enough of the class file format: the constant pool, super class, interfaces, fields, methods.
function parseClass(buf) {
	let p = 8;
	const count = buf.readUInt16BE(p); p += 2;
	const pool = new Array(count);
	for (let i = 1; i < count; i++) {
		const tag = buf[p];
		switch (tag) {
			case 1: { const len = buf.readUInt16BE(p + 1); pool[i] = { tag, text: buf.toString('utf8', p + 3, p + 3 + len) }; p += 3 + len; break; }
			case 7: case 8: case 16: case 19: case 20: pool[i] = { tag, a: buf.readUInt16BE(p + 1) }; p += 3; break;
			case 15: pool[i] = { tag }; p += 4; break;
			case 3: case 4: pool[i] = { tag }; p += 5; break;
			case 9: case 10: case 11: case 12: case 17: case 18: pool[i] = { tag, a: buf.readUInt16BE(p + 1), b: buf.readUInt16BE(p + 3) }; p += 5; break;
			case 5: case 6: pool[i] = { tag }; p += 9; i++; break;
			default: throw new Error('unknown constant tag ' + tag);
		}
	}
	const utf = i => pool[i].text, cls = i => (i ? utf(pool[i].a) : null);
	p += 2;
	const self = cls(buf.readUInt16BE(p)); p += 2;
	const sup = cls(buf.readUInt16BE(p)); p += 2;
	const ifaceCount = buf.readUInt16BE(p); p += 2;
	const interfaces = [];
	for (let i = 0; i < ifaceCount; i++) { interfaces.push(cls(buf.readUInt16BE(p))); p += 2; }
	const members = new Set();
	for (const kind of ['field', 'method']) {
		const n = buf.readUInt16BE(p); p += 2;
		for (let i = 0; i < n; i++) {
			const name = utf(buf.readUInt16BE(p + 2)), desc = utf(buf.readUInt16BE(p + 4));
			members.add(`${kind}:${name}:${desc}`);
			const attrs = buf.readUInt16BE(p + 6); p += 8;
			for (let a = 0; a < attrs; a++) { p += 6 + buf.readUInt32BE(p + 2); }
		}
	}
	const refs = [];
	for (const c of pool) {
		if (c && (c.tag === 9 || c.tag === 10 || c.tag === 11)) {
			const nt = pool[c.b];
			refs.push({ kind: c.tag === 9 ? 'field' : 'method', owner: cls(c.a), name: utf(nt.a), desc: utf(nt.b) });
		}
	}
	return { self, sup, interfaces, members, refs };
}

const game = readZip(gameJar), cache = new Map();
// Java's own classes (a game class's parent can be java/lang/Record, java/util/AbstractList...),
// read with the JDK's javap so an inherited method is really there, not just assumed.
const { execFileSync } = require('child_process');
// The JDK's javap: JAVAP if set, otherwise the one beside the running Java, otherwise on the PATH.
const JAVAP = process.env.JAVAP || (process.env.JAVA_HOME ? require('path').join(process.env.JAVA_HOME, 'bin', 'javap') : 'javap');
const jdkCache = new Map();
function jdkClass(name) {
	if (jdkCache.has(name)) return jdkCache.get(name);
	let parsed = null;
	try {
		const dotted = name.split('/').join('.');
		const out = execFileSync(JAVAP, ['-s', '-p', dotted], { encoding: 'utf8' });
		const lines = out.split('\n').map(l => l.replace('\r', ''));
		const members = new Set();
		for (let i = 0; i + 1 < lines.length; i++) {
			const next = lines[i + 1].trim();
			if (!next.startsWith('descriptor: ')) continue;
			const desc = next.slice('descriptor: '.length);
			const decl = lines[i].trim();
			const isMethod = desc.startsWith('(');
			let memberName;
			if (isMethod) {
				const before = decl.slice(0, decl.indexOf('('));
				memberName = before.slice(before.lastIndexOf(' ') + 1);
				if (memberName === dotted) memberName = '<init>';
			} else {
				const bare = decl.endsWith(';') ? decl.slice(0, -1) : decl;
				memberName = bare.slice(bare.lastIndexOf(' ') + 1);
			}
			members.add((isMethod ? 'method:' : 'field:') + memberName + ':' + desc);
		}
		// The class line: "public abstract class java.lang.Record" / "... extends X implements Y, Z {".
		const header = lines.find(l => / (class|interface) /.test(l) && l.trim().endsWith('{')) || '';
		const isInterface = / interface /.test(header);
		const names = text => text.replace(/<[^<>]*(<[^<>]*>[^<>]*)*>/g, '').replace('{', '').split(',')
			.map(x => x.trim()).filter(Boolean).map(x => x.split('.').join('/'));
		const extIdx = header.indexOf(' extends '), implIdx = header.indexOf(' implements ');
		const ext = extIdx < 0 ? [] : names(header.slice(extIdx + 9, implIdx > extIdx ? implIdx : undefined));
		const impl = implIdx < 0 ? [] : names(header.slice(implIdx + 12));
		parsed = {
			members,
			sup: isInterface ? null : (ext[0] || (name === 'java/lang/Object' ? null : 'java/lang/Object')),
			interfaces: isInterface ? [...ext, ...impl] : impl,
		};
	} catch (e) {
		parsed = null;
	}
	jdkCache.set(name, parsed);
	return parsed;
}
function gameClass(name) {
	if (!cache.has(name)) {
		const entry = game.get(name + '.class');
		cache.set(name, entry ? parseClass(entry()) : null);
	}
	return cache.get(name);
}
// Whether a member is declared in the class or anything it inherits from (within the game jar;
// anything that reaches a JDK class is assumed fine).
function has(owner, kind, name, desc, seen = new Set()) {
	if (seen.has(owner)) return false;
	seen.add(owner);
	const c = gameClass(owner) || (owner.startsWith('java/') ? jdkClass(owner) : null);
	// A library class shipped beside the game (not in its jar, not Java's own): assumed present.
	if (!c) return !owner.startsWith('net/minecraft/') && !owner.startsWith('java/');
	if (c.members.has(`${kind}:${name}:${desc}`)) return true;
	return [c.sup, ...c.interfaces].filter(Boolean).some(parent => has(parent, kind, name, desc, seen));
}

const missing = new Map();
let checked = 0;
for (const [name, read] of readZip(modJar)) {
	if (!name.endsWith('.class')) continue;
	const c = parseClass(read());
	for (const ref of c.refs) {
		if (ref.owner.startsWith('[') || !prefixes.some(prefix => ref.owner.startsWith(prefix))) continue;
		checked++;
		if (!gameClass(ref.owner) && !ref.owner.startsWith('net/minecraft/')) {
			continue;
		}
		if (!gameClass(ref.owner)) {
			const key = `class ${ref.owner}`;
			missing.set(key, (missing.get(key) || new Set()).add(c.self));
		} else if (!has(ref.owner, ref.kind, ref.name, ref.desc)) {
			const key = `${ref.kind} ${ref.owner}.${ref.name}${ref.kind === 'field' ? ' : ' : ''}${ref.desc}`;
			missing.set(key, (missing.get(key) || new Set()).add(c.self));
		}
	}
}
console.log(`checked ${checked} game references in ${modJar.split(/[\\/]/).pop()} against ${gameJar.split(/[\\/]/).pop()}`);
for (const [what, users] of [...missing].sort()) console.log(`MISSING ${what}\n    used by ${[...users].join(', ')}`);
console.log(missing.size ? `${missing.size} missing` : 'all present');
process.exit(missing.size ? 1 : 0);
