package com.gabbymaybelle.pocketlist;

import com.gabbymaybelle.pocketlist.compat.Viewers;
import com.gabbymaybelle.pocketlist.harness.ClientGameTest;
import com.gabbymaybelle.pocketlist.harness.ClientGameTestContext;
import com.gabbymaybelle.pocketlist.harness.TestServerContext;
import com.gabbymaybelle.pocketlist.harness.TestSingleplayerContext;
import com.gabbymaybelle.pocketlist.harness.TestWorldSave;
import com.gabbymaybelle.pocketlist.mixin.RecipeBookComponentAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Plays through Pocket List in a real game: counting, chest memory, pinning items and recipes,
 * typing in the recipe search, and the list screen. Run with `./gradlew :fabric:runClientGameTest`;
 * screenshots land in build/run/clientGameTest/screenshots.
 */
public class PocketListClientTest implements ClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (ScreenshotTour.enabled()) {
			// Only taking screenshots this time.
			return;
		}
		TestWorldSave save;
		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			// Logged here as well, because leaving the world after a failed check can hang the test
			// framework, and then the failure itself never gets printed.
			try {
				play(ctx, world);
			} catch (RuntimeException | Error e) {
				PocketList.LOGGER.error("Pocket List test failed", e);
				throw e;
			}
			save = world.getWorldSave();
		}
		// Back into the same world: the chests opened earlier, and the pot, are still remembered.
		try (TestSingleplayerContext world = save.open()) {
			try {
				playAgain(ctx, world);
			} catch (RuntimeException | Error e) {
				PocketList.LOGGER.error("Pocket List test failed", e);
				throw e;
			}
		}
	}

	private static void play(ClientGameTestContext ctx, TestSingleplayerContext world) {
		world.waitForChunksRender();
		welcome(ctx);
		TestServerContext server = world.getServer();
		server.runCommand("gamemode survival @a");
		server.runCommand("recipe give @a *");
		server.runCommand("clear @a");

		counting(ctx, server);
		chestMemory(ctx, server);
		creativePickerDoesNotCount(ctx, server);
		pinItems(ctx);
		pinRecipe(ctx);
		typingInRecipeSearchDoesNotPin(ctx);
		listScreen(ctx);
		listsAsText(ctx);
		typedAmounts(ctx);
		breakDown(ctx, server);
		finishedToast(ctx, server);
		settings(ctx);
		belowEffectIcons(ctx, server);
		saving(ctx);
	}

	private static void playAgain(ClientGameTestContext ctx, TestSingleplayerContext world) {
		world.waitForChunksRender();
		ctx.runOnClient(mc -> {
			Lists.data().lists.clear();
			Lists.add(Entry.ofItem(Items.IRON_INGOT, 5));
		});
		recount(ctx);
		expectHave(ctx, "minecraft:iron_ingot", 7);
		check(ctx.computeOnClient(mc -> mc.getToasts().getToast(SystemToast.class, PocketList.WELCOME)) == null,
				"the welcome toast only shows once");
		if (FabricLoader.getInstance().isModLoaded("jei") || FabricLoader.getInstance().isModLoaded("roughlyenoughitems")) {
			recipeViewer(ctx, world.getServer());
		}
	}

	// ------------------------------------------------------------------ new in this round

	/** The Settings button opens the settings; the HUD Position button walks the corners and the HUD follows. */
	private static void settings(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> {
			Lists.active().entries.clear();
			Lists.add(Entry.ofItem(Items.OAK_PLANKS, 12));
			Lists.data().hudPosition = Lists.HudPosition.TOP_LEFT;
		});
		ctx.getInput().pressKey(PocketList.openKey);
		ctx.waitForScreen(PocketListScreen.class);
		AbstractWidget settings = widgetNamed(ctx, "Settings");
		check(settings != null, "the list screen has a Settings button");
		click(ctx, settings.getX() + 5, settings.getY() + 5);
		ctx.waitForScreen(PocketListSettingsScreen.class);
		ctx.takeScreenshot("pocket_list_settings");
		AbstractWidget position = widgetNamed(ctx, "HUD Position");
		check(position != null, "the settings have a HUD Position button");
		Lists.HudPosition[] corners = Lists.HudPosition.values();
		for (int i = 1; i < corners.length; i++) {
			click(ctx, position.getX() + 5, position.getY() + 5);
			ctx.waitTick();
			Lists.HudPosition now = ctx.computeOnClient(mc -> Lists.data().hudPosition);
			check(now == corners[i], "clicking HUD Position moves to " + corners[i] + " (got " + now + ")");
		}
		// Back out to the game: the settings close (saving), then the list screen.
		ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
		ctx.waitForScreen(PocketListScreen.class);
		ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
		ctx.waitForScreen(null);
		ctx.waitTicks(2);
		ctx.takeScreenshot("pocket_list_hud_bottom_right");
		try {
			String saved = Files.readString(FabricLoader.getInstance().getConfigDir().resolve("pocket_list.json"));
			check(saved.contains("BOTTOM_RIGHT"), "the HUD position is saved to the file");
		} catch (IOException e) {
			throw new AssertionError("couldn't read the saved lists", e);
		}
		ctx.runOnClient(mc -> Lists.data().hudPosition = Lists.HudPosition.TOP_LEFT);
	}

	/** In the top-right corner the list moves down below the effect icons, one row or two. */
	private static void belowEffectIcons(ClientGameTestContext ctx, TestServerContext server) {
		server.runCommand("effect clear @a");
		ctx.waitTicks(3);
		int none = ctx.computeOnClient(PocketHud::belowEffects);
		server.runCommand("effect give @a speed 60 0");
		ctx.waitTicks(3);
		int beneficial = ctx.computeOnClient(PocketHud::belowEffects);
		server.runCommand("effect give @a poison 60 0");
		ctx.waitTicks(3);
		int harmful = ctx.computeOnClient(PocketHud::belowEffects);
		server.runCommand("effect clear @a");
		check(none == 4, "with no effects the list sits at the top (y " + none + ")");
		check(beneficial == 30, "below the row of beneficial effect icons (y " + beneficial + ")");
		check(harmful == 56, "below the second row once there's a harmful effect (y " + harmful + ")");
	}

	/** A button on the current screen (searching inside option lists too) whose label contains the text. */
	private static AbstractWidget widgetNamed(ClientGameTestContext ctx, String text) {
		return ctx.computeOnClient(mc -> {
			ArrayDeque<GuiEventListener> queue = new ArrayDeque<>(mc.screen.children());
			while (!queue.isEmpty()) {
				GuiEventListener child = queue.poll();
				if (child instanceof AbstractWidget widget && widget.getMessage().getString().contains(text)) {
					return widget;
				}
				if (child instanceof ContainerEventHandler container) {
					queue.addAll(container.children());
				}
			}
			return null;
		});
	}

	private static void welcome(ClientGameTestContext ctx) {
		ctx.waitTicks(5);
		check(ctx.computeOnClient(mc -> Lists.data().welcomed), "the welcome toast is marked as shown");
		// The first list is made while the game starts, before its language files are read.
		String firstName = ctx.computeOnClient(mc -> Lists.active().name);
		check(firstName.equals("List 1"), "a new player's first list is called List 1 (it's called " + firstName + ")");
		check(ctx.computeOnClient(mc -> mc.getToasts().getToast(SystemToast.class, PocketList.WELCOME)) != null,
				"the welcome toast is showing on first join");
		ctx.takeScreenshot("pocket_list_welcome");
	}

	private static void typedAmounts(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> {
			Lists.active().entries.clear();
			Lists.add(Entry.ofItem(Items.OAK_PLANKS, 1));
		});
		ctx.getInput().pressKey(PocketList.openKey);
		ctx.waitForScreen(PocketListScreen.class);
		// The first line's amount box: click it and type; the old number is replaced.
		EditBox amount = ctx.computeOnClient(mc -> mc.screen.children().stream()
				.filter(c -> c instanceof EditBox box && box.getX() > mc.screen.width / 2 && box.getY() < 80)
				.map(c -> (EditBox) c).findFirst().orElse(null));
		check(amount != null, "each line has an amount box");
		click(ctx, amount.getX() + 5, amount.getY() + 5);
		ctx.getInput().typeChars("2x00");
		ctx.waitTick();
		expectNeed(ctx, "minecraft:oak_planks", 200);
		check(ctx.computeOnClient(mc -> amount.getValue()).equals("200"), "letters are ignored in the amount box");

		// The amount next to the search box sets how many a result adds.
		EditBox addAmount = ctx.computeOnClient(mc -> (EditBox) field(mc.screen, PocketListScreen.class, "amountBox"));
		click(ctx, addAmount.getX() + 5, addAmount.getY() + 5);
		ctx.getInput().typeChars("12");
		EditBox search = ctx.computeOnClient(mc -> (EditBox) field(mc.screen, PocketListScreen.class, "searchBox"));
		click(ctx, search.getX() + 5, search.getY() + 5);
		ctx.getInput().typeChars("lantern");
		ctx.waitTicks(2);
		int[] lantern = ctx.computeOnClient(mc -> mc.screen.children().stream()
				.filter(c -> c instanceof PocketListScreen.ItemButton b && b.getMessage().getString().equals("Add Lantern"))
				.map(c -> (AbstractWidget) c).map(w -> new int[] { w.getX() + 9, w.getY() + 9 }).findFirst().orElse(null));
		check(lantern != null, "searching \"lantern\" finds the Lantern");
		click(ctx, lantern[0], lantern[1]);
		expectNeed(ctx, "minecraft:lantern", 12);
		ctx.takeScreenshot("pocket_list_amounts");
		// A small window: everything still has to fit.
		ctx.getInput().resizeWindow(560, 420);
		ctx.waitTicks(3);
		ctx.takeScreenshot("pocket_list_small_window");
		ctx.getInput().resizeWindow(854, 480);
		ctx.waitTicks(3);
	}

	private static void breakDown(ClientGameTestContext ctx, TestServerContext server) {
		// Like a brand-new player: no recipes unlocked. In single player the world still knows them all.
		server.runCommand("recipe take @a *");
		ctx.waitFor(mc -> mc.level.getRecipeManager().getRecipes().stream().noneMatch(r -> mc.player.getRecipeBook().contains(r)));
		// A lantern has one recipe, so it's replaced straight away.
		give(ctx, server, Items.LANTERN, 2);
		ctx.runOnClient(mc -> ((PocketListScreen) mc.screen).changed());
		expectHave(ctx, "minecraft:lantern", 2);
		clickBreakDown(ctx, 1);
		check(ctx.computeOnClient(mc -> mc.screen instanceof PocketListScreen), "one recipe needs no choosing");
		// Two lanterns are already in the inventory, so only ten more are made.
		expectNeed(ctx, "minecraft:iron_nugget", 80);
		expectNeed(ctx, "minecraft:torch", 10);
		check(ctx.computeOnClient(mc -> Lists.active().entries.stream().noneMatch(e -> e.key().equals("minecraft:lantern"))),
				"the lantern line is replaced");
		ctx.takeScreenshot("pocket_list_broken_down");

		// Iron ingots can be made several ways, so you get to choose.
		ctx.runOnClient(mc -> {
			Lists.active().entries.clear();
			Lists.add(Entry.ofItem(Items.IRON_INGOT, 9));
			((PocketListScreen) mc.screen).changed();
		});
		// The iron in the chests from earlier counts, so fewer are needed.
		int haveIron = entry(ctx, "minecraft:iron_ingot").have();
		clickBreakDown(ctx, 0);
		ctx.waitForScreen(RecipeChooserScreen.class);
		ctx.takeScreenshot("pocket_list_chooser");
		ctx.getInput().resizeWindow(560, 420);
		ctx.waitTicks(3);
		ctx.takeScreenshot("pocket_list_chooser_small");
		ctx.getInput().resizeWindow(854, 480);
		ctx.waitTicks(3);
		List<FoundRecipe> recipes = ctx.computeOnClient(mc -> Recipes.forItem(mc, Items.IRON_INGOT));
		check(recipes.size() >= 2, "iron ingots have several recipes");
		FoundRecipe first = recipes.getFirst();
		check(!(first.makes() == 9 && first.ingredients().size() == 1), "unpacking an iron block isn't offered first");
		int[] firstRow = ctx.computeOnClient(mc -> mc.screen.children().stream()
				.filter(c -> c.getClass().getSimpleName().equals("RecipeRow")).map(c -> (AbstractWidget) c)
				.map(w -> new int[] { w.getX() + 40, w.getY() + 10 }).findFirst().orElse(null));
		click(ctx, firstRow[0], firstRow[1]);
		ctx.waitForScreen(PocketListScreen.class);
		int crafts = (9 - haveIron + first.makes() - 1) / first.makes();
		Map<String, Integer> expected = new HashMap<>();
		for (Entry e : first.ingredients()) {
			expected.put(e.key(), e.need * crafts);
		}
		check(needs(ctx).equals(expected), "choosing a recipe adds its ingredients: expected " + expected + ", got " + needs(ctx));
		server.runCommand("recipe give @a *");
		close(ctx);
	}

	private static void clickBreakDown(ClientGameTestContext ctx, int row) {
		int[] at = ctx.computeOnClient(mc -> mc.screen.children().stream()
				.filter(c -> c instanceof PocketListScreen.ItemButton b && b.getMessage().getString().startsWith("Break down"))
				.map(c -> (AbstractWidget) c).skip(row).map(w -> new int[] { w.getX() + 9, w.getY() + 9 }).findFirst().orElse(null));
		check(at != null, "line " + row + " has a Break down button");
		click(ctx, at[0], at[1]);
		ctx.waitTicks(2);
	}

	private static void finishedToast(ClientGameTestContext ctx, TestServerContext server) {
		ctx.runOnClient(mc -> {
			mc.getToasts().clear();
			Lists.active().entries.clear();
			Lists.add(Entry.ofItem(Items.DIAMOND, 2));
		});
		recount(ctx);
		check(ctx.computeOnClient(mc -> mc.getToasts().getToast(SystemToast.class, PocketList.FINISHED)) == null,
				"no celebration before it's done");
		give(ctx, server, Items.DIAMOND, 2);
		recount(ctx);
		check(ctx.computeOnClient(mc -> mc.getToasts().getToast(SystemToast.class, PocketList.FINISHED)) != null,
				"a toast when the last line is gathered");
		ctx.takeScreenshot("pocket_list_finished");
		recount(ctx);
		ctx.runOnClient(mc -> mc.getToasts().clear());
		recount(ctx);
		check(ctx.computeOnClient(mc -> mc.getToasts().getToast(SystemToast.class, PocketList.FINISHED)) == null,
				"the celebration happens once, not on every count");
	}

	/** With JEI or REI installed: pin from its item list, keep typing in its search, and use its recipes. */
	private static void recipeViewer(ClientGameTestContext ctx, TestServerContext server) {
		ctx.runOnClient(mc -> Lists.data().lists.clear());
		openInventory(ctx, false);
		ctx.waitTicks(20);
		// Find an item in the viewer's list by moving the mouse over the right-hand side of the screen
		// (the viewers read the real mouse position).
		int width = ctx.computeOnClient(mc -> mc.screen.width);
		int height = ctx.computeOnClient(mc -> mc.screen.height);
		boolean found = false;
		for (int y = 30; y < height - 30 && !found; y += 18) {
			for (int x = width - 12; x > width / 2 + 100 && !found; x -= 18) {
				ctx.getInput().setCursorPos(x * scale(ctx), y * scale(ctx));
				ctx.waitTick();
				found = !ctx.computeOnClient(mc -> Pins.hoveredItem(mc, mc.screen)).isEmpty();
			}
		}
		check(found, "the recipe viewer's item list is on screen");
		ctx.getInput().pressKey(PocketList.pinKey);
		ctx.waitTick();
		check(ctx.computeOnClient(mc -> Lists.active().entries.size()) == 1, "pinning from the recipe viewer's list adds the item");
		ctx.takeScreenshot("pocket_list_viewer_pin");

		// Typing in the viewer's search box types; it doesn't pin. The box is at the bottom,
		// either in the middle (REI) or under the item list (JEI).
		boolean typing = false;
		for (int[] spot : new int[][] { { width / 2, height - 10 }, { width - 70, height - 10 }, { width - 70, height - 14 } }) {
			click(ctx, spot[0], spot[1]);
			if (ctx.computeOnClient(mc -> Pins.typingInViewer())) {
				typing = true;
				break;
			}
		}
		check(typing, "found the recipe viewer's search box");
		Map<String, Integer> before = needs(ctx);
		ctx.getInput().pressKey(InputConstants.KEY_Z);
		ctx.getInput().typeChar('z');
		ctx.waitTick();
		check(needs(ctx).equals(before), "typing z in the recipe viewer's search doesn't pin anything");

		check(!ctx.computeOnClient(mc -> Viewers.all().getFirst().recipesFor(Items.LANTERN)).isEmpty(),
				"the recipe viewer knows how to make a lantern");
		close(ctx);
	}

	// ------------------------------------------------------------------ the tests

	private static void counting(ClientGameTestContext ctx, TestServerContext server) {
		ctx.runOnClient(mc -> {
			Lists.data().lists.clear();
			Lists.data().active = 0;
			Lists.active().name = "Cottage";
			Lists.add(Entry.ofTag(ItemTags.LOGS, 10));
			Lists.add(Entry.ofItem(Items.STICK, 8));
			Lists.add(Entry.ofItem(Items.COBBLESTONE, 32));
			Lists.add(Entry.ofItem(Items.TORCH, 16));
			Lists.add(Entry.ofItem(Items.IRON_INGOT, 5));
		});
		give(ctx, server, Items.OAK_LOG, 6);
		give(ctx, server, Items.SPRUCE_LOG, 6);
		give(ctx, server, Items.STICK, 3);
		// Bundles are an experiment on 1.21.1 that this world doesn't have, so the torches come in a
		// shulker box; a bundle is checked on its own below.
		server.runCommand("give @a orange_shulker_box[container=[{slot:0,item:{id:\"minecraft:torch\",count:10}}]]");
		server.runCommand("give @a white_shulker_box[container=[{slot:0,item:{id:\"minecraft:cobblestone\",count:64}}]]");
		// On a slow computer the items take a few ticks to reach the player's game.
		ctx.waitFor(mc -> mc.player.getInventory().countItem(Items.ORANGE_SHULKER_BOX) >= 1 && mc.player.getInventory().countItem(Items.WHITE_SHULKER_BOX) >= 1);
		recount(ctx);
		expectHave(ctx, "#minecraft:logs", 12);
		expectHave(ctx, "minecraft:stick", 3);
		expectHave(ctx, "minecraft:torch", 10);
		expectHave(ctx, "minecraft:cobblestone", 64);
		expectHave(ctx, "minecraft:iron_ingot", 0);
		// What's in a bundle counts too, and a bundle in a shulker box.
		int inBundles = ctx.computeOnClient(mc -> {
			ItemStack bundle = new ItemStack(Items.BUNDLE);
			bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(new ItemStack(Items.TORCH, 7))));
			ItemStack box = new ItemStack(Items.WHITE_SHULKER_BOX);
			box.set(DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.fromItems(List.of(bundle.copy())));
			Map<Item, Integer> totals = new HashMap<>();
			Counter.add(bundle, totals, 0);
			Counter.add(box, totals, 0);
			return totals.getOrDefault(Items.TORCH, 0);
		});
		check(inBundles == 14, "torches in a bundle, and in a bundle in a shulker box: expected 14, counted " + inBundles);
		ctx.takeScreenshot("pocket_list_hud");
	}

	private static void chestMemory(ClientGameTestContext ctx, TestServerContext server) {
		BlockPos here = ctx.computeOnClient(mc -> mc.player.blockPosition());
		BlockPos left = here.offset(2, 0, 0);
		BlockPos right = here.offset(3, 0, 0);
		BlockPos ender = here.offset(4, 0, 0);
		// A double chest facing north: the left half connects east, to the right half.
		server.runCommand(setblock(left, "chest[facing=north,type=left]{Items:[{Slot:0b,id:\"minecraft:iron_ingot\",count:3}]}"));
		server.runCommand(setblock(right, "chest[facing=north,type=right]{Items:[{Slot:0b,id:\"minecraft:iron_ingot\",count:1}]}"));
		server.runCommand(setblock(ender, "ender_chest[facing=north]"));
		server.runCommand("item replace entity @a enderchest.0 with iron_ingot 1");
		world(ctx);

		openAndClose(ctx, left, ContainerScreen.class);
		expectHave(ctx, "minecraft:iron_ingot", 4);
		// The same double chest from its other half must not count twice.
		openAndClose(ctx, right, ContainerScreen.class);
		expectHave(ctx, "minecraft:iron_ingot", 4);
		// Ender chests are never remembered.
		openAndClose(ctx, ender, ContainerScreen.class);
		expectHave(ctx, "minecraft:iron_ingot", 4);

		// A decorated pot holds things without opening a screen, and the game tells every player what's
		// in it when its chunk loads (or a command changes it). Using it with an empty hand only
		// wobbles it; it's remembered through its item storage (the same way a modded jar would be).
		BlockPos pot = here.offset(2, 0, 2);
		String at = pot.getX() + " " + pot.getY() + " " + pot.getZ();
		server.runCommand(setblock(pot, "decorated_pot{item:{id:\"minecraft:iron_ingot\",count:2}}"));
		server.runCommand("item replace entity @a hotbar.8 with air");
		ctx.runOnClient(mc -> mc.player.getInventory().selected = 8);
		world(ctx);
		recount(ctx);
		// Not used yet: nothing from the pot counts.
		expectHave(ctx, "minecraft:iron_ingot", 4);
		useBlock(ctx, pot);
		ctx.waitTicks(ChestMemory.STORAGE_WAIT + 5);
		check(ctx.computeOnClient(mc -> mc.screen) == null, "a decorated pot opens no screen");
		recount(ctx);
		// Four in the chests and two in the pot.
		expectHave(ctx, "minecraft:iron_ingot", 6);
		ctx.runOnClient(mc -> Lists.data().countChests = false);
		recount(ctx);
		expectHave(ctx, "minecraft:iron_ingot", 0);
		ctx.runOnClient(mc -> Lists.data().countChests = true);
		// When the game is told the pot changed, the count follows without using it again.
		server.runCommand("data merge block " + at + " {item:{id:\"minecraft:iron_ingot\",count:3}}");
		world(ctx);
		recount(ctx);
		expectHave(ctx, "minecraft:iron_ingot", 7);
		ctx.runOnClient(mc -> mc.player.getInventory().selected = 0);
	}

	private static void creativePickerDoesNotCount(ClientGameTestContext ctx, TestServerContext server) {
		server.runCommand("gamemode creative @a");
		ctx.waitTicks(5);
		ctx.getInput().pressKey(options -> options.keyInventory);
		ctx.waitForScreen(CreativeModeInventoryScreen.class);
		ctx.waitTicks(2);
		// Something the creative tab shows that you don't own.
		String shown = ctx.computeOnClient(mc -> {
			AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.screen;
			for (Slot slot : screen.getMenu().slots) {
				ItemStack stack = slot.getItem();
				if (!stack.isEmpty() && mc.player.getInventory().countItem(stack.getItem()) == 0) {
					Lists.add(Entry.ofItem(stack.getItem(), 1));
					return Entry.ofItem(stack.getItem(), 1).key();
				}
			}
			return null;
		});
		check(shown != null, "the creative tab shows some items");
		recount(ctx);
		expectHave(ctx, shown, 0);
		ctx.runOnClient(mc -> Lists.active().entries.removeIf(e -> e.key().equals(shown)));
		close(ctx);
		server.runCommand("gamemode survival @a");
		ctx.waitTicks(5);
	}

	private static void pinItems(ClientGameTestContext ctx) {
		openInventory(ctx, false);
		hoverSlotWith(ctx, Items.STICK);
		ctx.getInput().pressKey(PocketList.pinKey);
		expectNeed(ctx, "minecraft:stick", 9);
		ctx.getInput().holdShift();
		ctx.getInput().pressKey(PocketList.pinKey);
		ctx.getInput().releaseShift();
		expectNeed(ctx, "minecraft:stick", 9 + 64);
		close(ctx);
	}

	private static void pinRecipe(ClientGameTestContext ctx) {
		openInventory(ctx, true);
		// The first recipe on the page, and what pinning it should add.
		RecipeButton button = ctx.computeOnClient(mc -> visibleRecipeButtons(mc).getFirst());
		Map<String, Integer> expected = ctx.computeOnClient(mc -> {
			RecipeHolder<?> recipe = button.getRecipe();
			Map<String, Integer> out = new HashMap<>();
			for (Entry e : Pins.ingredients(recipe.value())) {
				out.put(e.key(), e.need);
			}
			return out;
		});
		check(!expected.isEmpty(), "the first recipe has ingredients");
		Map<String, Integer> before = needs(ctx);
		hover(ctx, button.getX() + 12, button.getY() + 12);
		ctx.getInput().pressKey(PocketList.pinKey);
		ctx.waitTick();
		Map<String, Integer> after = needs(ctx);
		for (Map.Entry<String, Integer> e : expected.entrySet()) {
			int was = before.getOrDefault(e.getKey(), 0);
			check(after.getOrDefault(e.getKey(), 0) == was + e.getValue(),
					"pinning the recipe adds " + e.getValue() + " of " + e.getKey() + " (was " + was + ", now " + after.get(e.getKey()) + ")");
		}
		ctx.takeScreenshot("pocket_list_recipe_pinned");
	}

	private static void typingInRecipeSearchDoesNotPin(ClientGameTestContext ctx) {
		EditBox search = ctx.computeOnClient(mc -> (EditBox) field(recipeBook(mc), RecipeBookComponent.class, "searchBox"));
		click(ctx, search.getX() + 5, search.getY() + 5);
		if (!ctx.computeOnClient(mc -> search.isFocused())) {
			// With REI installed, a click there doesn't focus the vanilla search box (REI's doing, not
			// Pocket List's, which doesn't handle clicks). Focus it directly: the point is the typing.
			ctx.runOnClient(mc -> {
				mc.screen.setFocused(recipeBook(mc));
				search.setFocused(true);
			});
		}
		Map<String, Integer> before = needs(ctx);
		ctx.getInput().pressKey(InputConstants.KEY_Z);
		ctx.getInput().typeChar('z');
		ctx.waitTick();
		check(needs(ctx).equals(before), "typing z in the recipe search doesn't pin anything");
		check(ctx.computeOnClient(mc -> search.getValue()).contains("z"), "the z goes into the search box");
		close(ctx);
		ctx.runOnClient(mc -> mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, false));
	}

	private static void listScreen(ClientGameTestContext ctx) {
		ctx.getInput().pressKey(PocketList.openKey);
		ctx.waitForScreen(PocketListScreen.class);
		ctx.takeScreenshot("pocket_list_screen");

		ctx.clickScreenButton("New List");
		check(ctx.computeOnClient(mc -> Lists.data().lists.size()) == 2, "New List makes a second list");
		check(ctx.computeOnClient(mc -> Lists.active().entries.isEmpty()), "the new list starts empty");
		ctx.takeScreenshot("pocket_list_empty");

		EditBox search = ctx.computeOnClient(mc -> (EditBox) field(mc.screen, PocketListScreen.class, "searchBox"));
		click(ctx, search.getX() + 5, search.getY() + 5);
		ctx.getInput().typeChars("glass pane");
		ctx.waitTicks(2);
		ctx.takeScreenshot("pocket_list_search");
		// Click the first result.
		int[] at = ctx.computeOnClient(mc -> mc.screen.children().stream()
				.filter(c -> c.getClass().getSimpleName().equals("ItemButton"))
				.map(c -> (AbstractWidget) c)
				.map(w -> new int[] { w.getX() + 9, w.getY() + 9 }).findFirst().orElse(null));
		check(at != null, "searching \"glass pane\" finds something");
		click(ctx, at[0], at[1]);
		check(ctx.computeOnClient(mc -> Lists.active().entries.size()) == 1, "clicking a result adds it");
		ctx.clickScreenButton("+");
		check(ctx.computeOnClient(mc -> Lists.active().entries.getFirst().need) == 2, "+ adds one");
		ctx.clickScreenButton("-");
		ctx.clickScreenButton("-");
		check(ctx.computeOnClient(mc -> Lists.active().entries.getFirst().need) == 1, "- stops at one");
		ctx.takeScreenshot("pocket_list_added");

		ctx.clickScreenButton("Delete List");
		check(ctx.computeOnClient(mc -> Lists.data().lists.size()) == 2, "one click on Delete only asks");
		ctx.clickScreenButton("Really delete?");
		check(ctx.computeOnClient(mc -> Lists.data().lists.size()) == 1, "the second click deletes");
		check(ctx.computeOnClient(mc -> Lists.active().name).equals("Cottage"), "back on the first list");

		// The open key closes it again (the search box isn't focused after a rebuild by a button).
		ctx.runOnClient(mc -> mc.screen.clearFocus());
		ctx.getInput().pressKey(PocketList.openKey);
		ctx.waitFor(mc -> mc.screen == null);
	}

	/** Litematica's material list as its Write to file button writes it: a table, with each row's Total. */
	static final String LITEMATICA_TABLE = """
			+--------------------------------------------------------------------------+
			| Cottage roof                                                             |
			+--------------------------------------------------------------------------+
			| Item          | Total | Missing | Available | Total (sb) | Missing (sb) |
			+---------------+-------+---------+-----------+------------+--------------+
			| Oak Planks    |   320 |     300 |        20 |    0.19 SB |      0.17 SB |
			| Glass Pane    |    48 |      48 |         0 |    0.03 SB |      0.03 SB |
			| Mystery Block |     5 |       5 |         0 |    0.00 SB |      0.00 SB |
			+---------------+-------+---------+-----------+------------+--------------+
			""";
	/** The same with Shift held: a .csv. */
	static final String LITEMATICA_CSV = """
			"Item","Total","Missing","Available","Total (sb)","Missing (sb)"
			"Oak Planks",320,300,20,"0.19 SB","0.17 SB"
			"Glass Pane",48,48,0,"0.03 SB","0.03 SB"
			""";
	/** A list from a notes app or a video description. */
	static final String TYPED = """
			- [ ] 64 oak planks
			Torches x16
			minecraft:glass_pane: 8
			#minecraft:logs 10
			3 nonsense thing
			""";

	/** Paste List, Copy List and From Litematica on the list screen, and the text they read and write. */
	private static void listsAsText(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> {
			ListText.Result table = ListText.read(LITEMATICA_TABLE, mc.level.enabledFeatures());
			check("Cottage roof".equals(table.title()), "a Litematica table's schematic name becomes the list's name (got " + table.title() + ")");
			check(needsOf(table).equals(Map.of("minecraft:oak_planks", 320, "minecraft:glass_pane", 48)),
					"a Litematica table gives each item's Total: " + needsOf(table));
			check(table.unknown().equals(List.of("Mystery Block")), "a name that's no item is handed back, not guessed: " + table.unknown());
			ListText.Result csv = ListText.read(LITEMATICA_CSV, mc.level.enabledFeatures());
			check(needsOf(csv).equals(Map.of("minecraft:oak_planks", 320, "minecraft:glass_pane", 48)) && csv.unknown().isEmpty(),
					"a Litematica .csv reads the same: " + needsOf(csv) + " " + csv.unknown());
			ListText.Result typed = ListText.read(TYPED, mc.level.enabledFeatures());
			check(needsOf(typed).equals(Map.of("minecraft:oak_planks", 64, "minecraft:torch", 16, "minecraft:glass_pane", 8, "#minecraft:logs", 10)),
					"typed lines read with the amount first or last, by name, id or tag: " + needsOf(typed));
			check(typed.unknown().equals(List.of("nonsense thing")), "typed lines hand back what's no item: " + typed.unknown());
		});

		// Paste List makes a new list from the clipboard.
		ctx.getInput().pressKey(PocketList.openKey);
		ctx.waitForScreen(PocketListScreen.class);
		int lists = ctx.computeOnClient(mc -> Lists.data().lists.size());
		ctx.runOnClient(mc -> mc.keyboardHandler.setClipboard(TYPED));
		ctx.clickScreenButton("Paste List");
		check(ctx.computeOnClient(mc -> Lists.data().lists.size()) == lists + 1, "Paste List makes a new list");
		check(needs(ctx).equals(Map.of("minecraft:oak_planks", 64, "minecraft:torch", 16, "minecraft:glass_pane", 8, "#minecraft:logs", 10)),
				"the pasted list has the pasted lines: " + needs(ctx));
		check(ctx.computeOnClient(mc -> ((net.minecraft.network.chat.Component) field(mc.screen, PocketListScreen.class, "message")).getString()).contains("nonsense thing"),
				"the message names the line that wasn't recognised");
		ctx.takeScreenshot("pocket_list_pasted");

		// Copy List writes it out, and the copy pastes back as the same list.
		ctx.clickScreenButton("Copy List");
		Map<String, Integer> copied = ctx.computeOnClient(mc -> needsOf(ListText.read(mc.keyboardHandler.getClipboard(), mc.level.enabledFeatures())));
		check(copied.equals(needs(ctx)), "Copy List's text pastes back as the same list: " + copied);

		// From Litematica appears once Litematica has written a material list, and reads the newest.
		check(ctx.computeOnClient(mc -> mc.screen.children().stream().noneMatch(c -> c instanceof AbstractWidget w
				&& w.getMessage().getString().equals("From Litematica"))), "no From Litematica button without Litematica");
		Path folder = FabricLoader.getInstance().getConfigDir().resolve("litematica");
		try {
			Files.createDirectories(folder);
			Path old = folder.resolve("material_list_2026-01-01_10.00.00.txt");
			Files.writeString(old, "| Item | Total |\n| Stone | 1 |\n");
			Files.setLastModifiedTime(old, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() - 60_000));
			Files.writeString(folder.resolve("material_list_2026-10-17_12.00.00.txt"), LITEMATICA_TABLE);
			ctx.runOnClient(mc -> mc.setScreen(new PocketListScreen(null)));
			ctx.waitForScreen(PocketListScreen.class);
			ctx.clickScreenButton("From Litematica");
			check(ctx.computeOnClient(mc -> Lists.active().name).equals("Cottage roof"), "From Litematica names the list after the schematic");
			check(needs(ctx).equals(Map.of("minecraft:oak_planks", 320, "minecraft:glass_pane", 48)), "From Litematica reads the newest list: " + needs(ctx));
			ctx.takeScreenshot("pocket_list_litematica");
			try (var files = Files.list(folder)) {
				for (Path file : files.toList()) {
					Files.delete(file);
				}
			}
			Files.delete(folder);
		} catch (IOException e) {
			throw new AssertionError(e);
		}

		// Back to the one list the later checks expect.
		ctx.runOnClient(mc -> {
			Lists.data().lists.subList(lists, Lists.data().lists.size()).clear();
			Lists.data().active = 0;
			Lists.save();
			mc.setScreen(null);
		});
		ctx.waitTick();
	}

	private static Map<String, Integer> needsOf(ListText.Result result) {
		Map<String, Integer> out = new HashMap<>();
		for (Entry e : result.entries()) {
			out.merge(e.key(), e.need, Integer::sum);
		}
		return out;
	}

	private static void saving(ClientGameTestContext ctx) {
		try {
			savingOrThrow(ctx);
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	private static void savingOrThrow(ClientGameTestContext ctx) throws IOException {
		IOException[] failed = { null };
		ctx.runOnClient(mc -> {
			try {
				savingOnClient();
			} catch (IOException e) {
				failed[0] = e;
			}
		});
		if (failed[0] != null) {
			throw failed[0];
		}
	}

	private static void savingOnClient() throws IOException {
		{
			Path file = FabricLoader.getInstance().getConfigDir().resolve("pocket_list.json");
			int entries = Lists.active().entries.size();
			Lists.save();
			Lists.data().lists.clear();
			Lists.load();
			check(Lists.active().entries.size() == entries, "lists survive a save and load");

			String good = Files.readString(file);
			Files.writeString(file, "{ not json");
			Lists.load();
			check(Files.exists(file.resolveSibling("pocket_list.json.broken")), "a broken file is backed up");
			Files.writeString(file, good);
			Lists.load();

			// A save that dies part-way (a crash, the power going) leaves the old file whole.
			try {
				SafeFiles.write(file, writer -> {
					writer.write("{ \"lists\": [ half-writ");
					writer.flush();
					throw new IOException("the game stopped here");
				});
				check(false, "the interrupted save should have failed");
			} catch (IOException expected) {
				// As it would in a crash.
			}
			check(Files.readString(file).equals(good), "an interrupted save leaves the lists file as it was");
			Lists.save();
			check(!Files.exists(file.resolveSibling("pocket_list.json.tmp")), "the next save leaves no temporary file behind");
		}
	}

	// ------------------------------------------------------------------ helpers

	/** Gives items and waits until they've reached the client's inventory. */
	private static void give(ClientGameTestContext ctx, TestServerContext server, Item item, int count) {
		int before = ctx.computeOnClient(mc -> mc.player.getInventory().countItem(item));
		server.runCommand("give @a " + BuiltInRegistries.ITEM.getKey(item) + " " + count);
		ctx.waitFor(mc -> mc.player.getInventory().countItem(item) >= before + count);
	}

	private static void recount(ClientGameTestContext ctx) {
		ctx.waitTicks(2);
		ctx.runOnClient(Counter::update);
	}

	private static void world(ClientGameTestContext ctx) {
		ctx.waitTicks(3);
	}

	private static String setblock(BlockPos pos, String block) {
		return "setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + block;
	}

	/** Right-clicks a block with whatever is in the main hand. */
	private static void useBlock(ClientGameTestContext ctx, BlockPos pos) {
		ctx.runOnClient(mc -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
	}

	private static void openAndClose(ClientGameTestContext ctx, BlockPos pos, Class<? extends AbstractContainerScreen<?>> screen) {
		useBlock(ctx, pos);
		ctx.waitForScreen(screen);
		ctx.waitTicks(3);
		close(ctx);
		recount(ctx);
	}

	private static void openInventory(ClientGameTestContext ctx, boolean recipeBook) {
		ctx.runOnClient(mc -> mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, recipeBook));
		ctx.getInput().pressKey(options -> options.keyInventory);
		ctx.waitForScreen(InventoryScreen.class);
		ctx.waitTicks(3);
	}

	private static void hoverSlotWith(ClientGameTestContext ctx, Item item) {
		int[] at = ctx.computeOnClient(mc -> {
			AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.screen;
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
		hover(ctx, at[0], at[1]);
	}

	/** Closes the screen the way Esc does, so the server knows a container was closed. */
	private static void close(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> {
			if (mc.screen instanceof AbstractContainerScreen<?>) {
				mc.player.closeContainer();
			} else {
				mc.setScreen(null);
			}
		});
		ctx.waitTick();
	}

	private static double scale(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
	}

	/** Moves the cursor to a point in GUI coordinates and lets the screen notice. */
	private static void hover(ClientGameTestContext ctx, int guiX, int guiY) {
		double scale = ctx.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
		ctx.getInput().setCursorPos(guiX * scale, guiY * scale);
		ctx.waitTicks(2);
	}

	private static void click(ClientGameTestContext ctx, int guiX, int guiY) {
		hover(ctx, guiX, guiY);
		ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTick();
	}

	private static RecipeBookComponent recipeBook(Minecraft mc) {
		return ((RecipeUpdateListener) mc.screen).getRecipeBookComponent();
	}

	@SuppressWarnings("unchecked")
	private static List<RecipeButton> visibleRecipeButtons(Minecraft mc) {
		RecipeBookPage page = ((RecipeBookComponentAccessor) recipeBook(mc)).pocketList$page();
		List<RecipeButton> buttons = ((List<RecipeButton>) field(page, RecipeBookPage.class, "buttons")).stream().filter(b -> b.visible).toList();
		check(!buttons.isEmpty(), "the recipe book shows recipes");
		return buttons;
	}

	private static Map<String, Integer> needs(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> {
			Map<String, Integer> out = new HashMap<>();
			for (Entry e : Lists.active().entries) {
				out.put(e.key(), e.need);
			}
			return out;
		});
	}

	private static Entry entry(ClientGameTestContext ctx, String key) {
		return ctx.computeOnClient(mc -> Lists.active().entries.stream().filter(e -> e.key().equals(key)).findFirst().orElse(null));
	}

	private static void expectHave(ClientGameTestContext ctx, String key, int have) {
		Entry e = entry(ctx, key);
		check(e != null, "the list has " + key);
		check(e.have() == have, key + ": expected to have " + have + ", counted " + e.have());
	}

	private static void expectNeed(ClientGameTestContext ctx, String key, int need) {
		ctx.waitTick();
		Entry e = entry(ctx, key);
		check(e != null, "the list has " + key);
		check(e.need == need, key + ": expected to need " + need + ", list says " + e.need);
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
