package com.gabbymaybelle.pocketlist;

import com.gabbymaybelle.pocketlist.harness.ClientGameTest;
import com.gabbymaybelle.pocketlist.harness.ClientGameTestContext;
import com.gabbymaybelle.pocketlist.harness.TestServerContext;
import com.gabbymaybelle.pocketlist.harness.TestSingleplayerContext;
import com.gabbymaybelle.pocketlist.mixin.RecipeBookComponentAccessor;
import java.lang.reflect.Field;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.Difficulty;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.Items;

/**
 * Takes the screenshots for the mod page, in a normal world at 1280x720. Only runs when the
 * POCKET_LIST_SHOTS environment variable is set:
 * `POCKET_LIST_SHOTS=1 ./gradlew :fabric:runClientGameTest` (add -PtestViewer=jei for the JEI shot).
 */
public class ScreenshotTour implements ClientGameTest {
	static boolean enabled() {
		return System.getenv("POCKET_LIST_SHOTS") != null;
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (!enabled()) {
			return;
		}
		ctx.getInput().resizeWindow(1280, 720);
		ctx.runOnClient(mc -> {
			mc.options.guiScale().set(2);
			mc.resizeDisplay();
		});
		try (TestSingleplayerContext world = ctx.worldBuilder().normalWorld().adjustSettings(state -> {
			state.setSeed("pocket list");
			state.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
			state.setDifficulty(Difficulty.PEACEFUL);
			state.setAllowCommands(true);
		}).create()) {
			world.waitForChunksRender();
			TestServerContext server = world.getServer();
			server.runCommand("time set 5000");
			// Stand on the treetops with a view, rather than inside a tree.
			server.runCommand("execute as @a at @s positioned over motion_blocking run tp @s ~ ~ ~ 135 12");
			server.runCommand("weather clear");
			server.runCommand("recipe give @a *");
			server.runCommand("give @a oak_planks 24");
			server.runCommand("give @a glass_pane 32");
			server.runCommand("give @a torch 5");
			server.runCommand("give @a lantern 3");
			server.runCommand("give @a iron_nugget 30");
			ctx.runOnClient(mc -> {
				Lists.data().lists.clear();
				Lists.active().name = "Cottage";
				Lists.add(Entry.ofItem(Items.OAK_PLANKS, 64));
				Lists.add(Entry.ofItem(Items.GLASS_PANE, 32));
				Lists.add(Entry.ofItem(Items.LANTERN, 8));
				Lists.add(Entry.ofItem(Items.SPRUCE_STAIRS, 24));
				Lists.add(Entry.ofItem(Items.COBBLESTONE, 64));
				Lists.add(Entry.ofItem(Items.FLOWER_POT, 4));
				Lists.data().welcomed = true;
				mc.getToasts().clear();
				mc.player.getInventory().selected = 8;
			});
			ctx.waitTicks(40);
			world.waitForChunksRender();
			ctx.runOnClient(mc -> {
				mc.getToasts().clear();
				// The setup commands' messages aren't part of the picture.
				mc.gui.getChat().clearMessages(false);
			});
			ctx.runOnClient(Counter::update);
			ctx.waitTicks(5);
			ctx.takeScreenshot("page_hud");

			ctx.getInput().pressKey(PocketList.openKey);
			ctx.waitForScreen(PocketListScreen.class);
			ctx.waitTicks(5);
			ctx.takeScreenshot("page_list_screen");

			// The chooser, for iron ingots.
			ctx.runOnClient(mc -> {
				Lists.add(Entry.ofItem(Items.IRON_INGOT, 9));
				((PocketListScreen) mc.screen).changed();
				Entry iron = Lists.active().entries.getLast();
				mc.setScreen(new RecipeChooserScreen((PocketListScreen) mc.screen, iron, Recipes.forItem(mc, Items.IRON_INGOT)));
			});
			ctx.waitTicks(5);
			ctx.takeScreenshot("page_chooser");
			ctx.runOnClient(mc -> {
				mc.setScreen(null);
				Lists.active().entries.removeIf(e -> e.key().equals("minecraft:iron_ingot"));
			});

			// The inventory with the recipe book open, hovering a recipe, and the list beside it.
			ctx.runOnClient(mc -> mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, true));
			ctx.getInput().pressKey(options -> options.keyInventory);
			ctx.waitForScreen(InventoryScreen.class);
			ctx.waitTicks(10);
			RecipeButton button = ctx.computeOnClient(mc -> visibleRecipeButtons(mc).get(3));
			double scale = ctx.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
			ctx.getInput().setCursorPos((button.getX() + 12) * scale, (button.getY() + 12) * scale);
			ctx.waitTicks(5);
			ctx.takeScreenshot(FabricLoader.getInstance().isModLoaded("jei") ? "page_jei" : "page_recipe_book");
			ctx.runOnClient(mc -> mc.player.closeContainer());
		}
	}

	@SuppressWarnings("unchecked")
	private static List<RecipeButton> visibleRecipeButtons(net.minecraft.client.Minecraft mc) {
		RecipeBookPage page = ((RecipeBookComponentAccessor) ((RecipeUpdateListener) mc.screen).getRecipeBookComponent()).pocketList$page();
		try {
			Field f = RecipeBookPage.class.getDeclaredField("buttons");
			f.setAccessible(true);
			return ((List<RecipeButton>) f.get(page)).stream().filter(b -> b.visible).toList();
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}
}
