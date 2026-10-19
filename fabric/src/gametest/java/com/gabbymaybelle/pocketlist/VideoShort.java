package com.gabbymaybelle.pocketlist;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
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
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Films the vertical YouTube Short: like {@link VideoTour}, a screenshot every tick, but in a tall
 * window and in six quick scenes, half at a cottage and half at a timber house when their build
 * files are passed in (`-Pcottage=<.mcfunction> -Ptimber=<.mcfunction>`); without them the scenes
 * play on open ground. Frames, `actions.tsv` and `frames.tsv` go in the game folder's
 * `video_short`. Only runs with `./gradlew :fabric:runClientGameTest -PvideoShort`.
 */
public class VideoShort implements FabricClientGameTest {
	/** How far east of the cottage the timber house is built, so neither shows behind the other. */
	private static final int APART = 45;

	private ClientGameTestContext ctx;
	private Path dir;
	private String scene;
	private int frame;
	private final List<String> actions = new ArrayList<>();
	private final List<String> frames = new ArrayList<>();
	private int lastHave;
	private int lastCarried;
	private boolean lastFinished;
	private BlockPos cottage;
	private BlockPos timber;

	static boolean enabled() {
		return Boolean.getBoolean("pocketlist.videoShort");
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!enabled()) {
			return;
		}
		ctx = context;
		dir = FabricLoader.getInstance().getGameDir().resolve("video_short");
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
		// 1080x1920 at GUI scale 3 is 360 wide: the list screen (up to 320) and the inventory fit.
		String[] size = System.getProperty("pocketlist.videoSize", "1080x1920").split("x");
		int width = Integer.parseInt(size[0]);
		int height = Integer.parseInt(size[1]);
		ctx.getInput().resizeWindow(width, height);
		ctx.waitTicks(3);
		// The same 360-wide layout in a smaller window (`-PvideoSize=540x960`), scaled up when assembled.
		int guiScale = Math.max(1, width / 360);
		ctx.runOnClient(mc -> {
			mc.options.guiScale().set(guiScale);
			mc.resizeGui();
		});
		// A screen shorter than the window can make the system shrink it; better to stop than film the wrong shape.
		String got = ctx.computeOnClient(mc -> mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight());
		check(got.equals(width + "x" + height), "the window is " + width + "x" + height + " (it is " + got + ")");
		try (TestSingleplayerContext world = ctx.worldBuilder().setUseConsistentSettings(false).adjustSettings(state -> {
			state.setSeed("pocket list");
			state.setGameMode(net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.SelectedGameMode.SURVIVAL);
			state.setDifficulty(Difficulty.PEACEFUL);
			state.setAllowCommands(true);
		}).create()) {
			world.getConnection().waitForChunksRender();
			TestServerContext server = world.getServer();
			setUp(world, server);
			hook(server);
			addIron(server);
			breakDown();
			pasteList();
			litematica(server);
			pin(server);
			chest(server);
			outro(world, server);
		}
		try {
			Files.write(dir.resolve("actions.tsv"), actions);
			Files.write(dir.resolve("frames.tsv"), frames);
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	// ------------------------------------------------------------------ the set

	private void setUp(TestSingleplayerContext world, TestServerContext server) {
		server.runCommand("gamerule send_command_feedback false");
		server.runCommand("gamerule advance_time false");
		server.runCommand("gamerule advance_weather false");
		server.runCommand("time set 6000");
		server.runCommand("weather clear");
		BlockPos open = server.computeOnServer(s -> {
			BlockPos from = s.getPlayerList().getPlayers().getFirst().blockPosition();
			var found = s.overworld().findClosestBiome3d(b -> b.is(Biomes.PLAINS) || b.is(Biomes.SUNFLOWER_PLAINS) || b.is(Biomes.MEADOW),
					from, 2000, 32, 64);
			return found == null ? from : found.getFirst();
		});
		server.runCommand("spreadplayers " + open.getX() + " " + open.getZ() + " 0 1 false @a");
		ctx.waitTicks(5);
		world.getConnection().waitForChunksRender();
		cottage = ctx.computeOnClient(mc -> mc.player.blockPosition());
		timber = cottage.east(APART);
		// One flat lawn under both houses, in slices small enough for a single fill.
		int y = cottage.getY();
		for (int x = cottage.getX() - 14; x <= timber.getX() + 14; x += 6) {
			String xs = x + " ", xe = Math.min(x + 5, timber.getX() + 14) + " ";
			int z0 = cottage.getZ() - 28, z1 = cottage.getZ() + 18;
			server.runCommand("fill " + xs + (y - 3) + " " + z0 + " " + xe + (y - 2) + " " + z1 + " dirt");
			server.runCommand("fill " + xs + (y - 1) + " " + z0 + " " + xe + (y - 1) + " " + z1 + " grass_block");
			server.runCommand("fill " + xs + y + " " + z0 + " " + xe + (y + 31) + " " + z1 + " air");
		}
		build(server, "pocketlist.cottage", cottage);
		build(server, "pocketlist.timber", timber);
		ctx.waitTicks(2);
		server.runCommand("kill @e[type=item]");
		server.runCommand("recipe give @a *");
		ctx.runOnClient(mc -> {
			Lists.data().lists.clear();
			Lists.active().name = "Cottage";
			Lists.data().welcomed = true;
			mc.player.getInventory().setSelectedSlot(8);
		});
		ctx.waitTicks(20);
		world.getConnection().waitForChunksRender();
	}

	/** Replays a house's commands with its front path ending at `spot`, if the file was passed in. */
	private void build(TestServerContext server, String property, BlockPos spot) {
		String file = System.getProperty(property, "");
		if (file.isEmpty()) {
			return;
		}
		List<String> lines;
		try {
			lines = Files.readAllLines(Path.of(file));
		} catch (IOException e) {
			throw new AssertionError("can't read " + property + " (" + file + ")", e);
		}
		String at = "execute positioned " + spot.getX() + " " + spot.getY() + " " + spot.getZ() + " run ";
		for (String line : lines) {
			if (!line.isBlank() && !line.startsWith("#")) {
				server.runCommand(at + line);
			}
		}
	}

	/** Stands the player at an offset from a house's spot, looking at another offset. */
	private void stand(TestServerContext server, BlockPos spot, double x, double y, double z, double fx, double fy, double fz) {
		server.runCommand(String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f",
				spot.getX() + 0.5 + x, spot.getY() + y, spot.getZ() + 0.5 + z,
				spot.getX() + 0.5 + fx, spot.getY() + fy, spot.getZ() + 0.5 + fz));
		ctx.waitTicks(10);
		ctx.runOnClient(Counter::update);
		quiet();
	}

