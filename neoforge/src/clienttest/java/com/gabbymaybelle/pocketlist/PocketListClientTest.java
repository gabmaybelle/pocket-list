package com.gabbymaybelle.pocketlist;

import static com.gabbymaybelle.pocketlist.ClientTest.check;
import static com.gabbymaybelle.pocketlist.ClientTest.command;
import static com.gabbymaybelle.pocketlist.ClientTest.createWorld;
import static com.gabbymaybelle.pocketlist.ClientTest.run;
import static com.gabbymaybelle.pocketlist.ClientTest.screenshot;
import static com.gabbymaybelle.pocketlist.ClientTest.waitTicks;
import static com.gabbymaybelle.pocketlist.ClientTest.waitUntil;

import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The NeoForge jar's in-game test, a part of the Fabric game test (fabric/src/gametest) that covers
 * what NeoForge does differently: the default keys, counting what's carried (bundles and shulker
 * boxes too), chest memory through the screen path, a shelf through NeoForge's item storage, and
 * the list and settings screens.
 */
final class PocketListClientTest {
	/** Where the test's chests and shelf go, next to where the player starts. */
	private static BlockPos here;
	/** Iron ingots left in the hand after putting some on the shelf. */
	private static int inHand;

	private PocketListClientTest() {
	}

	static void steps() {
		// The default keys are K and Z on every version (26.3 renumbered keys, so they're looked up by name).
		run(mc -> {
			check(PocketList.OPEN_KEY.getDefaultKey().getName().equals("key.keyboard.k"), "The open key isn't K by default: " + PocketList.OPEN_KEY.getDefaultKey().getName());
			check(PocketList.PIN_KEY.getDefaultKey().getName().equals("key.keyboard.z"), "The pin key isn't Z by default: " + PocketList.PIN_KEY.getDefaultKey().getName());
		});
		createWorld("pocket_list-clienttest");
		command("gamemode survival @a");

		counting();
		chestMemory();
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
		command("give @a bundle[bundle_contents=[{id:\"minecraft:torch\",count:10}]]");
		command("give @a white_shulker_box[container=[{slot:0,item:{id:\"minecraft:cobblestone\",count:64}}]]");
		waitUntil("the given items in the inventory", 200,
				mc -> mc.player.getInventory().countItem(Items.BUNDLE) >= 1 && mc.player.getInventory().countItem(BuiltInRegistries.ITEM.getOptional(Identifier.parse("minecraft:white_shulker_box")).orElseThrow()) >= 1
						&& mc.player.getInventory().countItem(Items.STICK) >= 3);
		recount();
		expectHave("#minecraft:logs", 12);
		expectHave("minecraft:stick", 3);
		expectHave("minecraft:torch", 10);
		expectHave("minecraft:cobblestone", 64);
		expectHave("minecraft:iron_ingot", 0);
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

		// A shelf holds things without opening a screen: using it with ingots in hand puts them on it,
		// and it's remembered through NeoForge's item storage.
		setblockAt(2, 2, "oak_shelf[facing=north]");
		command("item replace entity @a hotbar.8 with iron_ingot 2");
		waitUntil("two iron ingots in the hotbar", 100, mc -> mc.player.getInventory().countItem(Items.IRON_INGOT) == 2);
		run(mc -> mc.player.getInventory().setSelectedSlot(8));
		waitTicks(3);
		run(mc -> {
			BlockPos shelf = here.offset(2, 0, 2);
			// The middle of the shelf's front.
			mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(shelf).add(0, 0, -0.5), Direction.NORTH, shelf, false));
		});
		waitUntil("ingots put on the shelf", 100, mc -> mc.player.getInventory().countItem(Items.IRON_INGOT) < 2);
		run(mc -> inHand = mc.player.getInventory().countItem(Items.IRON_INGOT));
		waitTicks(ChestMemory.STORAGE_WAIT + 5);
		recount();
		// Four in the chests, and the two ingots split between the hand and the shelf.
		expectHave("minecraft:iron_ingot", 6);
		run(mc -> Lists.data().countChests = false);
		recount();
		run(mc -> expectHaveNow("minecraft:iron_ingot", inHand));
		run(mc -> Lists.data().countChests = true);
		recount();
		run(mc -> mc.player.getInventory().setSelectedSlot(0));
	}

	/** The list screen and its settings screen open, and the settings follow from the list screen. */
	private static void screens() {
		run(mc -> mc.gui.setScreen(new PocketListScreen(null)));
		waitUntil("the list screen", 40, mc -> mc.gui.screen() instanceof PocketListScreen);
		screenshot();
		run(mc -> mc.gui.setScreen(new PocketListSettingsScreen(mc.gui.screen())));
		waitUntil("the settings screen", 40, mc -> mc.gui.screen() instanceof PocketListSettingsScreen);
		run(mc -> mc.gui.setScreen(null));
		waitTicks(5);
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
		waitUntil("a chest screen", 100, mc -> mc.gui.screen() instanceof ContainerScreen);
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
