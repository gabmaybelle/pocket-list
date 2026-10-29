package com.gabbymaybelle.pocketlist.paper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A recipe that makes the item, boiled down to what Break down needs: where it's made, its
 * ingredients (each a list of item keys that all do, with a count) and how many it makes. The same
 * tidying as the mod's {@code Recipes.tidy}: identical recipes merge into one ("Furnace (or Blast
 * Furnace)"), storage-block unpacking sorts last, then by station, then by how many ingredients.
 */
public record FoundRecipe(String station, int stationOrder, LinkedHashMap<List<String>, Integer> ingredients, int makes) {
	/** Crafting table and the 2x2 grid sort first, furnaces next, everything else after. */
	public static final int CRAFTING = 0, FURNACE = 1, OTHER = 2;

	/** One ingredient wanted once that makes nine: a storage block coming apart, the least useful suggestion. */
	public boolean unpacking() {
		return ingredients.size() == 1 && ingredients.values().iterator().next() == 1 && makes == 9;
	}

	public boolean sameAs(FoundRecipe other) {
		return makes == other.makes && ingredients.equals(other.ingredients);
	}

	public FoundRecipe mergedWith(FoundRecipe other) {
		return new FoundRecipe(station + " (or " + other.station + ")", Math.min(stationOrder, other.stationOrder), ingredients, makes);
	}

	/** Merges duplicates and sorts, so the first recipe is the one to apply when there's no choice. */
	public static List<FoundRecipe> tidy(List<FoundRecipe> found) {
		List<FoundRecipe> out = new ArrayList<>();
		for (FoundRecipe r : found) {
			if (r.ingredients.isEmpty() || r.makes <= 0) continue;
			boolean merged = false;
			for (int i = 0; i < out.size(); i++) {
				FoundRecipe o = out.get(i);
				if (o.sameAs(r)) {
					if (!o.station.equals(r.station) && !o.station.contains(r.station)) out.set(i, o.mergedWith(r));
					merged = true;
					break;
				}
			}
			if (!merged) out.add(r);
		}
		out.sort(Comparator.comparing(FoundRecipe::unpacking).thenComparingInt(FoundRecipe::stationOrder).thenComparingInt(r -> r.ingredients.size()));
		return out;
	}

	/** How many crafts cover what's still needed: {@code ceil(left / makes)}. */
	public int crafts(Entry entry) {
		return (entry.left() + makes - 1) / makes;
	}

	/** A one-line description: "2 Oak Planks, makes 4". */
	public String describe() {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<List<String>, Integer> ing : ingredients.entrySet()) {
			if (!sb.isEmpty()) sb.append(", ");
			sb.append(ing.getValue()).append(' ').append(Names.english(ing.getKey().get(0)));
			if (ing.getKey().size() > 1) sb.append(" (or similar)");
		}
		return sb + ", makes " + makes;
	}

	/**
	 * Applies this recipe to the line, the mod's {@code Lists.breakDown}: the line goes, and each
	 * ingredient times the crafts is put in its place, topping up a line that already exists instead
	 * of adding a second. One level only; the player can break the new lines down again.
	 */
	public void apply(PocketListData list, Entry entry) {
		int crafts = Math.max(1, crafts(entry));
		int at = list.entries.indexOf(entry);
		if (at < 0) return;
		list.entries.remove(at);
		for (Map.Entry<List<String>, Integer> ing : ingredients.entrySet()) {
			int need = Entry.clamp(ing.getValue() * crafts);
			Entry existing = list.find(String.join("|", ing.getKey()));
			if (existing != null) {
				existing.need = Entry.clamp(existing.need + need);
			} else {
				list.entries.add(Math.min(at, list.entries.size()), new Entry(ing.getKey(), need));
				at++;
			}
		}
	}
}
