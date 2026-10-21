package com.gabbymaybelle.pocketlist;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Films the long-form guide with {@link Film}, in a 1600x900 window (scaled up afterwards): one
 * build from start to finish. Gabby lives in the cozy cottage and finishes the timber house next
 * door, 45 blocks east, by giving it its front porch. The cottage is home and storage, the timber
 * house the build site, and each feature turns up where a player would meet it.
 *
 * <p>Only runs with `./gradlew :fabric:runClientGameTest -PvideoGuide`, in the saved backdrop
 * world ({@code -PvideoWorld=<zip>}, with kept "cottage" and "timber" spots); frames go in the game
 * folder's `video_guide`. Every other -Pvideo... property is passed on to {@link Film}. The
 * clipboard is put back as it was when the take ends: Paste List and Copy List use it.
 */
public class VideoGuide implements FabricClientGameTest {
	/** Names the system properties: pocketlist.guide.videoTour, pocketlist.guide.videoScenes... */
	private static final String PREFIX = "pocketlist.guide";

	/** The garden list, as it might be copied out of a notes app. */
	static final String GARDEN = """
			- 4 poppy
			- 3 cornflower
			- 3 azure bluet
			""";

	private Film film;
	private BlockPos cottage;
	private BlockPos timber;
	/** The timber house's porch as the saved world has it, as offsets from the timber spot. */
	private List<Placed> porch = List.of();
	private Vec3 looking;
	private int lastHave;
	private int lastCarried;
	private boolean lastFinished;

