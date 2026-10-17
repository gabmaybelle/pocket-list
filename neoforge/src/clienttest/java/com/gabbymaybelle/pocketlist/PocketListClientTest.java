package com.gabbymaybelle.pocketlist;

import static com.gabbymaybelle.pocketlist.ClientTest.check;
import static com.gabbymaybelle.pocketlist.ClientTest.command;
import static com.gabbymaybelle.pocketlist.ClientTest.createWorld;
import static com.gabbymaybelle.pocketlist.ClientTest.field;
import static com.gabbymaybelle.pocketlist.ClientTest.moveMouse;
import static com.gabbymaybelle.pocketlist.ClientTest.pressKey;
import static com.gabbymaybelle.pocketlist.ClientTest.run;
import static com.gabbymaybelle.pocketlist.ClientTest.screenshot;
import static com.gabbymaybelle.pocketlist.ClientTest.waitTicks;
import static com.gabbymaybelle.pocketlist.ClientTest.waitUntil;

import com.gabbymaybelle.pocketlist.compat.Viewers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/**
 * The NeoForge jar's in-game test, a part of the Fabric game test (fabric/src/gametest) that covers
 * what NeoForge does differently: the default keys, counting what's carried (bundles and shulker
 * boxes too), chest memory through the screen path, a decorated pot through NeoForge's item storage,
 * pinning with the pin key through NeoForge's screen key event (and, with JEI or REI installed,
 * from the viewer's item list, but not while typing in its search box), and the list and
 * settings screens.
 */
final class PocketListClientTest {
	/** Where the test's chests and shelf go, next to where the player starts. */
	private static BlockPos here;
	/** What the list needed before a key press, to compare with after it. */
	private static Map<String, Integer> before;
	/** A spot on screen (GUI coordinates) to point at or click; set by a step, used by the next. */
	private static int[] target;
	/** Where the recipe viewer's item list showed an item; null if none was found. */
	private static int[] viewerItem;

	private PocketListClientTest() {
	}

	static void steps() {
		// The default keys are K and Z.
		run(mc -> {
			check(PocketList.OPEN_KEY.getDefaultKey().getName().equals("key.keyboard.k"), "The open key isn't K by default: " + PocketList.OPEN_KEY.getDefaultKey().getName());
			check(PocketList.PIN_KEY.getDefaultKey().getName().equals("key.keyboard.z"), "The pin key isn't Z by default: " + PocketList.PIN_KEY.getDefaultKey().getName());
		});
		createWorld("pocket_list-clienttest");
		command("gamemode survival @a");
		// The first list is made while the game starts, before its language files are read.
		run(mc -> check(Lists.active().name.equals("List 1"), "A new player's first list isn't called List 1: " + Lists.active().name));

		counting();
		chestMemory();
		pinning();
		if (ModList.get().isLoaded("jei") || ModList.get().isLoaded("roughlyenoughitems")) {
			recipeViewer();
		}
		screens();
	}

