#!/usr/bin/env node
// Starts a Paper server with the plugin on a brand-new world, with no window, and checks the parts
// that need a real server: the plugin loads and registers /pocket; /pocket selftest reads the
// server's own recipes the way Break down does (oak planks from logs, sticks' two recipes, the
// furnace and blast furnace merged for iron, coal-or-charcoal for a torch), the breakdown maths,
// pasted text, and chest memory on real blocks (a double chest counted once, near and far, a hopper
// filling it, broken and forgotten); /pocket from the console is refused politely (lists belong to
// players); and the plugin logs no error or warning. What needs a player (the sidebar, counting, the
// chat buttons, a book) is a hand play. The Paper server jar is downloaded once into the folder.
// --no-chest-memory starts the plugin with chest memory off: the proof run, where the selftest must fail.
//
//   node tools/paper-server-test.js "<server folder>" --plugin paper/build/libs/pocket-list-paper-<v>.jar --eula
//       [--paper 26.3] [--port <n>] [--timeout <seconds>] [--no-chest-memory]
//
// --eula says you accept Minecraft's server EULA (https://aka.ms/MinecraftEULA); without it the
// script stops before starting the server. The world is deleted first.
'use strict';
const fs = require('fs'), path = require('path'), cp = require('child_process'), crypto = require('crypto');

const args = process.argv.slice(2);
const folder = args[0] && !args[0].startsWith('--') ? path.resolve(args[0]) : null;
const opt = (name, fallback) => { const i = args.indexOf(name); return i >= 0 && i + 1 < args.length ? args[i + 1] : fallback; };
const flag = (name) => args.includes(name);
const plugin = opt('--plugin', null);
if (!folder || !plugin) {
	console.error('usage: node tools/paper-server-test.js "<server folder>" --plugin <jar> --eula [--paper 26.3] [--port <n>] [--timeout <s>] [--no-chest-memory]');
	process.exit(2);
}
if (!flag('--eula')) {
	console.error('The server only runs once the Minecraft server EULA (https://aka.ms/MinecraftEULA) is accepted: pass --eula to say you do.');
	process.exit(2);
}
const paperVersion = opt('--paper', '26.3');
const port = +opt('--port', '25599');
const timeoutMs = +opt('--timeout', '600') * 1000;
const javaBin = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', 'java') : 'java';
const UA = { 'User-Agent': 'pocket-list-server-test (https://github.com/gabmaybelle/pocket-list)' };

async function download(url, to, sha256) {
	const res = await fetch(url, { headers: UA });
	if (!res.ok) throw new Error(`${url}: ${res.status}`);
	const data = Buffer.from(await res.arrayBuffer());
	if (sha256 && crypto.createHash('sha256').update(data).digest('hex') !== sha256) throw new Error(`${url}: checksum mismatch`);
	fs.writeFileSync(to, data);
}

/** The newest Paper build of this version, downloaded once as paper-<version>-<build>.jar. */
async function serverJar() {
	const existing = fs.readdirSync(folder).filter((f) => f.startsWith(`paper-${paperVersion}-`) && f.endsWith('.jar')).sort();
	if (existing.length) return path.join(folder, existing[existing.length - 1]);
	const res = await fetch(`https://fill.papermc.io/v3/projects/paper/versions/${paperVersion}/builds/latest`, { headers: UA });
	if (!res.ok) throw new Error(`Paper build list: ${res.status}`);
	const build = await res.json();
	const dl = build.downloads['server:default'];
	console.log(`downloading Paper ${paperVersion} build ${build.id} (${dl.name})`);
	const to = path.join(folder, dl.name);
	await download(dl.url, to, dl.checksums && dl.checksums.sha256);
	return to;
}

function prepare() {
	fs.mkdirSync(path.join(folder, 'plugins'), { recursive: true });
	fs.writeFileSync(path.join(folder, 'eula.txt'), 'eula=true\n');
	fs.writeFileSync(path.join(folder, 'server.properties'), [
		'online-mode=false', `server-port=${port}`, 'query.port=' + port, 'enable-rcon=false', 'white-list=false',
		'spawn-protection=0', 'view-distance=4', 'simulation-distance=4', 'max-players=2', 'motd=Pocket List test',
		'level-type=minecraft\\:flat', 'level-name=world', 'sync-chunk-writes=false', ''].join('\n'));
	// Only this plugin, and only the jar under test.
	for (const f of fs.readdirSync(path.join(folder, 'plugins'))) {
		if (f.endsWith('.jar')) fs.rmSync(path.join(folder, 'plugins', f));
	}
	fs.copyFileSync(plugin, path.join(folder, 'plugins', path.basename(plugin)));
	// The plugin's settings: its defaults, or chest memory off for the proof run (its checks must fail).
	const config = path.join(folder, 'plugins', 'PocketList', 'config.yml');
	if (flag('--no-chest-memory')) {
		fs.mkdirSync(path.dirname(config), { recursive: true });
		fs.writeFileSync(config, 'chestMemory: false\n');
	} else {
		fs.rmSync(config, { force: true });
	}
	for (const w of ['world', 'world_nether', 'world_the_end']) fs.rmSync(path.join(folder, w), { recursive: true, force: true });
}

