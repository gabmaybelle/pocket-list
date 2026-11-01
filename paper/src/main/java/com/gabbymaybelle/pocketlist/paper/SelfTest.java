package com.gabbymaybelle.pocketlist.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;

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

	static void run(CommandSender sender) {
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

		if (failures.isEmpty()) {
			PocketCommand.say(sender, "Pocket List selftest: passed (" + checks + " checks)");
		} else {
			PocketCommand.fail(sender, "Pocket List selftest: failed: " + String.join("; ", failures));
		}
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
