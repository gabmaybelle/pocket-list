package com.gabbymaybelle.pocketlist.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Hopper;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * {@code /pocket selftest}: the parts that need a running server, checked against its real recipes
 * and printed as pass or fail. It is what the headless server test runs; the pure maths has its own
 * unit tests. Checks: oak planks come from a log (4 a craft); sticks have more than one recipe
 * (planks and bamboo), so the chooser path exists; an iron ingot's furnace and blast furnace
 * recipes merge into one line; a torch's recipe reads coal-or-charcoal as one "or similar"
 * ingredient; such a line is named after the item the game lists first (a chest's "Oak Planks
 * (or similar)", a torch's "Coal (or similar)"); and breaking 64 sticks down with 10 in hand asks
 * for 28 planks.
 */
final class SelfTest {
	private SelfTest() {
	}

	static void run(CommandSender sender, Plugin plugin, Settings settings) {
		List<String> failures = new ArrayList<>();
		int checks = 0;

		List<FoundRecipe> planks = ServerRecipes.lookup(Material.OAK_PLANKS);
		checks++;
		if (planks.isEmpty() || planks.get(0).makes() != 4 || !planks.get(0).ingredients().containsKey(List.of("minecraft:oak_log")) && !hasAny(planks.get(0), "minecraft:oak_log")) {
			failures.add("oak planks: expected a crafting recipe from oak logs making 4, got " + describe(planks));
		}

		List<FoundRecipe> sticks = ServerRecipes.lookup(Material.STICK);
		checks++;
		if (sticks.size() < 2) failures.add("sticks: expected two recipes (planks, bamboo), got " + describe(sticks));
		checks++;
		if (!sticks.isEmpty() && sticks.get(0).stationOrder() != FoundRecipe.CRAFTING) failures.add("sticks: the crafting recipe should sort first, got " + describe(sticks));

		List<FoundRecipe> iron = ServerRecipes.lookup(Material.IRON_INGOT);
		checks++;
		boolean merged = false;
		for (FoundRecipe r : iron) if (r.station().contains("(or ")) merged = true;
		if (!merged) failures.add("iron ingot: expected the furnace and blast furnace recipes merged into one, got " + describe(iron));

		List<FoundRecipe> torch = ServerRecipes.lookup(Material.TORCH);
		checks++;
		boolean orSimilar = false;
		for (FoundRecipe r : torch) for (List<String> ing : r.ingredients().keySet()) if (ing.size() > 1) orSimilar = true;
		if (torch.isEmpty() || !orSimilar) failures.add("torch: expected coal-or-charcoal as one ingredient with alternatives, got " + describe(torch));

		List<FoundRecipe> chest = ServerRecipes.lookup(Material.CHEST);
		checks++;
		List<String> chestPlanks = null;
		for (FoundRecipe r : chest) for (List<String> ing : r.ingredients().keySet()) if (ing.contains("minecraft:oak_planks")) chestPlanks = ing;
		List<String> torchCoal = null;
		for (FoundRecipe r : torch) for (List<String> ing : r.ingredients().keySet()) if (ing.contains("minecraft:charcoal")) torchCoal = ing;
		if (chestPlanks == null || !chestPlanks.get(0).equals("minecraft:oak_planks") || torchCoal == null || !torchCoal.get(0).equals("minecraft:coal")) {
			failures.add("names: a chest's planks should read Oak Planks (or similar) and a torch's Coal (or similar), got " + chestPlanks + " and " + torchCoal);
		}

		checks++;
		if (!sticks.isEmpty()) {
			PocketListData list = new PocketListData("selftest");
			Entry e = list.topUp(List.of("minecraft:stick"), 64);
			e.have = 10;
			FoundRecipe r = sticks.get(0);
			r.apply(list, e);
			Entry planksLine = null;
			for (Entry x : list.entries) if (x.items.size() > 1 && x.items.contains("minecraft:oak_planks") || x.key().equals("minecraft:oak_planks")) planksLine = x;
			if (list.find("minecraft:stick") != null || planksLine == null || planksLine.need != 28) {
				failures.add("breakdown maths: 64 sticks with 10 in hand should become 28 planks, got " + lines(list));
			}
		}

		checks++;
		Paste.Result pasted = Paste.read(Paste.chatLines("64 oak planks; 3 torches, nonsense"), PocketCommand::itemsNamed);
		List<String> pastedKeys = new ArrayList<>();
		for (Entry x : pasted.entries()) pastedKeys.add(x.need + " " + x.key());
		if (!pastedKeys.equals(List.of("64 minecraft:oak_planks", "3 minecraft:torch")) || !pasted.unknown().equals(List.of("nonsense"))) {
			failures.add("paste: expected 64 oak planks and 3 torches with nonsense unknown, got " + pastedKeys + " unknown " + pasted.unknown());
		}
		checks++;
		List<String> planksTag = PocketCommand.itemsNamed("#minecraft:planks");
		if (planksTag == null || planksTag.size() < 10 || !planksTag.get(0).equals("minecraft:oak_planks")) {
			failures.add("paste: #minecraft:planks should be every plank with oak first, got " + planksTag);
		}

		int[] total = { checks };
		chests(plugin, settings, failures, total, () -> {
			if (failures.isEmpty()) {
				PocketCommand.say(sender, "Pocket List selftest: passed (" + total[0] + " checks)");
			} else {
				PocketCommand.fail(sender, "Pocket List selftest: failed: " + String.join("; ", failures));
			}
		});
	}

	/**
	 * Chest memory against real blocks, high above the world's spawn: a double chest with 20 oak planks
	 * whose two halves were both remembered counts 20 (once) from 3 blocks away and nothing from 40; a
	 * hopper on top filling it is counted without anyone opening it; broken, it's forgotten. The hopper
	 * needs the world to run a while, so this part reports back about three seconds later.
	 */
	private static void chests(Plugin plugin, Settings settings, List<String> failures, int[] checks, Runnable done) {
		World world = Bukkit.getWorlds().get(0);
		Location spawn = world.getSpawnLocation();
		int x = spawn.getBlockX(), y = Math.min(world.getMaxHeight() - 4, 310), z = spawn.getBlockZ();
		// Loaded without a command, so the console shows nothing but the result.
		java.util.Set<long[]> forced = new java.util.HashSet<>();
		for (int cx : new int[] { x >> 4, (x + 1) >> 4 }) {
			if (!world.isChunkForceLoaded(cx, z >> 4)) {
				world.setChunkForceLoaded(cx, z >> 4, true);
				forced.add(new long[] { cx, z >> 4 });
			}
		}
		// Facing north, a chest's left half has its other half to the east.
		world.getBlockAt(x, y, z).setBlockData(Bukkit.createBlockData("minecraft:chest[facing=north,type=left]"), false);
		world.getBlockAt(x + 1, y, z).setBlockData(Bukkit.createBlockData("minecraft:chest[facing=north,type=right]"), false);
		Block left = world.getBlockAt(x, y, z), right = world.getBlockAt(x + 1, y, z);
		Remembered memory = new Remembered();
		memory.add(ChestMemory.place(left));
		memory.add(ChestMemory.place(right));
		Inventory both = left.getState(false) instanceof Chest c ? c.getInventory() : null;
		checks[0]++;
		if (!(both instanceof DoubleChestInventory)) {
			failures.add("chest memory: the two chests didn't make a double chest");
			for (long[] c : forced) world.setChunkForceLoaded((int) c[0], (int) c[1], false);
			done.run();
			return;
		}
		both.addItem(new ItemStack(Material.OAK_PLANKS, 20));
		Location near = new Location(world, x + 0.5, y, z + 3.5), far = new Location(world, x + 0.5, y, z + 40.5);
		checks[0]++;
		int nearCount = planks(memory, near, settings);
		if (nearCount != 20) failures.add("chest memory: a remembered double chest with 20 oak planks should count 20 from 3 blocks, got " + nearCount);
		checks[0]++;
		int farCount = planks(memory, far, settings);
		if (farCount != 0) failures.add("chest memory: from 40 blocks away it should count 0, got " + farCount);

		world.getBlockAt(x, y + 1, z).setBlockData(Bukkit.createBlockData("minecraft:hopper[facing=down]"));
		if (world.getBlockAt(x, y + 1, z).getState(false) instanceof Hopper hopper) hopper.getInventory().addItem(new ItemStack(Material.OAK_PLANKS, 5));
		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			checks[0]++;
			int filled = planks(memory, near, settings);
			if (filled != 25) failures.add("chest memory: a hopper putting 5 more planks in should make 25 without opening it, got " + filled);
			for (int[] p : new int[][] { { x, y + 1, z }, { x, y, z }, { x + 1, y, z } }) world.getBlockAt(p[0], p[1], p[2]).setType(Material.AIR, false);
			for (org.bukkit.entity.Item dropped : world.getEntitiesByClass(org.bukkit.entity.Item.class)) {
				if (dropped.getLocation().distanceSquared(near) < 100) dropped.remove();
			}
			checks[0]++;
			int gone = planks(memory, near, settings);
			if (gone != 0 || !memory.isEmpty()) failures.add("chest memory: broken chests should count 0 and be forgotten, got " + gone + " with " + memory.size() + " remembered");
			for (long[] c : forced) world.setChunkForceLoaded((int) c[0], (int) c[1], false);
			done.run();
		}, 60);
	}

	/** Oak planks counted from remembered containers, as the plugin counts them (nothing with chest memory off). */
	private static int planks(Remembered memory, Location here, Settings settings) {
		if (!settings.chestMemory()) return 0;
		Map<String, Integer> counts = new java.util.HashMap<>();
		ChestMemory.addNearby(memory, here, settings.chestRadius(), null, counts);
		return counts.getOrDefault("minecraft:oak_planks", 0);
	}

	private static boolean hasAny(FoundRecipe r, String item) {
		for (List<String> ing : r.ingredients().keySet()) if (ing.contains(item)) return true;
		return false;
	}

	private static String describe(List<FoundRecipe> recipes) {
		List<String> out = new ArrayList<>();
		for (FoundRecipe r : recipes) out.add(r.station() + ": " + r.describe());
		return out.isEmpty() ? "nothing" : String.join(" / ", out);
	}

	private static String lines(PocketListData list) {
		List<String> out = new ArrayList<>();
		for (Entry e : list.entries) out.add(e.need + " " + e.key());
		return String.join(", ", out);
	}

	@SuppressWarnings("unused")
	private static String keys(Map<List<String>, Integer> ingredients) {
		return ingredients.toString();
	}
}
