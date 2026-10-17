package com.gabbymaybelle.pocketlist;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Films the mod-page video: a screenshot every game tick (20 a second) through five short scenes,
 * saved as `<scene>_<frame>.png` in the game folder's `video_tour`, with `actions.tsv` (the frame
 * of each key press, click, pickup, count and finished list, for the sound) and `frames.tsv` (where
 * the mouse is on each frame, since screenshots don't show it). Only runs with
 * `./gradlew :fabric:runClientGameTest -PvideoTour`.
 */
public class VideoTour implements FabricClientGameTest {
	/** A list as it might be copied out of a notes app, bullets and a checkbox included. */
	static final String NOTES = """
			- 64 oak planks
			- 32 glass pane
			[ ] 12 spruce stairs
			4 flower pot
			""";

	private ClientGameTestContext ctx;
	private Path dir;
	private String scene;
	private int frame;
	private final List<String> actions = new ArrayList<>();
	private final List<String> frames = new ArrayList<>();
	// What the last frame saw, so counting, pickups and the finished list are logged as they happen.
	private int lastHave;
	private int lastCarried;
	private boolean lastFinished;

	static boolean enabled() {
		return Boolean.getBoolean("pocketlist.videoTour");
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!enabled()) {
			return;
		}
		ctx = context;
		dir = FabricLoader.getInstance().getGameDir().resolve("video_tour");
		try {
			if (Files.isDirectory(dir)) {
				try (var old = Files.list(dir)) {
					for (Path file : old.toList()) {
						Files.delete(file);
					}
				}
			}
			Files.createDirectories(dir);
		} catch (IOException e) {
			throw new AssertionError(e);
		}
		// 1600x900 at GUI scale 3 leaves room for four lines on the list screen; it is scaled to 720p after.
		ctx.getInput().resizeWindow(1600, 900);
		ctx.runOnClient(mc -> {
			mc.options.guiScale().set(3);
			mc.resizeGui();
		});
		try (TestSingleplayerContext world = ctx.worldBuilder().setUseConsistentSettings(false).adjustSettings(state -> {
			state.setSeed("pocket list");
			state.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
			state.setDifficulty(Difficulty.PEACEFUL);
			state.setAllowCommands(true);
		}).create()) {
			world.getConnection().waitForChunksRender();
			TestServerContext server = world.getServer();
			setUp(world, server);
			addLanterns();
			countUp(server);
			breakDown(server);
			pasteList();
			chest(server);
		}
		try {
			Files.write(dir.resolve("actions.tsv"), actions);
			Files.write(dir.resolve("frames.tsv"), frames);
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	private void setUp(TestSingleplayerContext world, TestServerContext server) {
		server.runCommand("gamerule send_command_feedback false");
		server.runCommand("gamerule advance_time false");
		server.runCommand("gamerule advance_weather false");
		server.runCommand("time set 5000");
		server.runCommand("weather clear");
		// Somewhere open and bright: the nearest plains or meadow.
		BlockPos open = server.computeOnServer(s -> {
			BlockPos from = s.getPlayerList().getPlayers().getFirst().blockPosition();
			var found = s.overworld().findClosestBiome3d(b -> b.is(Biomes.PLAINS) || b.is(Biomes.SUNFLOWER_PLAINS) || b.is(Biomes.MEADOW),
					from, 2000, 32, 64);
			return found == null ? from : found.getFirst();
		});
		server.runCommand("spreadplayers " + open.getX() + " " + open.getZ() + " 0 1 false @a");
		ctx.waitTicks(5);
		world.getConnection().waitForChunksRender();
		// Face where the land is lowest, so the view is open ground rather than a hillside.
		int yaw = server.computeOnServer(s -> {
			BlockPos at = s.getPlayerList().getPlayers().getFirst().blockPosition();
			int best = 0;
			int lowest = Integer.MAX_VALUE;
			for (int facing = 0; facing < 360; facing += 45) {
				double rad = Math.toRadians(facing);
				int height = 0;
				for (int ahead = 10; ahead <= 30; ahead += 5) {
					height += s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
							at.getX() + (int) Math.round(-Math.sin(rad) * ahead), at.getZ() + (int) Math.round(Math.cos(rad) * ahead));
				}
				if (height < lowest) {
					lowest = height;
					best = facing;
				}
			}
			return best;
		});
		server.runCommand("execute as @a at @s run tp @s ~ ~ ~ " + yaw + " 12");
		// A flat patch to stand on, so the chest has level ground and there's room to back away.
		server.runCommand("execute as @a at @s run fill ~-9 ~-3 ~-9 ~9 ~-2 ~9 dirt");
		server.runCommand("execute as @a at @s run fill ~-9 ~-1 ~-9 ~9 ~-1 ~9 grass_block");
		server.runCommand("execute as @a at @s run fill ~-9 ~ ~-9 ~9 ~10 ~9 air");
		// Flowers at the edge pop off; don't let them be picked up on camera.
		ctx.waitTicks(2);
		server.runCommand("kill @e[type=item]");
		server.runCommand("recipe give @a *");
		server.runCommand("give @a iron_pickaxe");
		server.runCommand("give @a iron_axe");
		server.runCommand("give @a bread 12");
		server.runCommand("give @a oak_planks 20");
		ctx.runOnClient(mc -> {
			Lists.data().lists.clear();
			Lists.active().name = "Cottage";
			Lists.data().welcomed = true;
			mc.player.getInventory().setSelectedSlot(8);
		});
		ctx.waitTicks(40);
		world.getConnection().waitForChunksRender();
		quiet();
	}

	/** Press K, search "lantern" a letter at a time, ask for 20 and add it. */
	private void addLanterns() {
		scene = "add";
		frame = 0;
		record(10);
		ctx.getInput().pressKey(PocketList.openKey);
		log("key", "open");
		until(mc -> mc.gui.screen() instanceof PocketListScreen);
		record(12);
		clickBox("searchBox");
		for (char c : "lantern".toCharArray()) {
			ctx.getInput().typeChar(c);
			log("key", "type " + c);
			record(3);
		}
		record(10);
		clickBox("amountBox");
		for (char c : "20".toCharArray()) {
			ctx.getInput().typeChar(c);
			log("key", "type " + c);
			record(3);
		}
		record(6);
		int[] lantern = ctx.computeOnClient(mc -> mc.gui.screen().children().stream()
				.filter(c -> c instanceof PocketListScreen.ItemButton b && b.getMessage().getString().equals("Add Lantern"))
				.map(c -> (AbstractWidget) c).map(w -> new int[] { w.getX() + 9, w.getY() + 9 }).findFirst().orElse(null));
		check(lantern != null, "searching \"lantern\" finds the Lantern");
		click(lantern[0], lantern[1], "add lantern");
		moveTo(lantern[0] + 120, lantern[1] - 40, 6);
		check(ctx.computeOnClient(mc -> Lists.active().entries.stream().anyMatch(e -> e.key().equals("minecraft:lantern") && e.need == 20)),
				"the list needs 20 lanterns");
		record(25);
		press(InputConstants.KEY_ESCAPE, "close");
		until(mc -> mc.gui.screen() == null);
		record(12);
	}

	/** Back in the world, lanterns arrive four at a time and the line counts up, then turns green. */
	private void countUp(TestServerContext server) {
		// Toasts time themselves by the clock, and filming slows the game right down, so the finish
		// note is given the longest Notification Time while it's filmed.
		double noteTime = ctx.computeOnClient(mc -> mc.options.notificationDisplayTime().get());
		ctx.runOnClient(mc -> mc.options.notificationDisplayTime().set(10.0));
		scene = "count";
		frame = 0;
		record(15);
		for (int i = 0; i < 5; i++) {
			server.runCommand("give @a lantern 4");
			record(16);
		}
		until(mc -> Boolean.TRUE.equals(Lists.active().wasFinished));
		record(45);
		check(ctx.computeOnClient(mc -> mc.gui.toastManager().getToast(SystemToast.class, PocketList.FINISHED)) != null,
				"the finish note is still showing");
		ctx.runOnClient(mc -> mc.options.notificationDisplayTime().set(noteTime));
	}

	/** Break down swaps the 20 lanterns for the iron nuggets and torches they take. */
	private void breakDown(TestServerContext server) {
		server.runCommand("clear @a lantern");
		ctx.waitFor(mc -> mc.player.getInventory().countItem(net.minecraft.world.item.Items.LANTERN) == 0);
		ctx.runOnClient(Counter::update);
		quiet();
		scene = "breakdown";
		frame = 0;
		record(12);
		ctx.getInput().pressKey(PocketList.openKey);
		log("key", "open");
		until(mc -> mc.gui.screen() instanceof PocketListScreen);
		record(12);
		int[] at = ctx.computeOnClient(mc -> mc.gui.screen().children().stream()
				.filter(c -> c instanceof PocketListScreen.ItemButton b && b.getMessage().getString().startsWith("Break down"))
				.map(c -> (AbstractWidget) c).map(w -> new int[] { w.getX() + 9, w.getY() + 9 }).findFirst().orElse(null));
		check(at != null, "the lantern line has a Break down button");
		moveTo(at[0], at[1], 8);
		// Long enough to read the button's tooltip.
		record(20);
		ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		log("click", "break down");
		record(2);
		check(ctx.computeOnClient(mc -> Lists.active().entries.stream().anyMatch(e -> e.key().equals("minecraft:iron_nugget") && e.need == 160)),
				"20 lanterns take 160 iron nuggets");
		moveTo(at[0] + 160, at[1] + 70, 6);
		record(30);
		press(InputConstants.KEY_ESCAPE, "close");
		until(mc -> mc.gui.screen() == null);
		record(10);
	}

	/** Paste List makes a list from text copied out of a notes app. */
	private void pasteList() {
		ctx.runOnClient(mc -> mc.keyboardHandler.setClipboard(NOTES));
		quiet();
		scene = "paste";
		frame = 0;
		record(10);
		ctx.getInput().pressKey(PocketList.openKey);
		log("key", "open");
		until(mc -> mc.gui.screen() instanceof PocketListScreen);
		record(12);
		int[] paste = ctx.computeOnClient(mc -> mc.gui.screen().children().stream()
				.filter(c -> c instanceof AbstractWidget w && w.getMessage().getString().equals("Paste List"))
				.map(c -> (AbstractWidget) c).map(w -> new int[] { w.getX() + w.getWidth() / 2, w.getY() + 10 }).findFirst().orElse(null));
		check(paste != null, "the list screen has Paste List");
		click(paste[0], paste[1], "paste list");
		moveTo(paste[0] + 140, paste[1] - 90, 6);
		check(ctx.computeOnClient(mc -> Lists.active().entries.size()) == 4, "the pasted list has four lines");
		record(35);
		press(InputConstants.KEY_ESCAPE, "close");
		until(mc -> mc.gui.screen() == null);
		record(20);
	}

	/** Open a chest, walk away, and what's in it still counts. */
	private void chest(TestServerContext server) {
		BlockPos chest = ctx.computeOnClient(mc -> {
			Direction facing = mc.player.getDirection();
			return mc.player.blockPosition().relative(facing, 3);
		});
		String at = chest.getX() + " " + chest.getY() + " " + chest.getZ();
		Direction towardsPlayer = ctx.computeOnClient(mc -> mc.player.getDirection().getOpposite());
		server.runCommand("setblock " + at + " chest[facing=" + towardsPlayer.getSerializedName() + "]{Items:[{Slot:12b,id:\"minecraft:oak_planks\",count:40}]}");
		ctx.waitTicks(3);
		ctx.getInput().lookAt(chest);
		ctx.waitTicks(3);
		quiet();
		scene = "chest";
		frame = 0;
		record(12);
		ctx.runOnClient(mc -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(chest), towardsPlayer, chest, false)));
		log("chest_open", "use chest");
		until(mc -> mc.gui.screen() instanceof ContainerScreen);
		record(30);
		ctx.runOnClient(mc -> mc.player.closeContainer());
		log("chest_close", "close chest");
		record(15);
		Vec3 before = ctx.computeOnClient(mc -> mc.player.position());
		ctx.getInput().holdKey(options -> options.keyDown);
		log("walk", "walk back");
		record(28);
		ctx.getInput().releaseKey(options -> options.keyDown);
		record(10);
		double walked = ctx.computeOnClient(mc -> mc.player.position().distanceTo(before));
		check(walked > 3, "the player backed away from the chest (walked " + walked + ")");
		check(ctx.computeOnClient(mc -> Lists.active().entries.stream().anyMatch(e -> e.key().equals("minecraft:oak_planks") && e.have >= 60)),
				"the chest's planks still count from further away");
		record(30);
	}

	// ------------------------------------------------------------------ filming

	/** Takes `count` frames, one per tick, noting counts, pickups and the finished list as they happen. */
	private void record(int count) {
		for (int i = 0; i < count; i++) {
			shoot();
			ctx.waitTick();
		}
	}

	/** Films until the condition holds (or ten seconds go by). */
	private void until(Predicate<Minecraft> done) {
		for (int i = 0; i < 200; i++) {
			if (ctx.computeOnClient(done::test)) {
				return;
			}
			shoot();
			ctx.waitTick();
		}
		throw new AssertionError("Failed: " + scene + " waited ten seconds at frame " + frame);
	}

	private void shoot() {
		ctx.takeScreenshot(TestScreenshotOptions.of(String.format("%s_%04d", scene, frame)).disableCounterPrefix().withDestinationDir(dir));
		String state = ctx.computeOnClient(mc -> {
			int have = Lists.active().entries.stream().mapToInt(e -> e.have).sum();
			int carried = 0;
			for (ItemStack stack : mc.player.getInventory().getNonEquipmentItems()) {
				carried += stack.getCount();
			}
			boolean finished = Boolean.TRUE.equals(Lists.active().wasFinished);
			String cursor = mc.gui.screen() == null ? "-\t-" : (int) mc.mouseHandler.xpos() + "\t" + (int) mc.mouseHandler.ypos();
			return have + "\t" + carried + "\t" + finished + "\t" + cursor;
		});
		String[] parts = state.split("\t");
		int have = Integer.parseInt(parts[0]);
		int carried = Integer.parseInt(parts[1]);
		boolean finished = Boolean.parseBoolean(parts[2]);
		if (frame > 0) {
			if (carried > lastCarried) {
				log("pickup", "+" + (carried - lastCarried) + " items");
			}
			if (have != lastHave) {
				log("counted", lastHave + " -> " + have);
			}
			if (finished && !lastFinished) {
				log("finished", "list complete");
			}
		}
		lastHave = have;
		lastCarried = carried;
		lastFinished = finished;
		frames.add(scene + "\t" + frame + "\t" + parts[3] + "\t" + parts[4]);
		frame++;
	}

	private void log(String kind, String what) {
		actions.add(scene + "\t" + frame + "\t" + kind + "\t" + what);
	}

	/** Clears setup messages and toasts so they aren't in the picture. */
	private void quiet() {
		ctx.runOnClient(mc -> {
			mc.gui.toastManager().clear();
			mc.gui.hud.getChat().clearMessages(false);
		});
	}

	private void press(int key, String what) {
		ctx.getInput().pressKey(key);
		log("key", what);
	}

	/** Glides the mouse to a point in GUI coordinates over a few frames, so the move can be seen. */
	private void moveTo(int guiX, int guiY, int steps) {
		double scale = ctx.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
		double fromX = ctx.computeOnClient(mc -> mc.mouseHandler.xpos());
		double fromY = ctx.computeOnClient(mc -> mc.mouseHandler.ypos());
		for (int i = 1; i <= steps; i++) {
			double t = (double) i / steps;
			t = t * t * (3 - 2 * t);
			ctx.getInput().setCursorPos(fromX + (guiX * scale - fromX) * t, fromY + (guiY * scale - fromY) * t);
			record(1);
		}
	}

	private void click(int guiX, int guiY, String what) {
		moveTo(guiX, guiY, 8);
		record(4);
		ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		log("click", what);
		record(3);
	}

	/** Clicks into one of the list screen's text boxes (they're rebuilt as you type, so it's found by name). */
	private void clickBox(String name) {
		int[] at = ctx.computeOnClient(mc -> {
			EditBox box = (EditBox) field(mc.gui.screen(), PocketListScreen.class, name);
			return new int[] { box.getX() + 6, box.getY() + 10 };
		});
		click(at[0], at[1], name);
	}

	private static Object field(Object owner, Class<?> declaring, String name) {
		try {
			Field f = declaring.getDeclaredField(name);
			f.setAccessible(true);
			return f.get(owner);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("no field " + name + " on " + declaring.getSimpleName(), e);
		}
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError("Failed: " + what);
		}
	}
}