	// ------------------------------------------------------------------ scenes

	/** 1. On the cottage porch, the last four lanterns arrive and the list finishes with its chime. */
	private void hook(TestServerContext server) {
		server.runCommand("give @a lantern 16");
		server.runCommand("give @a candle 4");
		ctx.runOnClient(mc -> {
			Lists.add(Entry.ofItem(Items.LANTERN, 20));
			Lists.add(Entry.ofItem(Items.CANDLE, 4));
		});
		// On the front path, looking up at the porch and door.
		stand(server, cottage, 0.5, 0, 2.5, 0, 3, -8.5);
		double noteTime = ctx.computeOnClient(mc -> mc.options.notificationDisplayTime().get());
		ctx.runOnClient(mc -> mc.options.notificationDisplayTime().set(10.0));
		scene = "hook";
		frame = 0;
		record(12);
		server.runCommand("give @a lantern 4");
		until(mc -> Boolean.TRUE.equals(Lists.active().wasFinished));
		record(45);
		check(ctx.computeOnClient(mc -> mc.gui.toastManager().getToast(SystemToast.class, PocketList.FINISHED)) != null,
				"the finish note is showing");
		ctx.runOnClient(mc -> mc.options.notificationDisplayTime().set(noteTime));
	}

	/** 2. By the cottage fireplace: K, search "iron ingot", ask for 9 and add it. */
	private void addIron(TestServerContext server) {
		server.runCommand("clear @a");
		server.runCommand("give @a iron_ingot 3");
		ctx.runOnClient(mc -> {
			Lists.data().lists.clear();
			Lists.active().name = "Cottage";
			mc.player.getInventory().setSelectedSlot(8);
		});
		stand(server, cottage, 2.5, 1, -10, 8, 2.2, -10);
		scene = "list";
		frame = 0;
		record(8);
		ctx.getInput().pressKey(PocketList.openKey);
		log("key", "open");
		until(mc -> mc.gui.screen() instanceof PocketListScreen);
		record(8);
		clickBox("searchBox");
		for (char c : "iron ingot".toCharArray()) {
			ctx.getInput().typeChar(c);
			log("key", "type " + c);
			record(2);
		}
		record(6);
		clickBox("amountBox");
		ctx.getInput().typeChar('9');
		log("key", "type 9");
		record(6);
		int[] add = ctx.computeOnClient(mc -> mc.gui.screen().children().stream()
				.filter(c -> c instanceof PocketListScreen.ItemButton b && b.getMessage().getString().equals("Add Iron Ingot"))
				.map(c -> (AbstractWidget) c).map(w -> new int[] { w.getX() + 9, w.getY() + 9 }).findFirst().orElse(null));
		check(add != null, "searching \"iron ingot\" finds the Iron Ingot");
		click(add[0], add[1], "add iron");
		check(ctx.computeOnClient(mc -> Lists.active().entries.stream().anyMatch(e -> e.key().equals("minecraft:iron_ingot") && e.need == 9)),
				"the list needs 9 iron ingots");
		record(20);
	}

