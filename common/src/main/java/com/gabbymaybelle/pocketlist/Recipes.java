package com.gabbymaybelle.pocketlist;

import com.gabbymaybelle.pocketlist.compat.RecipeViewer;
import com.gabbymaybelle.pocketlist.compat.Viewers;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

/**
 * Finds the ways to make an item, from the best source there is:
 * <ol>
 * <li>your own world, in single player or a world you're hosting, which knows every recipe;</li>
 * <li>an installed recipe viewer (JEI or REI);</li>
 * <li>your recipe book, which only has the recipes you've unlocked.</li>
 * </ol>
 */
public final class Recipes {
	private Recipes() {
	}

	public static List<FoundRecipe> forItem(Minecraft mc, Item item) {
		if (mc.level == null || item == null) {
			return List.of();
		}
		List<FoundRecipe> found = fromWorld(mc, item);
		if (found.isEmpty()) {
			for (RecipeViewer viewer : Viewers.all()) {
				try {
					found = viewer.recipesFor(item);
				} catch (LinkageError | RuntimeException e) {
					PocketList.LOGGER.warn("Pocket List: a recipe viewer couldn't look up recipes", e);
				}
				if (!found.isEmpty()) {
					break;
				}
			}
		}
		if (found.isEmpty()) {
			found = fromRecipeBook(mc, item);
		}
		return tidy(found);
	}

	/** Every recipe the integrated server has loaded: all of them, unlocked or not. */
	private static List<FoundRecipe> fromWorld(Minecraft mc, Item item) {
		IntegratedServer server = mc.getSingleplayerServer();
		if (server == null) {
			return List.of();
		}
		ContextMap context = SlotDisplayContext.fromLevel(mc.level);
		try {
			// Read straight from here: the server swaps in a whole new set of recipes on /reload rather
			// than changing them in place, so this never sees half a set. Waiting for the server's own
			// thread instead could freeze the game.
			List<FoundRecipe> out = new ArrayList<>();
			for (RecipeHolder<?> holder : List.copyOf(server.getRecipeManager().getRecipes())) {
				for (RecipeDisplay display : holder.value().display()) {
					FoundRecipe recipe = fromDisplay(display, item, context);
					if (recipe != null) {
						out.add(recipe);
					}
				}
			}
			return out;
		} catch (RuntimeException e) {
			PocketList.LOGGER.warn("Pocket List: couldn't read the world's recipes", e);
			return List.of();
		}
	}

	private static List<FoundRecipe> fromRecipeBook(Minecraft mc, Item item) {
		if (mc.player == null) {
			return List.of();
		}
		ContextMap context = SlotDisplayContext.fromLevel(mc.level);
		List<FoundRecipe> out = new ArrayList<>();
		for (RecipeCollection collection : mc.player.getRecipeBook().getCollections()) {
			for (RecipeDisplayEntry entry : collection.getRecipes()) {
				FoundRecipe recipe = fromDisplay(entry.display(), item, context);
				if (recipe != null) {
					out.add(recipe);
				}
			}
		}
		return out;
	}

	/** A vanilla recipe display, if it makes the item. */
	static FoundRecipe fromDisplay(RecipeDisplay display, Item item, ContextMap context) {
		int makes = 0;
		for (ItemStack result : display.result().resolveForStacks(context)) {
			if (result.is(item)) {
				makes = result.getCount();
				break;
			}
		}
		if (makes <= 0) {
			return null;
		}
		List<Entry> ingredients = Pins.ingredients(display, context);
		if (ingredients.isEmpty()) {
			return null;
		}
		ItemStack station = display.craftingStation().resolveForFirstStack(context);
		Component where = station.isEmpty() ? Component.translatable("pocket_list.recipe.anywhere") : station.getHoverName();
		return new FoundRecipe(ingredients, makes, station, where);
	}

	/** Adds up repeated ingredients ("3 planks", not three lines of one). */
	public static List<Entry> merge(List<Entry> ingredients) {
		Map<String, Entry> merged = new LinkedHashMap<>();
		for (Entry entry : ingredients) {
			merged.merge(entry.key(), entry.copyWithNeed(entry.need), (a, b) -> {
				a.need += b.need;
				return a;
			});
		}
		return new ArrayList<>(merged.values());
	}

	/**
	 * Recipes with the same ingredients become one ("Furnace (or Blast Furnace)"), shown under
	 * the station a new player is most likely to have. Crafting table and furnace recipes come
	 * first, and unpacking a storage block (one iron block into nine ingots) last, since it's
	 * rarely how you'd gather something.
	 */
	private static List<FoundRecipe> tidy(List<FoundRecipe> recipes) {
		Map<String, List<FoundRecipe>> same = new LinkedHashMap<>();
		for (FoundRecipe recipe : recipes) {
			same.computeIfAbsent(recipe.key(), k -> new ArrayList<>()).add(recipe);
		}
		List<FoundRecipe> out = new ArrayList<>();
		for (List<FoundRecipe> group : same.values()) {
			group.sort(Comparator.comparingInt(Recipes::stationRank));
			FoundRecipe main = group.getFirst();
			List<String> others = group.stream().skip(1).map(r -> r.where().getString())
					.filter(w -> !w.equals(main.where().getString())).distinct().toList();
			Component where = others.isEmpty() ? main.where()
					: Component.translatable("pocket_list.recipe.or", main.where(), String.join(", ", others));
			out.add(new FoundRecipe(main.ingredients(), main.makes(), main.station(), where));
		}
		out.sort(Comparator.comparing(Recipes::unpacksBlock).thenComparingInt(Recipes::stationRank)
				.thenComparingInt(r -> r.ingredients().size()));
		return out;
	}

	/** Everyday stations first: a crafting table (or the 2x2 grid), then a furnace. */
	private static int stationRank(FoundRecipe recipe) {
		ItemStack station = recipe.station();
		if (station.isEmpty() || station.is(Items.CRAFTING_TABLE)) {
			return 0;
		}
		return station.is(Items.FURNACE) ? 1 : 2;
	}

	private static boolean unpacksBlock(FoundRecipe recipe) {
		return recipe.makes() == 9 && recipe.ingredients().size() == 1 && recipe.ingredients().getFirst().need == 1;
	}
}