/** Runs the server, feeding it console commands and reading their replies. */
class Server {
	constructor(jar) {
		this.lines = [];
		this.waiters = [];
		this.proc = cp.spawn(javaBin, ['-Xmx2G', '-Dcom.mojang.eula.agree=true', '-jar', jar, 'nogui'],
			{ cwd: folder, stdio: ['pipe', 'pipe', 'pipe'], windowsHide: true });
		let buffer = '';
		const onData = (chunk) => {
			buffer += chunk.toString();
			let i;
			while ((i = buffer.indexOf('\n')) >= 0) {
				const line = buffer.slice(0, i).replace(/\r$/, '');
				buffer = buffer.slice(i + 1);
				this.lines.push(line);
				for (const w of [...this.waiters]) if (w.test(line)) { this.waiters.splice(this.waiters.indexOf(w), 1); w.resolve(line); }
			}
		};
		this.proc.stdout.on('data', onData);
		this.proc.stderr.on('data', onData);
		this.exited = new Promise((resolve) => this.proc.on('exit', (code) => { this.exitCode = code; resolve(code); }));
	}

	waitFor(pattern, ms, from = 0) {
		const found = this.lines.slice(from).find((l) => pattern.test(l));
		if (found) return Promise.resolve(found);
		return new Promise((resolve, reject) => {
			const w = { test: (l) => pattern.test(l), resolve: (l) => { clearTimeout(timer); resolve(l); } };
			const timer = setTimeout(() => { this.waiters.splice(this.waiters.indexOf(w), 1); reject(new Error(`timed out waiting for ${pattern}`)); }, ms);
			this.waiters.push(w);
		});
	}

	/** Sends a console command and returns the server's reply line (the plugin's replies all start with "Pocket List"). */
	async command(text, reply = /Pocket List|Unknown or incomplete command|Incorrect argument|System chat: /) {
		const from = this.lines.length;
		this.proc.stdin.write(text + '\n');
		const line = await this.waitFor(reply, 15000, from);
		return line.replace(/^.*?\]: /, '').replace(/\x1b\[[0-9;]*m/g, '');
	}

	ready() {
		return Promise.race([this.waitFor(/Done \(.*\)! For help/, timeoutMs), this.exited.then(() => { throw new Error('the server stopped before it was ready'); })]);
	}

	async stop() {
		if (this.exitCode !== undefined) return;
		try { this.proc.stdin.write('stop\n'); } catch (e) { /* already gone */ }
		const t = setTimeout(() => this.proc.kill(), 60000);
		await this.exited;
		clearTimeout(t);
	}
}

async function main() {
	fs.mkdirSync(folder, { recursive: true });
	const jar = await serverJar();
	prepare();
	console.log(`starting Paper (${path.basename(jar)}) in ${folder} with ${path.basename(plugin)}`);
	const server = new Server(jar);
	const failures = [];
	const check = (name, ok, detail) => { console.log(`${ok ? 'ok  ' : 'FAIL'} ${name}${detail ? ': ' + detail : ''}`); if (!ok) failures.push(name); };
	try {
		await server.ready();
		const readyLine = server.lines.find((l) => /Pocket List: ready/.test(l));
		check('the plugin started', !!readyLine, readyLine ? readyLine.replace(/^.*?\]: /, '') : 'no "Pocket List: ready" line in the log');

		const selftest = await server.command('pocket selftest', /Pocket List selftest:|Unknown or incomplete command|System chat: /);
		check('/pocket selftest passes against the server\'s recipes', /Pocket List selftest: passed/.test(selftest), selftest);
		const alias = await server.command('pocketlist selftest', /Pocket List selftest:|Unknown or incomplete command|System chat: /);
		check('/pocketlist is an alias', /Pocket List selftest: passed/.test(alias), alias);
		const console_ = await server.command('pocket');
		check('/pocket from the console says the list belongs to a player', /belongs to a player/.test(console_), console_);
		const help = await server.command('pocket help', /gathering list|Unknown or incomplete command/);
		check('/pocket help answers', /gathering list/.test(help), help);
		const meant = await server.command('pocket chest 2');
		check('/pocket chest 2 suggests /pocket add chest 2', /Did you mean \/pocket add chest 2\?/.test(meant), meant);
		const meantTwo = await server.command('pocket oak log 8');
		check('/pocket oak log 8 suggests /pocket add oak log 8', /Did you mean \/pocket add oak log 8\?/.test(meantTwo), meantTwo);
		const unknown = await server.command('pocket frobnicate');
		check('an unknown word that is no item points at /pocket help', /\/pocket help lists the commands/.test(unknown) && !/Did you mean/.test(unknown), unknown);

		const errors = () => server.lines.filter((l) => /^\[[\d:]+ (ERROR|WARN)\]/.test(l) && /PocketList|Pocket List|pocketlist/i.test(l));
		check('no errors or warnings from the plugin', errors().length === 0, errors().join(' | '));
	} catch (e) {
		check('the test ran to the end', false, e.message);
	} finally {
		await server.stop();
		fs.writeFileSync(path.join(folder, 'test-console.log'), server.lines.join('\n'));
		console.log(failures.length ? `FAILED: ${failures.join(', ')}` : 'passed: every check');
		process.exit(failures.length ? 1 : 0);
	}
}

main().catch((e) => { console.error(e); process.exit(1); });