	/** 3. Break down the iron: choose a recipe, and only what's still missing is asked for. */
	private void breakDown() {
		scene = "breakdown";
		frame = 0;
		record(4);
		int[] at = ctx.computeOnClient(mc -> mc.gui.screen().children().stream()
				.filter(c -> c instanceof PocketListScreen.ItemButton b && b.getMessage().getString().startsWith("Break down"))
				.map(c -> (AbstractWidget) c).map(w -> new int[] { w.getX() + 9, w.getY() + 9 }).findFirst().orElse(null));
		check(at != null, "the iron line has a Break down button");
		moveTo(at[0], at[1], 8);
		record(14);
		ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		log("click", "break down");
		until(mc -> mc.gui.screen() instanceof RecipeChooserScreen);
		record(24);
		int[] row = ctx.computeOnClient(mc -> mc.gui.screen().children().stream()
				.filter(c -> c.getClass().getSimpleName().equals("RecipeRow")).map(c -> (AbstractWidget) c)
				.map(w -> new int[] { w.getX() + 40, w.getY() + 10 }).findFirst().orElse(null));
		check(row != null, "the chooser lists a recipe");
		click(row[0], row[1], "choose recipe");
		until(mc -> mc.gui.screen() instanceof PocketListScreen);
		check(ctx.computeOnClient(mc -> Lists.active().entries.stream().noneMatch(e -> e.key().equals("minecraft:iron_ingot"))),
				"the iron line is swapped for what it takes");
		record(40);
		press(InputConstants.KEY_ESCAPE, "close");
		until(mc -> mc.gui.screen() == null);
		record(6);
	}

	/** A list as it might be copied out of a notes app, bullets and a checkbox included. */
	static final String NOTES = """
			- 64 spruce planks
			- 24 glass pane
			[ ] 12 lantern
			8 flower pot
			""";