	private record Placed(BlockPos offset, BlockState state) {
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!Film.on(PREFIX)) {
			return;
		}
		String clipboard = context.computeOnClient(mc -> mc.keyboardHandler.getClipboard());
		litematicaFile(false);
		try {
			film = new Film(context, PREFIX, "video_guide");
			film.take(() -> film.newWorld("pocket list guide"), this::stage, this::scenes);
		} finally {
			context.runOnClient(mc -> mc.keyboardHandler.setClipboard(clipboard));
			litematicaFile(false);
		}
	}

	// ------------------------------------------------------------------ the set

	/**
	 * A new world (no saved one given): both spots on the ground a little way from spawn, 45 blocks
	 * apart, with the houses built there when their build files are passed in
	 * ({@code -PvideoCottage=<cottage.mcfunction> -PvideoTimber=<timber.mcfunction>}). Without them
	 * the scenes run in a plain spot, and the ones that need a house say so.
	 */
	private void stage() {
		var spawn = film.server.computeOnServer(s -> s.overworld().getRespawnData().pos());
		int x = spawn.getX() + 20, z = spawn.getZ();
		BlockPos home = new BlockPos(x, film.ground(x, z), z);
		BlockPos site = new BlockPos(x + 45, film.ground(x + 45, z), z);
		film.keep("cottage", home.getX() + " " + home.getY() + " " + home.getZ());
		film.keep("timber", site.getX() + " " + site.getY() + " " + site.getZ());
		build(film.prop("videoCottage", ""), home);
		build(film.prop("videoTimber", ""), site);
	}

	/** Replays a house's build file at its spot (where its front path ends), if one was given. */
	private void build(String file, BlockPos at) {
		if (file.isEmpty()) {
			film.note("house", "none given for " + at.toShortString());
			return;
		}
		try {
			for (String line : Files.readAllLines(Path.of(file))) {
				if (!line.isBlank()) {
					film.server.runCommand(String.format(Locale.ROOT, "execute positioned %d %d %d run %s", at.getX(), at.getY(), at.getZ(), line));
				}
			}
		} catch (IOException e) {
			throw new AssertionError("couldn't read " + file, e);
		}
		film.note("house", file + " at " + at.toShortString());
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
	 * Survival, first person, HUD on, daylight, standing at {@code feet} looking at {@code look},
	 * with no lists, no remembered chests and an empty inventory.
	 */
	private void ready(Vec3 feet, Vec3 look) {
		film.ctx.setScreen(() -> null);
		film.hud(true);
		film.survival();
		film.thirdPerson(false, 4);
		film.normalGui();
		cmd("time set 6000");
		cmd("kill @e[type=item]");
		cmd("clear @a");
		film.camera(feet, look);
		film.ctx.waitTicks(3);
		film.turnHead(look);
		looking = look;
		forgetChests();
		film.ctx.runOnClient(mc -> {
			Lists.data().lists.clear();
			Lists.data().active = 0;
			Lists.data().welcomed = true;
			Lists.data().hud = true;
			Lists.data().hudPosition = Lists.HudPosition.TOP_LEFT;
			Lists.data().hudLines = 8;
			Lists.data().countChests = true;
			mc.player.getInventory().setSelectedSlot(8);
		});
	}

	/** Pocket List remembers chests for the whole take; each scene starts having opened none. */
	private void forgetChests() {
		film.ctx.runOnClient(mc -> ((Map<?, ?>) field(null, ChestMemory.class, "REMEMBERED")).clear());
	}

	// ------------------------------------------------------------------ the porch

	/** What the timber house's porch is built from (its build file's "door, porch, path" section). */
	private static final Set<Block> PORCH_BLOCKS = Set.of(Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_LOG,
			Blocks.SPRUCE_WALL_SIGN, Blocks.POLISHED_BLACKSTONE_BUTTON, Blocks.CAMPFIRE, Blocks.FLOWERING_AZALEA_LEAVES, Blocks.LANTERN);
	/** Placed after the blocks they hang from or sit on. */
	private static final Set<Block> ATTACHED = Set.of(Blocks.SPRUCE_WALL_SIGN, Blocks.POLISHED_BLACKSTONE_BUTTON,
			Blocks.FLOWERING_AZALEA_LEAVES, Blocks.LANTERN);
	/** The porch list's order: the big things first. */
	private static final List<Item> PORCH_ORDER = List.of(Items.CAMPFIRE, Items.LANTERN, Items.SPRUCE_PLANKS, Items.SPRUCE_LOG,
			Items.SPRUCE_STAIRS, Items.SPRUCE_SIGN, Items.POLISHED_BLACKSTONE_BUTTON);

	/**
	 * Reads the porch from the world: x -3..3, y 0..5, z -6..-3 from the timber spot, only its own
	 * blocks (the flower beds beside it stay), planks only on the floor, and nothing above the back
	 * row of the floor, where the upper storey juts out.
	 */
	private List<Placed> readPorch() {
		BlockPos at = timber;
		return film.server.computeOnServer(s -> {
			var level = s.overworld();
			List<Placed> found = new ArrayList<>();
			for (int y = 0; y <= 5; y++) {
				for (int dz = -6; dz <= -3; dz++) {
					for (int dx = -3; dx <= 3; dx++) {
						if (dz == -6 && y > 0) {
							continue;
						}
						BlockState state = level.getBlockState(at.offset(dx, y, dz));
						if (!PORCH_BLOCKS.contains(state.getBlock()) || (state.is(Blocks.SPRUCE_PLANKS) && y > 0)) {
							continue;
						}
						found.add(new Placed(new BlockPos(dx, y, dz), state));
					}
				}
			}
			return found;
		});
	}

	/** The porch's blocks in building order: from the floor up, then what hangs or sits on them. */
	private List<Placed> buildOrder() {
		List<Placed> order = new ArrayList<>(porch.stream().filter(p -> !ATTACHED.contains(p.state().getBlock())).toList());
		order.addAll(porch.stream().filter(p -> ATTACHED.contains(p.state().getBlock())).toList());
		return order;
	}

	/** Puts blocks in place without neighbour updates, so nothing pops off and drops. */
	private void place(List<Placed> blocks, boolean remove) {
		BlockPos at = timber;
		film.server.runOnServer(s -> {
			var level = s.overworld();
			for (Placed p : blocks) {
				// 2 | 16: tell the clients, but don't update neighbours (a sign would pop off its post).
				level.setBlock(at.offset(p.offset()), remove ? Blocks.AIR.defaultBlockState() : p.state(), 18);
			}
		});
	}

	private void porchBuilt(boolean built) {
		List<Placed> order = buildOrder();
		if (!built) {
			order = new ArrayList<>(order.reversed());
		}
		place(order, !built);
		cmd("kill @e[type=item]");
	}

	/** What the porch takes, item by item, counted from its blocks (the azalea grows there by itself). */
	private Map<Item, Integer> porchNeeds() {
		Map<Item, Integer> counted = new LinkedHashMap<>();
		for (Placed p : porch) {
			if (!p.state().is(Blocks.FLOWERING_AZALEA_LEAVES)) {
				counted.merge(p.state().getBlock().asItem(), 1, Integer::sum);
			}
		}
		Map<Item, Integer> ordered = new LinkedHashMap<>();
		for (Item item : PORCH_ORDER) {
			if (counted.containsKey(item)) {
				ordered.put(item, counted.remove(item));
			}
		}
		ordered.putAll(counted);
		return ordered;
	}

	private void porchList() {
		Map<Item, Integer> needs = porchNeeds();
		film.ctx.runOnClient(mc -> {
			Lists.data().lists.clear();
			Lists.addList("Timber porch", needs.entrySet().stream().map(e -> Entry.ofItem(e.getKey(), e.getValue())).toList());
		});
	}

	private void giveAll(Map<Item, Integer> items) {
		for (var e : items.entrySet()) {
			cmd("give @a %s %d", id(e.getKey()), e.getValue());
		}
	}

	private static String id(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	// ------------------------------------------------------------------ the cottage's furniture

	/**
	 * The nearest block of this kind inside the cottage (x -8..8, z -18..-1 from its spot), found in
	 * the world rather than worked out from the build file.
	 */
	private BlockPos inCottage(Block block, Vec3 near) {
		BlockPos at = cottage;
		BlockPos found = film.server.computeOnServer(s -> {
			var level = s.overworld();
			BlockPos best = null;
			for (BlockPos p : BlockPos.betweenClosed(at.offset(-8, 0, -18), at.offset(8, 6, -1))) {
				if (level.getBlockState(p).is(block) && (best == null || Vec3.atCenterOf(p).distanceTo(near) < Vec3.atCenterOf(best).distanceTo(near))) {
					best = p.immutable();
				}
			}
			return best;
		});
		Film.check(found != null, "the cottage has a " + BuiltInRegistries.BLOCK.getKey(block).getPath());
		return found;
	}

	/** Where to stand in the fireplace room (the cottage README's stage spot). */
	private Vec3 fireplaceRoom() {
		return off(cottage, 2.5, 1, -10);
	}

	// ------------------------------------------------------------------ the scenes

	/**
	 * Filmed in story order, except the two shots of the finished porch, which go first: every
	 * scene from "plan" on starts with the porch cleared away, and "build" puts it back.
	 */
	private void scenes() {
		cottage = spot(film.kept("cottage"));
		timber = spot(film.kept("timber"));
		porch = readPorch();
		Map<String, Integer> kinds = new LinkedHashMap<>();
		porch.forEach(p -> kinds.merge(BuiltInRegistries.BLOCK.getKey(p.state().getBlock()).getPath(), 1, Integer::sum));
		film.note("porch", porch.size() + " blocks " + kinds);
		Film.check(!porch.isEmpty(), "the timber house has its porch in the saved world");
		film.window(1600, 900, 3);
		if (film.scouting()) {
			// The sets first, before any action, so Gabby judges where things happen before how.
			film.scene("x_set_porch", () -> {
				porchBuilt(false);
				preview(off(timber, 9, 4, 8), off(timber, 0.5, 2, -5));
			});
			film.scene("x_set_kitchen", () -> preview(fireplaceRoom(), Vec3.atCenterOf(inCottage(Blocks.CHEST, fireplaceRoom()))));
			film.scene("x_set_loft", () -> preview(off(cottage, -3, 5, -12), off(cottage, 0.5, 5.4, -8.5)));
			film.scene("x_set_lawn", () -> {
				flowerRow();
				preview(off(cottage, 0.5, 4, 3), off(cottage, 0.5, 0, 9));
			});
		}
		film.scene("w_payoff", this::payoff);
		film.scene("w_end", this::end);
		film.scene("w_plan", this::plan);
		film.scene("w_breakdown", this::breakDown);
		film.scene("w_storage", this::storage);
		film.scene("w_paste", this::paste);
		film.scene("w_gather", this::gather);
		film.scene("w_litematica", this::litematica);
		film.scene("w_build", this::build);
		film.scene("w_settings", this::settings);
		porchBuilt(true);
	}

	/** 1. The finished porch at sunset, from the storybook corner: the last lantern arrives and the list chimes. */
	private void payoff() {
		porchBuilt(true);
		ready(off(timber, 12.5, 0.3, 5.5), off(timber, 1.5, 6, -9.5));
		cmd("time set 12800");
		porchList();
		Map<Item, Integer> all = porchNeeds();
		all.merge(Items.LANTERN, -1, Integer::sum);
		giveAll(all);
		film.ctx.waitTicks(45);
		resetCounts();
		record(30);
		cmd("give @a lantern 1");
		film.log("pickup", "the last lantern");
		until(mc -> Boolean.TRUE.equals(Lists.active().wasFinished));
		record(60);
	}

	/** 10. Dusk on the lawn, both houses in the frame, lanterns lit; no HUD. */
	private void end() {
		porchBuilt(true);
		ready(off(cottage, 22, 0, 16), off(cottage, 22, 3, -10));
		film.hud(false);
		film.spectator();
		cmd("time set 12900");
		// The lawn runs about 20 blocks south before the woods begin, and pulling back to 19 ended
		// in their leaves: rise more than pull back, and stay well short of them.
		Vec3 from = off(cottage, 22, 4, 13), to = off(cottage, 22, 9, 15);
		Vec3 look = off(cottage, 22, 4, -10), lookTo = off(cottage, 22, 2, -10);
		film.camera(from, look);
		film.dolly(120, from, to, look, lookTo);
		record(20);
	}

	/**
	 * 2. The bare porch site: she opens the list, names it "Timber porch" and adds campfires and
	 * lanterns by name. The rest of the list is filled in off camera.
	 */
	private void plan() {
		porchBuilt(false);
		ready(off(timber, 0.5, 0, 2.5), off(timber, 0.5, 2, -6));
		resetCounts();
		record(15);
		openList();
		rename("Timber porch");
		record(10);
		addByName("campfire", "Campfire", 10);
		addByName("lantern", "Lantern", 4);
		record(30);
		closeScreen();
		record(20);
	}

	/**
	 * 3. Back home at the cottage's crafting table: Break down swaps the campfires for logs, sticks
	 * and coal, and the lanterns for iron nuggets and torches.
	 */
	private void breakDown() {
		porchBuilt(false);
		BlockPos table = inCottage(Blocks.CRAFTING_TABLE, fireplaceRoom());
		ready(fireplaceRoom(), Vec3.atCenterOf(table));
		porchList();
		film.aim(table);
		resetCounts();
		record(15);
		openList();
		record(10);
		breakDownLine(Items.CAMPFIRE);
		record(20);
		breakDownLine(Items.LANTERN);
		Film.check(film.ctx.computeOnClient(mc -> Lists.active().entries.stream().anyMatch(e -> e.key().equals("minecraft:iron_nugget"))),
				"the lanterns broke down into iron nuggets");
		record(40);
		closeScreen();
		record(15);
	}

	/**
	 * 4. The cottage's chest and a barrel: the spruce, coal, nuggets and torches are already at
	 * home, so most lines fill when they're opened, and keep counting from across the room.
	 */
	private void storage() {
		porchBuilt(false);
		BlockPos chest = inCottage(Blocks.CHEST, fireplaceRoom());
		BlockPos barrel = inCottage(Blocks.BARREL, fireplaceRoom());
		ready(fireplaceRoom(), Vec3.atCenterOf(chest));
		// The porch list as broken down in the last scene, and what's at home for it: everything but
		// the lanterns' torches, which she crafts once the garden is picked.
		List<Entry> needs = porchBrokenDown();
		List<String> inChest = new ArrayList<>(), inBarrel = new ArrayList<>();
		for (Entry e : needs) {
			if (e.matches(Items.TORCH)) {
				continue;
			}
			boolean small = e.matches(Items.IRON_NUGGET) || e.matches(Items.TORCH) || e.matches(Items.POLISHED_BLACKSTONE_BUTTON);
			List<String> into = small ? inBarrel : inChest;
			for (int left = e.need; left > 0; left -= 64) {
				into.add(String.format(Locale.ROOT, "{Slot:%db,id:\"%s\",count:%d}", into.size() + (small ? 4 : 0), id(stored(e)), Math.min(64, left)));
			}
		}
		// Everyday things, so the storage looks lived in.
		inChest.add("{Slot:20b,id:\"minecraft:bread\",count:9}");
		inChest.add("{Slot:22b,id:\"minecraft:wheat_seeds\",count:23}");
		inChest.add("{Slot:24b,id:\"minecraft:iron_shovel\",count:1}");
		inBarrel.add("{Slot:0b,id:\"minecraft:bone_meal\",count:14}");
		inBarrel.add("{Slot:1b,id:\"minecraft:candle\",count:5}");
		fill(chest, inChest);
		fill(barrel, inBarrel);
		film.aim(chest);
		resetCounts();
		record(15);
		film.ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
		film.log("chest_open", "use chest");
		until(mc -> mc.gui.screen() instanceof ContainerScreen);
		record(35);
		film.ctx.runOnClient(mc -> mc.player.closeContainer());
		film.log("chest_close", "close chest");
		record(8);
		sweep(Vec3.atCenterOf(barrel), 12);
		film.aim(barrel);
		film.ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
		film.log("barrel_open", "use barrel");
		until(mc -> mc.gui.screen() != null);
		record(30);
		film.ctx.runOnClient(mc -> mc.player.closeContainer());
		film.log("barrel_close", "close barrel");
		record(8);
		// Walk back across the room: the chest and barrel still count.
		Vec3 before = film.ctx.computeOnClient(mc -> mc.player.position());
		film.ctx.getInput().holdKey(o -> o.keyDown);
		film.log("walk", "back away");
		try {
			record(24);
		} finally {
			film.ctx.getInput().releaseKey(o -> o.keyDown);
		}
		record(20);
		double walked = film.ctx.computeOnClient(mc -> mc.player.position().distanceTo(before));
		Film.check(walked > 2, "she backed away from the chest (walked " + walked + ")");
		Film.check(film.ctx.computeOnClient(mc -> Lists.active().entries.stream().allMatch(e -> e.done() != e.matches(Items.TORCH))),
				"everything on the porch list but the torches is at home");
	}

	/** The porch list with its campfires and lanterns broken down, as in the Break down scene; its lines. */
	private List<Entry> porchBrokenDown() {
		porchList();
		film.ctx.runOnClient(mc -> {
			for (Entry e : List.copyOf(Lists.active().entries)) {
				if (e.matches(Items.CAMPFIRE) || e.matches(Items.LANTERN)) {
					List<FoundRecipe> recipes = Recipes.forItem(mc, e.mainItem());
					if (!recipes.isEmpty()) {
						Lists.breakDown(e, recipes.getFirst());
					}
				}
			}
		});
		return film.ctx.computeOnClient(mc -> List.copyOf(Lists.active().entries));
	}

	private void fill(BlockPos container, List<String> items) {
		cmd("data merge block %d %d %d {Items:[%s]}", container.getX(), container.getY(), container.getZ(), String.join(",", items));
	}

	/** 5. On the cottage porch: Paste List makes the garden list from her notes, and she switches between the two. */
	private void paste() {
		porchBuilt(false);
		ready(off(cottage, 0.5, 1, -4), off(cottage, 0.5, 1.5, 4));
		porchList();
		film.ctx.runOnClient(mc -> mc.keyboardHandler.setClipboard(GARDEN));
		resetCounts();
		record(15);
		openList();
		record(10);
		clickWidget(w -> w.getMessage().getString().equals("Paste List"), "paste list");
		Film.check(film.ctx.computeOnClient(mc -> Lists.data().lists.size() == 2 && Lists.active().entries.size() == 3),
				"Paste List made a second list with three lines");
		record(25);
		// It's called "List 2": she names it.
		rename("Garden");
		record(20);
		clickWidget(w -> w.getMessage().getString().equals("<") && w.getY() == 24, "previous list");
		record(25);
		clickWidget(w -> w.getMessage().getString().equals(">") && w.getY() == 24, "next list");
		record(25);
		closeScreen();
		record(15);
	}

	/**
	 * 6. On the lawn south of the cottage: picking flowers ticks the garden list off, and the last
	 * one finishes it.
	 */
	private void gather() {
		porchBuilt(false);
		// She stands in line with the middle of the patch, so the walk passes within reach of every drop.
		Vec3 stand = off(cottage, 1.0, 0, 6.5);
		ready(stand, off(cottage, 1.0, 0.5, 11));
		// She has loaded up from home for the porch: all of it but the torches, and a spare stick
		// and coal to make them.
		for (Entry e : porchBrokenDown()) {
			if (!e.matches(Items.TORCH)) {
				cmd("give @a %s %d", id(stored(e)), e.need + (e.matches(Items.STICK) || e.matches(Items.COAL) ? 1 : 0));
			}
		}
		film.ctx.runOnClient(mc -> Lists.addList("Garden", List.of(Entry.ofItem(Items.POPPY, 4), Entry.ofItem(Items.CORNFLOWER, 3), Entry.ofItem(Items.AZURE_BLUET, 3))));
		// Four of them picked already; the rest grow in a short row ahead.
		cmd("give @a poppy 2");
		cmd("give @a cornflower 2");
		cmd("give @a azure_bluet 2");
		List<BlockPos> flowers = flowerRow();
		film.ctx.waitTicks(45);
		resetCounts();
		record(15);
		for (BlockPos flower : flowers) {
			// A flower sits off its block's centre by a random step, so aim at the flower itself.
			Vec3 at = film.ctx.computeOnClient(mc -> mc.level.getBlockState(flower).getShape(mc.level, flower).bounds().getCenter()
					.add(flower.getX(), flower.getY(), flower.getZ()));
			sweep(at, 8);
			film.turnHead(at);
			film.ctx.waitTicks(3);
			String on = film.ctx.computeOnClient(mc -> mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
					? hit.getBlockPos().toShortString() : "nothing");
			Film.check(on.equals(flower.toShortString()), "the player aims at the flower at " + flower.toShortString() + " (the crosshair is on " + on + ")");
			film.ctx.getInput().pressKey(o -> o.keyAttack);
			film.log("pick", flower.toShortString());
			record(3);
			// A click now and then doesn't break it; swing again rather than leave it standing.
			for (int tries = 0; tries < 2 && !film.ctx.computeOnClient(mc -> mc.level.getBlockState(flower).isAir()); tries++) {
				film.ctx.getInput().pressKey(o -> o.keyAttack);
				film.log("pick again", flower.toShortString());
				record(3);
			}
			Film.check(film.ctx.computeOnClient(mc -> mc.level.getBlockState(flower).isAir()), "the flower at " + flower.toShortString() + " is picked");
			record(5);
		}
		// Walk over them to pick them up.
		sweep(off(cottage, 1.0, 0.5, 14), 8);
		film.ctx.getInput().holdKey(o -> o.keyUp);
		film.log("walk", "");
		// Stop just past the patch: further south the lawn turns to tall grass.
		double stop = cottage.getZ() + 11.5;
		try {
			until(mc -> Boolean.TRUE.equals(Lists.active().wasFinished) || mc.player.getZ() > stop);
		} finally {
			film.ctx.getInput().releaseKey(o -> o.keyUp);
		}
		until(mc -> Boolean.TRUE.equals(Lists.active().wasFinished));
		record(40);
		// Back to the porch list: only the lanterns' torches are left. She makes them from the
		// spare stick and coal in her inventory's crafting grid, and the porch is all gathered too.
		// Count the porch list straight away, as the list screen does when she switches there.
		film.ctx.runOnClient(mc -> {
			Lists.data().active = 0;
			Counter.update(mc);
		});
		film.log("switch", "Timber porch");
		Film.check(film.ctx.computeOnClient(mc -> Lists.active().name.equals("Timber porch")
				&& Lists.active().entries.stream().allMatch(e -> e.done() != e.matches(Items.TORCH))), "only the torches are left on the porch list");
		// One craft makes four torches: enough for the porch's four lanterns.
		Film.check(film.ctx.computeOnClient(mc -> Lists.active().entries.stream().filter(e -> e.matches(Items.TORCH)).allMatch(e -> e.need <= 4)),
				"one craft covers the torches");
		record(30);
		film.ctx.getInput().pressKey(o -> o.keyInventory);
		until(mc -> mc.gui.screen() instanceof InventoryScreen);
		film.log("inventory_open", "");
		record(8);
		// Inventory menu slots: 0 the result, 1 and 2 the grid's top row, 3 and 4 its bottom row.
		placeOne(Items.COAL, 1);
		placeOne(Items.STICK, 3);
		record(6);
		clickSlot(0, InputConstants.MOUSE_BUTTON_LEFT, "take torches");
		clickSlot(emptySlot(), InputConstants.MOUSE_BUTTON_LEFT, "put torches away");
		record(10);
		film.ctx.runOnClient(mc -> mc.player.closeContainer());
		film.log("inventory_close", "");
		until(mc -> Boolean.TRUE.equals(Lists.active().wasFinished));
		record(50);
	}

	/** Picks up the stack of {@code item}, drops one into the inventory menu's slot {@code into}, and puts the rest back. */
	private void placeOne(Item item, int into) {
		int from = film.ctx.computeOnClient(mc -> {
			var slots = ((AbstractContainerScreen<?>) mc.gui.screen()).getMenu().slots;
			for (int i = 9; i < slots.size(); i++) {
				if (slots.get(i).getItem().is(item)) {
					return i;
				}
			}
			return -1;
		});
		Film.check(from >= 0, "she has " + id(item) + " to craft with");
		clickSlot(from, InputConstants.MOUSE_BUTTON_LEFT, "pick up " + id(item));
		clickSlot(into, InputConstants.MOUSE_BUTTON_RIGHT, "place one " + id(item));
		clickSlot(from, InputConstants.MOUSE_BUTTON_LEFT, "put back " + id(item));
	}

	/** An empty slot in the main inventory (menu slots 9 to 35). */
	private int emptySlot() {
		int empty = film.ctx.computeOnClient(mc -> {
			var slots = ((AbstractContainerScreen<?>) mc.gui.screen()).getMenu().slots;
			for (int i = 9; i <= 35; i++) {
				if (slots.get(i).getItem().isEmpty()) {
					return i;
				}
			}
			return -1;
		});
		Film.check(empty >= 0, "there's an empty slot for the torches");
		return empty;
	}

	/** Glides the pointer to the open container screen's slot {@code index} and clicks it with {@code button}. */
	private void clickSlot(int index, int button, String what) {
		int[] at = film.ctx.computeOnClient(mc -> {
			AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.gui.screen();
			Slot slot = screen.getMenu().slots.get(index);
			return new int[] { (int) field(screen, "leftPos") + slot.x + 8, (int) field(screen, "topPos") + slot.y + 8 };
		});
		glideTo(at[0], at[1], 7 + film.jitter.nextInt(3));
		record(3);
		film.ctx.getInput().pressMouse(button);
		film.log("click", what);
		record(4);
	}

	private static Object field(AbstractContainerScreen<?> screen, String name) {
		try {
			var f = AbstractContainerScreen.class.getDeclaredField(name);
			f.setAccessible(true);
			return f.get(screen);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("no field " + name + " on the container screen", e);
		}
	}

	/**
	 * The four flowers still to pick, in a small patch straight ahead on the lawn south of the
	 * cottage, nearest first. A wider row left a flower out of reach of the walk that picks them up.
	 */
	private List<BlockPos> flowerRow() {
		String[] row = { "poppy", "cornflower", "poppy", "azure_bluet" };
		int[][] spots = { { 0, 8 }, { 1, 8 }, { 0, 10 }, { 1, 10 } };
		List<BlockPos> flowers = new ArrayList<>();
		for (int i = 0; i < row.length; i++) {
			BlockPos at = cottage.offset(spots[i][0], 0, spots[i][1]);
			cmd("setblock %d %d %d %s", at.getX(), at.getY(), at.getZ(), row[i]);
			flowers.add(at);
		}
		return flowers;
	}

	/** Scout only: a still of a set from a spectator camera, no HUD. */
	private void preview(Vec3 eye, Vec3 look) {
		film.ctx.setScreen(() -> null);
		film.hud(false);
		film.spectator();
		cmd("time set 6000");
		film.camera(eye, look);
		film.record(2);
	}

	/**
	 * 7. In the cottage loft, for builders with a schematic: Litematica's material list for the
	 * porch, written to a file, becomes a list with From Litematica. Litematica itself isn't loaded:
	 * the button shows when its file is there, and the file is the same table its Write to file
	 * button makes.
	 */
	private void litematica() {
		porchBuilt(false);
		ready(off(cottage, -2, 5, -11), off(cottage, 0.5, 5.6, -8.5));
		film.ctx.runOnClient(mc -> Lists.data().lists.clear());
		litematicaFile(true);
		resetCounts();
		record(15);
		openList();
		record(10);
		clickWidget(w -> w.getMessage().getString().equals("From Litematica"), "from litematica");
		Film.check(film.ctx.computeOnClient(mc -> Lists.active().name.equals("Timber porch") && !Lists.active().entries.isEmpty()),
				"From Litematica made the \"Timber porch\" list");
		record(45);
		closeScreen();
		record(15);
		litematicaFile(false);
	}

	/** Writes (or removes) a material list as Litematica's Write to file button does, for the porch. */
	private void litematicaFile(boolean write) {
		Path dir = FabricLoader.getInstance().getConfigDir().resolve("litematica");
		Path file = dir.resolve("material_list_2026-10-20_12.00.00.txt");
		try {
			// Any material list left by the game test would show the From Litematica button in every scene.
			if (Files.isDirectory(dir)) {
				try (var old = Files.list(dir)) {
					for (Path p : old.filter(p -> p.getFileName().toString().startsWith("material_list")).toList()) {
						Files.delete(p);
					}
				}
			}
			if (!write) {
				return;
			}
			Files.createDirectories(dir);
			String line = "+---------------------------+-------+---------+-----------+";
			StringBuilder table = new StringBuilder();
			table.append(line).append('\n').append(String.format(Locale.ROOT, "| %-57s |%n", "Timber porch")).append(line).append('\n');
			table.append("| Item                      | Total | Missing | Available |\n").append(line).append('\n');
			for (var e : porchNeeds().entrySet()) {
				table.append(String.format(Locale.ROOT, "| %-25s | %5d | %7d | %9d |%n", new ItemStack(e.getKey()).getHoverName().getString(), e.getValue(), e.getValue(), 0));
			}
			table.append(line).append('\n');
			Files.writeString(file, table.toString());
		} catch (IOException e) {
			throw new AssertionError("couldn't write the material list", e);
		}
	}

	/**
	 * 8. At the timber house with everything carried over: Clear Done tidies the gathered list away,
	 * then the porch goes up block by block in a quick time-lapse.
	 */
	private void build() {
		porchBuilt(false);
		ready(off(timber, 0.5, 0, 3.5), off(timber, 0.5, 2, -5));
		porchList();
		giveAll(porchNeeds());
		film.ctx.waitTicks(45);
		Film.check(film.ctx.computeOnClient(mc -> {
			Counter.update(mc);
			return Lists.active().entries.stream().allMatch(Entry::done);
		}), "everything for the porch is carried over");
		resetCounts();
		record(20);
		// All gathered: Clear Done tidies the list away before she builds (building uses the items up,
		// so afterwards the lines would no longer be done).
		openList();
		record(10);
		clickWidget(w -> w.getMessage().getString().equals("Clear Done"), "clear done");
		Film.check(film.ctx.computeOnClient(mc -> Lists.active().entries.isEmpty()), "Clear Done empties a gathered list");
		record(25);
		closeScreen();
		record(10);
		film.thirdPerson(true, 6);
		record(10);
		List<Placed> order = buildOrder();
		for (int i = 0; i < order.size(); i++) {
			Placed p = order.get(i);
			if (!p.state().is(Blocks.FLOWERING_AZALEA_LEAVES)) {
				Item item = p.state().getBlock().asItem();
				cmd("clear @a %s 1", id(item));
			}
			place(List.of(p), false);
			film.ctx.runOnClient(VideoGuide::swingArm);
			film.log("place", BuiltInRegistries.BLOCK.getKey(p.state().getBlock()).getPath());
			record(i % 2 == 0 ? 2 : 1);
		}
		record(40);
	}

	/**
	 * Swings the player's main hand. 26.2 has {@code swing(hand)}; 26.3 only {@code swing(hand,
	 * SwingAnimation, boolean)}, whose animation type 26.2 lacks, so the call is looked up by name.
	 */
	private static void swingArm(Minecraft mc) {
		for (var m : net.minecraft.world.entity.LivingEntity.class.getMethods()) {
			Class<?>[] types = m.getParameterTypes();
			if (!m.getName().equals("swing") || types.length == 0 || types[0] != net.minecraft.world.InteractionHand.class) {
				continue;
			}
			try {
				if (types.length == 1) {
					m.invoke(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
					return;
				}
				if (types.length == 3 && types[2] == boolean.class) {
					m.invoke(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, types[1].getField("DEFAULT").get(null), false);
					return;
				}
			} catch (ReflectiveOperationException e) {
				throw new AssertionError("could not swing the arm", e);
			}
		}
		throw new AssertionError("no swing method on this Minecraft version");
	}

	/** 9. The timber house's sitting room: Settings, where the HUD goes round the corners. */
	private void settings() {
		porchBuilt(true);
		ready(off(timber, -1, 1, -12), off(timber, 5, 2.5, -12));
		porchList();
		resetCounts();
		record(15);
		openList();
		record(10);
		clickWidget(w -> w.getMessage().getString().equals("Settings..."), "settings");
		until(mc -> mc.gui.screen() instanceof PocketListSettingsScreen);
		record(15);
		for (int i = 0; i < 4; i++) {
			clickWidget(w -> w.getMessage().getString().startsWith("HUD Position"), "hud position");
			record(14);
		}
		Film.check(film.ctx.computeOnClient(mc -> Lists.data().hudPosition == Lists.HudPosition.TOP_LEFT),
				"four clicks bring the HUD back to the top left");
		record(15);
		closeScreen();
		closeScreen();
		record(20);
	}

	// ------------------------------------------------------------------ the list screen

	private void openList() {
		film.ctx.getInput().pressKey(PocketList.openKey);
		film.log("key", "open");
		until(mc -> mc.gui.screen() instanceof PocketListScreen);
		record(8);
	}

	private void closeScreen() {
		film.ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
		film.log("key", "close");
		record(6);
	}

	/** Clicks the list's name box, selects the name and types a new one over it. */
	private void rename(String to) {
		EditBox name = film.ctx.computeOnClient(mc -> allWidgets(mc).stream()
				.filter(w -> w instanceof EditBox b && b.getY() == 24).map(w -> (EditBox) w).findFirst().orElse(null));
		Film.check(name != null, "the list screen has its name box");
		clickAt(name.getX() + name.getWidth() - 8, name.getY() + 10, "name box");
		film.ctx.runOnClient(mc -> {
			name.moveCursorToEnd(false);
			name.setHighlightPos(0);
		});
		record(6);
		typeText(to);
		Film.check(film.ctx.computeOnClient(mc -> Lists.active().name.equals(to)), "the list is named \"" + to + "\"");
	}

	/** What the cottage keeps for a line: spruce for any log, coal for any coal. */
	private static Item stored(Entry e) {
		if (e.matches(Items.SPRUCE_LOG)) {
			return Items.SPRUCE_LOG;
		}
		if (e.matches(Items.COAL)) {
			return Items.COAL;
		}
		return e.mainItem();
	}

	/** Searches for an item a letter at a time, types the amount and adds it. */
	private void addByName(String search, String itemName, int amount) {
		clickBox("searchBox");
		film.ctx.runOnClient(mc -> ((EditBox) field(mc.gui.screen(), PocketListScreen.class, "searchBox")).setValue(""));
		typeText(search);
		record(8);
		clickBox("amountBox");
		film.ctx.runOnClient(mc -> ((EditBox) field(mc.gui.screen(), PocketListScreen.class, "amountBox")).setValue(""));
		typeText(Integer.toString(amount));
		record(6);
		clickWidget(w -> w instanceof PocketListScreen.ItemButton && w.getMessage().getString().equals("Add " + itemName), "add " + search);
		Film.check(film.ctx.computeOnClient(mc -> Lists.active().entries.stream().anyMatch(e -> e.name().getString().equals(itemName) && e.need == amount)),
				"the list needs " + amount + " " + itemName);
		record(12);
	}

	/** Clicks the Break down button on the line for {@code item} (the rows are in list order). */
	private void breakDownLine(Item item) {
		int row = film.ctx.computeOnClient(mc -> {
			List<Entry> entries = Lists.active().entries;
			for (int i = 0; i < entries.size(); i++) {
				if (entries.get(i).matches(item)) {
					return i;
				}
			}
			return -1;
		});
		Film.check(row >= 0, "the list has a line for " + id(item));
		List<int[]> buttons = film.ctx.computeOnClient(mc -> allWidgets(mc).stream()
				.filter(w -> w instanceof PocketListScreen.ItemButton && w.getMessage().getString().startsWith("Break down"))
				.sorted((a, b) -> Integer.compare(a.getY(), b.getY()))
				.map(w -> new int[] { w.getX() + 9, w.getY() + 9 }).toList());
		Film.check(row < buttons.size(), "the " + id(item) + " line is on the first page");
		int[] at = buttons.get(row);
		glideTo(at[0], at[1], 8);
		// Long enough to read the button's tooltip.
		record(20);
		film.ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		film.log("click", "break down " + id(item));
		record(3);
		if (film.ctx.computeOnClient(mc -> mc.gui.screen() instanceof RecipeChooserScreen)) {
			// More than one way to make it: take the first.
			record(15);
			clickWidget(w -> w.getClass().getSimpleName().equals("RecipeRow"), "recipe");
			until(mc -> mc.gui.screen() instanceof PocketListScreen);
		}
		Film.check(film.ctx.computeOnClient(mc -> Lists.active().entries.stream().noneMatch(e -> e.matches(item))),
				"the " + id(item) + " line was broken down");
	}

	/** Every widget on the open screen, inside lists and rows too. */
	private static List<AbstractWidget> allWidgets(Minecraft mc) {
		List<AbstractWidget> found = new ArrayList<>();
		collect(mc.gui.screen(), found);
		return found;
	}

	private static void collect(GuiEventListener at, List<AbstractWidget> into) {
		if (at instanceof AbstractWidget w) {
			into.add(w);
		}
		if (at instanceof ContainerEventHandler c) {
			for (GuiEventListener child : c.children()) {
				collect(child, into);
			}
		}
	}

	private void clickWidget(Predicate<AbstractWidget> which, String what) {
		int[] at = film.ctx.computeOnClient(mc -> allWidgets(mc).stream().filter(w -> w.visible && w.active).filter(which)
				.map(w -> new int[] { w.getX() + w.getWidth() / 2, w.getY() + w.getHeight() / 2 }).findFirst().orElse(null));
		Film.check(at != null, "the screen has the \"" + what + "\" control");
		clickAt(at[0], at[1], what);
	}

	/** Clicks into one of the list screen's text boxes (they're rebuilt as you type, so it's found by name). */
	private void clickBox(String name) {
		int[] at = film.ctx.computeOnClient(mc -> {
			EditBox box = (EditBox) field(mc.gui.screen(), PocketListScreen.class, name);
			return new int[] { box.getX() + 6, box.getY() + 10 };
		});
		clickAt(at[0], at[1], name);
	}

	private void clickAt(int guiX, int guiY, String what) {
		glideTo(guiX, guiY, 8 + film.jitter.nextInt(3));
		record(4);
		film.ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		film.log("click", what);
		record(3);
	}

	/** Types a letter at a time with small, uneven pauses. */
	private void typeText(String text) {
		for (char c : text.toCharArray()) {
			film.ctx.getInput().typeChar(c);
			film.log("key", "type " + c);
			record(2 + (film.jitter.nextInt(5) == 0 ? 2 : film.jitter.nextInt(2)));
		}
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

	/** Turns the head from where it looks now to {@code to} over {@code n} frames, eased. */
	private void sweep(Vec3 to, int n) {
		Vec3 from = looking == null ? to : looking;
		for (int i = 1; i <= n; i++) {
			film.turnHead(from.lerp(to, Film.ease(i / (double) n)));
			record(1);
		}
		looking = to;
	}

	/** Films {@code n} frames, logging pickups, counts and the finish for the sound pass. */
	private void record(int n) {
		for (int i = 0; i < n; i++) {
			marks();
			film.record(1);
		}
	}

	private void until(Predicate<Minecraft> done) {
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
