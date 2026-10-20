package com.gabbymaybelle.pocketlist;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Films the third cut of the YouTube Short with {@link Film}, in a tall 1080x1920 window, seen from
 * behind the player in third person: a builder's story. An inventory of odd stacks on an empty lot
 * (how much of the cottage is in there?), the list in the corner that says exactly, gathering the
 * rest at the timber house (a storage chest, two logs, two lanterns), then the cottage itself.
 *
 * <p>Only runs with `./gradlew :fabric:runClientGameTest -PvideoShort3`; frames go in the game
 * folder's `video_short3`. Every other -Pvideo... property is passed on to {@link Film}:
 * {@code -PvideoWorld=<zip>} films in a saved world with kept "cottage" and "timber" spots (the
 * cottage must be built there), {@code -PvideoSkin=<png>} dresses the player in that slim-armed
 * skin, {@code -PvideoScout} takes one still per scene, {@code -PvideoScenes=s_gather} films only
 * those.
 */
public class VideoShort3 implements FabricClientGameTest {
	/** Names the system properties: pocketlist.short3.videoTour, pocketlist.short3.videoScenes... */
	private static final String PREFIX = "pocketlist.short3";

	private Film film;
	private BlockPos cottage;
	private BlockPos timber;
	private int lastHave;
	private int lastCarried;
	private boolean lastFinished;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!Film.on(PREFIX)) {
			return;
		}
		film = new Film(context, PREFIX, "video_short3");
		film.take(() -> film.newWorld("pocket list"), this::stage, this::scenes);
	}

	// ------------------------------------------------------------------ the set

	/** A new world (no saved one given): both spots on the ground a little way from spawn. */
	private void stage() {
		var spawn = film.server.computeOnServer(s -> s.overworld().getRespawnData().pos());
		int x = spawn.getX() + 20, z = spawn.getZ();
		film.keep("cottage", x + " " + film.ground(x, z) + " " + z);
		film.keep("timber", (x + 45) + " " + film.ground(x + 45, z) + " " + z);
	}

	private static BlockPos spot(String kept) {
		String[] s = kept.split(" ");
		return new BlockPos(Integer.parseInt(s[0]), Integer.parseInt(s[1]), Integer.parseInt(s[2]));
	}

	private void cmd(String format, Object... args) {
		film.server.runCommand(String.format(Locale.ROOT, format, args));
	}

	/** A point {@code east}, {@code up} and {@code south} of a spot's block corner. */
	private static Vec3 off(BlockPos spot, double east, double up, double south) {
		return new Vec3(spot.getX() + east, spot.getY() + up, spot.getZ() + south);
	}

	/**
	 * Survival, HUD on, seen from behind; standing at {@code feet} looking at {@code look}, with a
	 * fresh list. Items are given off camera, so their names over the hotbar fade before filming.
	 */
	private void ready(Vec3 feet, Vec3 look) {
		film.ctx.setScreen(() -> null);
		film.hud(true);
		film.survival();
		film.thirdPerson(true, 6);
		cmd("kill @e[type=item]");
		cmd("clear @a");
		film.camera(feet, look);
		film.ctx.waitTicks(3);
		film.turnHead(look);
		film.ctx.runOnClient(mc -> {
			Lists.data().lists.clear();
			Lists.data().welcomed = true;
			mc.player.getInventory().setSelectedSlot(8);
		});
	}

	// ------------------------------------------------------------------ the cottage's list

	/**
	 * What the cottage is built from (counted from its build file) and how much of it waits in the
	 * storage chest. The jumble carries some of each; the rest (two logs, two lanterns) is gathered
	 * by hand on camera.
	 */
	private record Need(Item item, int need, int chest) {
	}

	private static final List<Need> NEEDS = List.of(
			new Need(Items.SPRUCE_PLANKS, 180, 134),
			new Need(Items.DEEPSLATE_TILE_STAIRS, 171, 144),
			new Need(Items.SPRUCE_LOG, 92, 73),
			new Need(Items.COBBLESTONE, 70, 40),
			new Need(Items.CALCITE, 63, 56),
			new Need(Items.GLASS_PANE, 22, 18),
			new Need(Items.LANTERN, 7, 4));

	/** A real survival inventory: the cottage's things in odd, split stacks among the usual junk. */
	private static final String[][] JUMBLE = {
			{ "hotbar.1", "spruce_planks", "23" }, { "hotbar.2", "dirt", "41" }, { "hotbar.3", "cobblestone", "30" },
			{ "hotbar.5", "wheat_seeds", "6" }, { "inventory.0", "spruce_log", "12" }, { "inventory.2", "deepslate_tile_stairs", "8" },
			{ "inventory.4", "spruce_planks", "14" }, { "inventory.7", "stick", "11" }, { "inventory.9", "calcite", "7" },
			{ "inventory.10", "poppy", "3" }, { "inventory.12", "deepslate_tile_stairs", "19" }, { "inventory.14", "spruce_log", "5" },
			{ "inventory.16", "glass_pane", "4" }, { "inventory.18", "bone", "2" }, { "inventory.20", "spruce_planks", "9" },
			{ "inventory.22", "lantern", "1" }, { "inventory.25", "dirt", "17" } };

	/** The jumble, with the axe in the first hotbar slot. */
	private void giveJumble() {
		cmd("clear @a");
		cmd("item replace entity @a hotbar.0 with netherite_axe");
		for (String[] s : JUMBLE) {
			cmd("item replace entity @a %s with %s %s", s[0], s[1], s[2]);
		}
	}

	private void cottageList() {
		film.ctx.runOnClient(mc -> {
			Lists.data().lists.clear();
			Lists.active().name = "Cottage";
			Lists.data().hudLines = 8;
			for (Need n : NEEDS) {
				Lists.add(Entry.ofItem(n.item(), n.need()));
			}
		});
	}

	private static String id(Item item) {
		return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
	}

	/** A GUI scale past the game's own limit, so the list reads on a phone. */
	private void gui(int scale) {
		film.ctx.runOnClient(mc -> mc.getWindow().setGuiScale(scale));
		film.ctx.waitTicks(2);
	}

	private void cameraDistance(double blocks) {
		cmd("attribute @p minecraft:camera_distance base set %.2f", blocks);
	}

	// ------------------------------------------------------------------ the scenes

	/**
	 * The Short is cut together as jumble, gather, build, but filmed build first: the jumble scene
	 * clears the cottage away to show the empty lot, so it goes last.
	 */
	private void scenes() {
		cottage = spot(film.kept("cottage"));
		timber = spot(film.kept("timber"));
		film.window(1080, 1920, 3);
		film.scene("s_build", this::build);
		film.scene("s_gather", this::gather);
		film.scene("s_jumble", this::jumble);
	}

	/** On the cottage's front path, where the jumble starts and the build ends. */
	private Vec3 pathStand() {
		return off(cottage, 0.5, 0, 5.5);
	}

	private Vec3 porchLook() {
		return off(cottage, 0.5, 2.6, -8);
	}

	/** How far behind her the camera ends the build, and films the jumble from. */
	private static final double WIDE = 12;

	/** The cottage's build space, from its README: x -8..8, z -18..-1, y -2..13 from the spot. */
	private void clearCottage() {
		BlockPos a = cottage.offset(-9, 0, -19), b = cottage.offset(9, 14, 0);
		cmd("fill %d %d %d %d %d %d air", a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
		// Only the cottage's own pad gets new grass (its foundation sat in it), so no seam shows on the lawn round it.
		BlockPos pad = cottage.offset(-8, -1, -18), padEnd = cottage.offset(8, -1, -1);
		cmd("fill %d %d %d %d %d %d grass_block", pad.getX(), pad.getY(), pad.getZ(), padEnd.getX(), padEnd.getY(), padEnd.getZ());
		cmd("kill @e[type=item]");
	}

	/**
	 * 1. The empty lot, an inventory of odd stacks: how much of the cottage is in there? The cursor
	 * looks over the plank stacks; then the inventory closes and the list in the corner says exactly.
	 */
	private void jumble() {
		clearCottage();
		ready(pathStand(), porchLook());
		cameraDistance(WIDE);
		giveJumble();
		film.ctx.waitTicks(45);
		gui(5);
		film.ctx.getInput().pressKey(o -> o.keyInventory);
		film.until0(mc -> mc.gui.screen() instanceof InventoryScreen);
		film.ctx.getInput().setCursorPos(540, 1300);
		resetCounts();
		record(4);
		for (int[] at : slotsWith(Items.SPRUCE_PLANKS)) {
			glideTo(at[0], at[1], 6 + film.jitter.nextInt(3));
			record(7 + film.jitter.nextInt(4));
		}
		film.ctx.runOnClient(mc -> mc.player.closeContainer());
		film.log("inventory_close", "");
		cottageList();
		film.ctx.runOnClient(Counter::update);
		Film.check(film.ctx.computeOnClient(mc -> Lists.active().entries.stream().anyMatch(e -> e.key().equals("minecraft:spruce_planks") && e.have == 46)),
				"the list counts the 46 planks in the jumble");
		record(40);
	}

	/**
	 * 2. At the timber house's storage chest: open it and most lines fill; chop the last two logs;
	 * walk on over the last two lanterns, and the list finishes.
	 */
	private void gather() {
		BlockPos box = timber.offset(0, 0, 3);
		BlockPos other = box.east();
		BlockPos log1 = timber.offset(-3, 0, 6), log2 = timber.offset(-4, 0, 6);
		// Clear the flowers and grass west of the path for the logs and lanterns; the lawn itself stays as it is.
		BlockPos a = timber.offset(-9, 0, 4), b = timber.offset(-1, 3, 9);
		cmd("fill %d %d %d %d %d %d air", a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
		Vec3 edge = new Vec3(box.getX() + 0.12, box.getY() + 0.7, box.getZ() + 0.94);
		ready(off(timber, 0.5, 0, 6.3), edge);
		StringBuilder items = new StringBuilder();
		int slot = 0;
		for (Need n : NEEDS) {
			for (int left = n.chest(); left > 0; left -= 64) {
				items.append(items.isEmpty() ? "" : ",").append(String.format(Locale.ROOT, "{Slot:%db,id:\"%s\",count:%d}", slot++, id(n.item()), Math.min(64, left)));
			}
		}
		cmd("setblock %d %d %d chest[facing=south,type=right]{Items:[%s]}", box.getX(), box.getY(), box.getZ(), items);
		cmd("setblock %d %d %d chest[facing=south,type=left]", other.getX(), other.getY(), other.getZ());
		cmd("setblock %d %d %d spruce_log[axis=x]", log1.getX(), log1.getY(), log1.getZ());
		cmd("setblock %d %d %d spruce_log[axis=x]", log2.getX(), log2.getY(), log2.getZ());
		for (double east : new double[] { -6.2, -7.1 }) {
			Vec3 at = off(timber, east, 0.05, 6.5);
			cmd("summon item %.2f %.2f %.2f {Item:{id:\"minecraft:lantern\",count:1},PickupDelay:0s,Motion:[0d,0d,0d]}", at.x, at.y, at.z);
		}
		// A log in one chop, without a Haste icon in the corner.
		cmd("attribute @p minecraft:block_break_speed base set 10");
		giveJumble();
		cottageList();
		film.ctx.getInput().pressKey(o -> o.keyHotbarSlots[0]);
		gui(4);
		film.ctx.waitTicks(45);
		film.turnHead(edge);
		looking = edge;
		film.ctx.waitTicks(3);
		Film.check(film.ctx.computeOnClient(mc -> mc.hitResult instanceof BlockHitResult hit
				&& (hit.getBlockPos().equals(box) || hit.getBlockPos().equals(other))), "the crosshair is on the chest");
		resetCounts();
		record(6);
		film.ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
		film.log("chest_open", "use chest");
		until(mc -> mc.gui.screen() instanceof ContainerScreen);
		until(mc -> Lists.active().entries.stream().filter(Entry::done).count() >= 5);
		record(16);
		film.ctx.runOnClient(mc -> mc.player.closeContainer());
		film.log("chest_close", "close chest");
		record(3);
		// The two logs lie end to end to the west: chop the near one, then the far one.
		chop(log1, new Vec3(log1.getX() + 1.02, log1.getY() + 0.5, log1.getZ() + 0.85), 10);
		chop(log2, new Vec3(log2.getX() + 1.02, log2.getY() + 0.5, log2.getZ() + 0.85), 5);
		sweep(off(timber, -14, 1.4, 6.5), 6);
		film.ctx.getInput().holdKey(o -> o.keyUp);
		film.log("walk", "");
		try {
			until(mc -> Boolean.TRUE.equals(Lists.active().wasFinished));
		} finally {
			film.ctx.getInput().releaseKey(o -> o.keyUp);
		}
		record(30);
		cmd("attribute @p minecraft:block_break_speed base set 1");
		cmd("fill %d %d %d %d %d %d air", box.getX(), box.getY(), box.getZ(), other.getX(), other.getY(), other.getZ());
	}

	/** Turns to a log, checks the crosshair is on it, and breaks it with one swing. */
	private void chop(BlockPos log, Vec3 aim, int frames) {
		sweep(aim, frames);
		String on = film.ctx.computeOnClient(mc -> mc.hitResult instanceof BlockHitResult hit ? hit.getBlockPos().toShortString() : "nothing");
		Film.check(on.equals(log.toShortString()), "the crosshair is on the log at " + log.toShortString() + " (it is on " + on + ")");
		film.ctx.getInput().pressKey(o -> o.keyAttack);
		film.log("chop", log.toShortString());
		record(6);
	}

	private Vec3 looking;

	/** Turns the head from where it looks now to {@code to} over {@code n} frames, eased. */
	private void sweep(Vec3 to, int n) {
		Vec3 from = looking == null ? to : looking;
		for (int i = 1; i <= n; i++) {
			film.turnHead(from.lerp(to, Film.ease(i / (double) n)));
			record(1);
		}
		looking = to;
	}

	/**
	 * 3. The cottage, built, and everything gathered: close behind her on the path, the camera
	 * eases back until the whole cottage is in view, ending where the jumble's shot begins.
	 */
	private void build() {
		ready(pathStand(), porchLook());
		cmd("clear @a");
		for (Need n : NEEDS) {
			cmd("give @a %s %d", id(n.item()), n.need());
		}
		cottageList();
		gui(4);
		cameraDistance(3);
		film.ctx.waitTicks(45);
		film.ctx.runOnClient(Counter::update);
		Film.check(film.ctx.computeOnClient(mc -> Lists.active().entries.stream().allMatch(Entry::done)),
				"everything on the cottage list is gathered");
		resetCounts();
		record(6);
		int n = 50;
		for (int i = 1; i <= n; i++) {
			cameraDistance(3 + (WIDE - 3) * Film.ease(i / (double) n));
			record(1);
		}
		record(30);
	}

	/** The middle of each slot holding {@code item} on the open inventory, in GUI coordinates, in order. */
	private List<int[]> slotsWith(Item item) {
		return film.ctx.computeOnClient(mc -> {
			AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.gui.screen();
			int left = (int) field(screen, AbstractContainerScreen.class, "leftPos");
			int top = (int) field(screen, AbstractContainerScreen.class, "topPos");
			List<int[]> found = new java.util.ArrayList<>();
			for (Slot slot : screen.getMenu().slots) {
				if (slot.getItem().is(item)) {
					found.add(new int[] { left + slot.x + 8, top + slot.y + 8 });
				}
			}
			return found;
		});
	}

	private static Object field(Object owner, Class<?> declaring, String name) {
		try {
			var f = declaring.getDeclaredField(name);
			f.setAccessible(true);
			return f.get(owner);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("no field " + name + " on " + declaring.getSimpleName(), e);
		}
	}

	// ------------------------------------------------------------------ filming, with the sound marks

	/** Films {@code n} frames, logging pickups, counts and the finish for the sound pass. */
	private void record(int n) {
		for (int i = 0; i < n; i++) {
			marks();
			// At this GUI scale the finish pop-up lands on the list, which turns green and says so itself.
			film.ctx.runOnClient(mc -> mc.gui.toastManager().clear());
			film.record(1);
		}
	}

	private void until(java.util.function.Predicate<net.minecraft.client.Minecraft> done) {
		for (int i = 0; i < 200; i++) {
			if (film.ctx.computeOnClient(done::test)) {
				return;
			}
			record(1);
		}
		throw new AssertionError("waited twenty seconds");
	}

	private void resetCounts() {
		lastHave = -1;
	}

	private void marks() {
		String state = film.ctx.computeOnClient(mc -> {
			int have = Lists.active().entries.stream().mapToInt(e -> e.have).sum();
			int carried = 0;
			for (ItemStack stack : mc.player.getInventory().getNonEquipmentItems()) {
				carried += stack.getCount();
			}
			return have + "\t" + carried + "\t" + Boolean.TRUE.equals(Lists.active().wasFinished);
		});
		String[] parts = state.split("\t");
		int have = Integer.parseInt(parts[0]);
		int carried = Integer.parseInt(parts[1]);
		boolean finished = Boolean.parseBoolean(parts[2]);
		if (lastHave >= 0) {
			if (carried > lastCarried) {
				film.log("pickup", "+" + (carried - lastCarried) + " items");
			}
			if (have != lastHave) {
				film.log("counted", lastHave + " -> " + have);
			}
			if (finished && !lastFinished) {
				film.log("finished", "list complete");
			}
		}
		lastHave = have;
		lastCarried = carried;
		lastFinished = finished;
	}

	/** Moves the cursor to a GUI point over a few uneven frames. */
	private void glideTo(int guiX, int guiY, int steps) {
		double scale = film.ctx.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
		double fromX = film.ctx.computeOnClient(mc -> mc.mouseHandler.xpos());
		double fromY = film.ctx.computeOnClient(mc -> mc.mouseHandler.ypos());
		for (int i = 1; i <= steps; i++) {
			double t = Film.ease((double) i / steps);
			film.ctx.getInput().setCursorPos(fromX + (guiX * scale - fromX) * t, fromY + (guiY * scale - fromY) * t);
			record(1);
		}
	}
}