	/** Litematica's own material list export (its Write to file button), for the timber house. */
	static final String MATERIAL_LIST = """
			+------------------------------------------------------------------------------+
			| Timber house                                                                 |
			+------------------------------------------------------------------------------+
			| Item                 | Total | Missing | Available | Total (sb) | Missing (sb) |
			+----------------------+-------+---------+-----------+------------+--------------+
			| Deepslate Tiles      |   640 |     640 |         0 |    0.37 SB |      0.37 SB |
			| Spruce Log           |   212 |     212 |         0 |    0.12 SB |      0.12 SB |
			| Stripped Spruce Log  |    96 |      96 |         0 |    0.06 SB |      0.06 SB |
			| Glass Pane           |    54 |      54 |         0 |    0.03 SB |      0.03 SB |
			| Lantern              |    14 |      14 |         0 |    0.01 SB |      0.01 SB |
			| Flower Pot           |    12 |      12 |         0 |    0.01 SB |      0.01 SB |
			+----------------------+-------+---------+-----------+------------+--------------+
			""";

	/** 3b. Still by the fireplace: Paste List turns text from a notes app into a checklist. */
	private void pasteList() {
		ctx.runOnClient(mc -> mc.keyboardHandler.setClipboard(NOTES));
		quiet();
		scene = "paste";
		frame = 0;
		record(6);
		ctx.getInput().pressKey(PocketList.openKey);
		log("key", "open");
		until(mc -> mc.gui.screen() instanceof PocketListScreen);
		record(6);
		clickButton("Paste List");
		check(ctx.computeOnClient(mc -> Lists.active().entries.size()) == 4, "the pasted list has four lines");
		record(36);
		press(InputConstants.KEY_ESCAPE, "close");
		until(mc -> mc.gui.screen() == null);
		record(4);
	}