	/** Counting what's carried, including inside a bundle and a shulker box. */
	private static void counting() {
		run(mc -> {
			Lists.data().lists.clear();
			Lists.data().active = 0;
			Lists.active().name = "Cottage";
			Lists.add(Entry.ofTag(ItemTags.LOGS, 10));
			Lists.add(Entry.ofItem(Items.STICK, 8));
			Lists.add(Entry.ofItem(Items.COBBLESTONE, 32));
			Lists.add(Entry.ofItem(Items.TORCH, 16));
			Lists.add(Entry.ofItem(Items.IRON_INGOT, 5));
		});
		command("give @a minecraft:oak_log 6");
		command("give @a minecraft:spruce_log 6");
		command("give @a minecraft:stick 3");
		// Bundles are an experiment on 1.21.1 that this world doesn't have, so the torches come in a
		// shulker box; a bundle is checked on its own below.
		command("give @a orange_shulker_box[container=[{slot:0,item:{id:\"minecraft:torch\",count:10}}]]");
		command("give @a white_shulker_box[container=[{slot:0,item:{id:\"minecraft:cobblestone\",count:64}}]]");
		waitUntil("the given items in the inventory", 200,
				mc -> mc.player.getInventory().countItem(Items.ORANGE_SHULKER_BOX) >= 1 && mc.player.getInventory().countItem(Items.WHITE_SHULKER_BOX) >= 1
						&& mc.player.getInventory().countItem(Items.STICK) >= 3);
		recount();
		expectHave("#minecraft:logs", 12);
		expectHave("minecraft:stick", 3);
		expectHave("minecraft:torch", 10);
		expectHave("minecraft:cobblestone", 64);
		expectHave("minecraft:iron_ingot", 0);
		// What's in a bundle counts too, and a bundle in a shulker box.
		run(mc -> {
			ItemStack bundle = new ItemStack(Items.BUNDLE);
			bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(new ItemStack(Items.TORCH, 7))));
			ItemStack box = new ItemStack(Items.WHITE_SHULKER_BOX);
			box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(bundle.copy())));
			Map<Item, Integer> totals = new HashMap<>();
			Counter.add(bundle, totals, 0);
			Counter.add(box, totals, 0);
			int torches = totals.getOrDefault(Items.TORCH, 0);
			check(torches == 14, "Torches in a bundle, and in a bundle in a shulker box: expected 14, counted " + torches);
		});
		screenshot();
	}

	/** Chests through their screens, never twice for a double chest, never an ender chest; a shelf through item storage. */
	private static void chestMemory() {
		run(mc -> here = mc.player.blockPosition());
		// A double chest facing north: the left half connects east, to the right half.
		setblockAt(2, 0, "chest[facing=north,type=left]{Items:[{Slot:0b,id:\"minecraft:iron_ingot\",count:3}]}");
		setblockAt(3, 0, "chest[facing=north,type=right]{Items:[{Slot:0b,id:\"minecraft:iron_ingot\",count:1}]}");
		setblockAt(4, 0, "ender_chest[facing=north]");
		command("item replace entity @a enderchest.0 with iron_ingot 1");

		openAndClose(2, 0);
		expectHave("minecraft:iron_ingot", 4);
		// The same double chest from its other half must not count twice.
		openAndClose(3, 0);
		expectHave("minecraft:iron_ingot", 4);
		// Ender chests are never remembered.
		openAndClose(4, 0);
		expectHave("minecraft:iron_ingot", 4);

		// A decorated pot holds things without opening a screen, and the game tells every player what's
		// in it when its chunk loads (or a command changes it). Using it with an empty hand only wobbles
		// it; it's remembered through NeoForge's item storage.
		setblockAt(2, 2, "decorated_pot{item:{id:\"minecraft:iron_ingot\",count:2}}");
		command("item replace entity @a hotbar.8 with air");
		run(mc -> mc.player.getInventory().selected = 8);
		recount();
		// Not used yet: nothing from the pot counts.
		expectHave("minecraft:iron_ingot", 4);
		run(mc -> {
			BlockPos pot = here.offset(2, 0, 2);
			mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pot), Direction.UP, pot, false));
		});
		waitTicks(ChestMemory.STORAGE_WAIT + 5);
		run(mc -> check(mc.screen == null, "A decorated pot opened a screen"));
		recount();
		// Four in the chests and two in the pot.
		expectHave("minecraft:iron_ingot", 6);
		run(mc -> Lists.data().countChests = false);
		recount();
		expectHave("minecraft:iron_ingot", 0);
		run(mc -> Lists.data().countChests = true);
		// When the game is told the pot changed, the count follows without using it again.
		run(mc -> {
			BlockPos pot = here.offset(2, 0, 2);
			IntegratedServer server = mc.getSingleplayerServer();
			String command = "data merge block " + pot.getX() + " " + pot.getY() + " " + pot.getZ() + " {item:{id:\"minecraft:iron_ingot\",count:3}}";
			server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
		});
		waitTicks(4);
		recount();
		expectHave("minecraft:iron_ingot", 7);
		run(mc -> mc.player.getInventory().selected = 0);
	}

	/**
	 * The pin key over an inventory slot adds one of that item to the list; with Shift, a stack. The
	 * key goes through the game's keyboard handler, so it reaches Pocket List by NeoForge's screen
	 * key event, as in play.
	 */
	private static void pinning() {
		openInventory();
		pointAtSlotWith(Items.STICK);
		run(mc -> check(Pins.hoveredItem(mc, mc.screen).is(Items.STICK), "The mouse isn't over the sticks' slot"));
		run(mc -> before = needs());
		pressKey("key.keyboard.z", false);
		run(mc -> expectNeed("minecraft:stick", before.getOrDefault("minecraft:stick", 0) + 1));
		pressKey("key.keyboard.z", true);
		run(mc -> expectNeed("minecraft:stick", before.getOrDefault("minecraft:stick", 0) + 1 + new ItemStack(Items.STICK).getMaxStackSize()));
		// With nothing under the mouse, the key pins nothing.
		moveMouse(1, 1);
		run(mc -> before = needs());
		pressKey("key.keyboard.z", false);
		run(mc -> check(needs().equals(before), "The pin key pinned something with nothing under the mouse: " + needs()));
		screenshot();
		run(mc -> mc.setScreen(null));
		waitTicks(3);
	}

	/** With JEI or REI installed: pin from its item list, keep typing in its search box, and use its recipes. */
	private static void recipeViewer() {
		run(mc -> Lists.active().entries.clear());
		openInventory();
		// The viewer fills in its item list a moment after the screen opens.
		waitTicks(20);
		findViewerItem();
		run(mc -> check(viewerItem != null, "Found no item in the recipe viewer's list on the right of the screen"));
		pressKey("key.keyboard.z", false);
		run(mc -> check(Lists.active().entries.size() == 1, "Pinning from the recipe viewer's list didn't add the item: " + needs()));
		screenshot();

		// Typing in the viewer's search box types; it doesn't pin. The box is at the bottom, in the
		// middle (REI) or under the item list (JEI).
		clickSearchBox();
		screenshot();
		run(mc -> check(Pins.typingInViewer(), "Couldn't click into the recipe viewer's search box after " + searchClicks
				+ " clicks along the bottom of a " + mc.screen.width + "x" + mc.screen.height + " screen (focused: "
				+ mc.screen.getFocused() + ")"));
		run(mc -> before = needs());
		pressKey("key.keyboard.z", false);
		run(mc -> check(needs().equals(before), "Pressing Z while typing in the recipe viewer's search pinned something: " + needs()));
		run(mc -> check(!Viewers.all().isEmpty() && !Viewers.all().getFirst().recipesFor(Items.LANTERN).isEmpty(),
				"The recipe viewer doesn't know how to make a lantern"));
		run(mc -> mc.setScreen(null));
		waitTicks(3);
	}

	/** Points the mouse at spots right of the inventory, one per frame, until the viewer has an item under it. */
	private static void findViewerItem() {
		List<int[]> spots = new ArrayList<>();
		run(mc -> {
			viewerItem = null;
			int width = mc.screen.width, height = mc.screen.height;
			for (int y = 30; y < height - 30; y += 18) {
				for (int x = width - 12; x > width / 2 + 100; x -= 18) {
					spots.add(new int[] { x, y });
				}
			}
		});
		ClientTest.add(new ClientTest.Step() {
			private int next;
			private int wait;

			@Override
			public boolean tick(Minecraft mc) {
				if (next > 0 && wait > 0) {
					// Give the screen a frame to see where the mouse went.
					wait--;
					return false;
				}
				if (next > 0 && !Pins.hoveredItem(mc, mc.screen).isEmpty()) {
					viewerItem = spots.get(next - 1);
					return true;
				}
				if (next >= spots.size()) {
					return true;
				}
				int[] spot = spots.get(next++);
				ClientTest.placeMouse(mc, spot[0], spot[1]);
				wait = 1;
				return false;
			}
		});
	}

	/**
	 * Clicks the likely places of the viewer's search box until one of them takes the typing: the
	 * bottom middle (REI) and along the bottom right, under the item list (JEI). Each click is a
	 * press, a frame, then the release, as a player's click arrives.
	 */
	private static void clickSearchBox() {
		ClientTest.add(new ClientTest.Step() {
			private int next;
			private int phase;
			private int wait;

			@Override
			public boolean tick(Minecraft mc) {
				if (wait > 0) {
					wait--;
					return false;
				}
				int width = mc.screen.width, height = mc.screen.height;
				int[][] spots = { { width / 2, height - 10 }, { width - 70, height - 10 }, { width - 70, height - 14 },
						{ width - 40, height - 12 }, { width - 100, height - 12 }, { width - 70, height - 7 }, { width - 70, height - 18 } };
				if (phase == 0) {
					if (Pins.typingInViewer() || next >= spots.length) {
						searchClicks = next;
						return true;
					}
					ClientTest.placeMouse(mc, spots[next][0], spots[next][1]);
					ClientTest.mouseButton(mc, true);
					phase = 1;
				} else {
					ClientTest.mouseButton(mc, false);
					phase = 0;
					next++;
					wait = 2;
				}
				return false;
			}
		});
	}

	/** How many spots clickSearchBox tried, for the failure message. */
	private static int searchClicks;

	private static void openInventory() {
		run(mc -> {
			mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, false);
			mc.setScreen(new InventoryScreen(mc.player));
		});
		waitUntil("the inventory screen", 40, mc -> mc.screen instanceof InventoryScreen);
		waitTicks(3);
	}

	/** Points the mouse at the first inventory slot holding this item. */
	private static void pointAtSlotWith(Item item) {
		run(mc -> {
			AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.screen;
			int left = (int) field(screen, AbstractContainerScreen.class, "leftPos");
			int top = (int) field(screen, AbstractContainerScreen.class, "topPos");
			target = null;
			for (Slot slot : screen.getMenu().slots) {
				if (slot.getItem().is(item)) {
					target = new int[] { left + slot.x + 8, top + slot.y + 8 };
					break;
				}
			}
			check(target != null, "No inventory slot holds " + item);
			ClientTest.placeMouse(mc, target[0], target[1]);
		});
		waitTicks(3);
	}

	private static Map<String, Integer> needs() {
		Map<String, Integer> out = new HashMap<>();
		for (Entry e : Lists.active().entries) {
			out.put(e.key(), e.need);
		}
		return out;
	}

	private static void expectNeed(String key, int need) {
		Integer now = needs().get(key);
		check(now != null && now == need, key + ": expected the list to need " + need + ", it says " + now);
	}

	/** The list screen and its settings screen open, and the settings follow from the list screen. */
	private static void screens() {
		run(mc -> mc.setScreen(new PocketListScreen(null)));
		waitUntil("the list screen", 40, mc -> mc.screen instanceof PocketListScreen);
		screenshot();
		listsAsText();
		run(mc -> mc.setScreen(new PocketListSettingsScreen(mc.screen)));
		waitUntil("the settings screen", 40, mc -> mc.screen instanceof PocketListSettingsScreen);
		run(mc -> mc.setScreen(null));
		waitTicks(5);
	}

	/** Litematica's material list as its Write to file button writes it (the Fabric test has the .csv too). */
	private static final String LITEMATICA_TABLE = """
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

	/** Paste List from the clipboard (here a Litematica table), and Copy List's text pasting back as the same list. */
	private static void listsAsText() {
		run(mc -> {
			int lists = Lists.data().lists.size();
			mc.keyboardHandler.setClipboard(LITEMATICA_TABLE);
			((PocketListScreen) mc.screen).pasteList(mc.keyboardHandler.getClipboard(), null);
			check(Lists.data().lists.size() == lists + 1, "Paste List didn't make a new list");
			check(Lists.active().name.equals("Cottage roof"), "A pasted Litematica list isn't named after its schematic: " + Lists.active().name);
			check(needs().equals(Map.of("minecraft:oak_planks", 320, "minecraft:glass_pane", 48)),
					"A pasted Litematica list doesn't have each item's Total: " + needs());
			Map<String, Integer> pasted = needs();
			ListText.Result copied = ListText.read(ListText.write(Lists.active()), mc.level.enabledFeatures());
			Map<String, Integer> back = new HashMap<>();
			copied.entries().forEach(e -> back.put(e.key(), e.need));
			check(back.equals(pasted) && copied.unknown().isEmpty(), "Copy List's text doesn't paste back as the same list: " + back + " " + copied.unknown());
		});
		screenshot();
		run(mc -> {
			Lists.data().lists.removeLast();
			Lists.data().active = 0;
			Lists.save();
		});
	}

	/** Places a block this far east and south of where the player started, on the singleplayer server. */
	private static void setblockAt(int east, int south, String block) {
		run(mc -> {
			BlockPos pos = here.offset(east, 0, south);
			IntegratedServer server = mc.getSingleplayerServer();
			String command = "setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + block;
			server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
		});
		waitTicks(4);
	}

	private static void openAndClose(int east, int south) {
		run(mc -> {
			BlockPos pos = here.offset(east, 0, south);
			mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
		});
		waitUntil("a chest screen", 100, mc -> mc.screen instanceof ContainerScreen);
		waitTicks(3);
		run(mc -> mc.player.closeContainer());
		waitTicks(2);
		recount();
	}

	private static void recount() {
		waitTicks(2);
		run(Counter::update);
	}

	private static void expectHave(String key, int have) {
		run(mc -> expectHaveNow(key, have));
	}

	private static void expectHaveNow(String key, int have) {
		Entry e = Lists.active().entries.stream().filter(entry -> entry.key().equals(key)).findFirst().orElse(null);
		check(e != null, "The list has " + key);
		check(e.have() == have, key + ": expected to have " + have + ", counted " + e.have());
	}
}