	/** 3c. In the timber house's sitting room: From Litematica makes the build's whole list. */
	private void litematica(TestServerContext server) {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("litematica").resolve("material_list_2026-10-18_12.00.00.txt");
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, MATERIAL_LIST);
		} catch (IOException e) {
			throw new AssertionError(e);
		}
		stand(server, timber, -1, 1, -12, 5, 2.2, -12);
		scene = "litematica";
		frame = 0;
		record(6);
		ctx.getInput().pressKey(PocketList.openKey);
		log("key", "open");
		until(mc -> mc.gui.screen() instanceof PocketListScreen);
		record(6);
		clickButton("From Litematica");
		check(ctx.computeOnClient(mc -> Lists.active().entries.size()) == 6, "the material list has six lines");
		record(40);
		press(InputConstants.KEY_ESCAPE, "close");
		until(mc -> mc.gui.screen() == null);
		record(14);
		try {
			// Don't leave it for the next game test, whose screen would gain the button.
			Files.delete(file);
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	/** Clicks a button on the open screen by its label. */
	private void clickButton(String label) {
		int[] at = ctx.computeOnClient(mc -> mc.gui.screen().children().stream()
				.filter(c -> c instanceof AbstractWidget w && w.getMessage().getString().equals(label))
				.map(c -> (AbstractWidget) c).map(w -> new int[] { w.getX() + w.getWidth() / 2, w.getY() + 10 }).findFirst().orElse(null));
		check(at != null, "the screen has " + label);
		click(at[0], at[1], label.toLowerCase(java.util.Locale.ROOT));
	}

	/** 4. Upstairs in the timber house's workshop: pin an inventory item with Z. */
	private void pin(TestServerContext server) {
		server.runCommand("give @a spruce_planks 12");
		ctx.runOnClient(mc -> {
			Lists.data().lists.clear();
			Lists.active().name = "Workshop";
		});
		stand(server, timber, 0.5, 6, -10.5, -2.5, 6.5, -15.5);
		scene = "pin";
		frame = 0;
		record(8);
		ctx.runOnClient(mc -> mc.player.getRecipeBook().setOpen(net.minecraft.world.inventory.RecipeBookType.CRAFTING, false));
		ctx.getInput().pressKey(options -> options.keyInventory);
		log("key", "inventory");
		until(mc -> mc.gui.screen() instanceof InventoryScreen);
		record(6);
		int[] planks = slotWith(Items.SPRUCE_PLANKS);
		moveTo(planks[0], planks[1], 10);
		record(10);
		ctx.getInput().pressKey(PocketList.pinKey);
		log("key", "pin");
		record(2);
		check(ctx.computeOnClient(mc -> Lists.active().entries.stream().anyMatch(e -> e.key().equals("minecraft:spruce_planks") && e.need > 0)),
				"Z pins the planks");
		record(24);
		ctx.runOnClient(mc -> mc.player.closeContainer());
		log("key", "close");
		record(18);
	}

	/** 5. In front of the timber house: open a chest once, and its planks count from then on. */
	private void chest(TestServerContext server) {
		ctx.runOnClient(mc -> Lists.active().entries.forEach(e -> e.need = 48));
		// Our own chest on the front path, so the shot doesn't depend on where the house's chests are.
		BlockPos box = timber.offset(1, 0, 3);
		String at = box.getX() + " " + box.getY() + " " + box.getZ();
		server.runCommand("setblock " + at + " chest[facing=south]{Items:[{Slot:13b,id:\"minecraft:spruce_planks\",count:36}]}");
		stand(server, timber, 1, 0, 6, 1, 0.5, 3);
		ctx.getInput().lookAt(box);
		ctx.waitTicks(3);
		scene = "chest";
		frame = 0;
		record(12);
		// A right-click, as a player opens it, with the crosshair on the chest.
		check(ctx.computeOnClient(mc -> mc.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(box)),
				"the crosshair is on the chest at " + box);
		ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
		log("chest_open", "use chest");
		until(mc -> mc.gui.screen() instanceof ContainerScreen);
		record(28);
		ctx.runOnClient(mc -> mc.player.closeContainer());
		log("chest_close", "close chest");
		record(10);
		// Turn away from the chest, and it still counts.
		Vec3 eye = ctx.computeOnClient(mc -> mc.player.getEyePosition());
		ctx.getInput().lookAt(BlockPos.containing(eye.add(eye.subtract(Vec3.atCenterOf(box)))));
		record(36);
		check(ctx.computeOnClient(mc -> Lists.active().entries.stream().anyMatch(e -> e.key().equals("minecraft:spruce_planks") && e.have >= 48)),
				"the chest's planks count with what's carried");
	}

	/** 6. The timber house at dusk, from the low corner, with the HUD still up. */
	private void outro(TestSingleplayerContext world, TestServerContext server) {
		server.runCommand("time set 12800");
		server.runCommand("clear @a spruce_planks");
		ctx.runOnClient(mc -> {
			Lists.data().lists.clear();
			Lists.active().name = "Next build";
			Lists.add(Entry.ofItem(Items.SPRUCE_LOG, 64));
			Lists.add(Entry.ofItem(Items.LANTERN, 8));
			Lists.add(Entry.ofItem(Items.DEEPSLATE_TILES, 128));
		});
		stand(server, timber, 12.5, 0.3, 5.5, 1.5, 9, -9.5);
		world.getConnection().waitForChunksRender();
		scene = "outro";
		frame = 0;
		record(110);
	}

	// ------------------------------------------------------------------ filming (as in VideoTour)

	private void record(int count) {
		for (int i = 0; i < count; i++) {
			shoot();
			ctx.waitTick();
		}
	}

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

	private void clickBox(String name) {
		int[] at = ctx.computeOnClient(mc -> {
			EditBox box = (EditBox) field(mc.gui.screen(), PocketListScreen.class, name);
			return new int[] { box.getX() + 6, box.getY() + 10 };
		});
		click(at[0], at[1], name);
	}

	/** The middle of the first slot holding `item` on the open container screen, in GUI coordinates. */
	private int[] slotWith(Item item) {
		int[] at = ctx.computeOnClient(mc -> {
			AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.gui.screen();
			int left = (int) field(screen, AbstractContainerScreen.class, "leftPos");
			int top = (int) field(screen, AbstractContainerScreen.class, "topPos");
			for (Slot slot : screen.getMenu().slots) {
				if (slot.getItem().is(item)) {
					return new int[] { left + slot.x + 8, top + slot.y + 8 };
				}
			}
			return null;
		});
		check(at != null, "found a slot with " + item);
		return at;
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

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError("Failed: " + what);
		}
	}
}
