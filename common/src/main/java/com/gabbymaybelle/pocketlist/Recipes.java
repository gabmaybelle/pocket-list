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
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;

/**
 * Finds the ways to make an item, from the best source there is:
 * <ol>
 * <li>the recipes the world or server sent your game when you joined: on 1.20.1 that's every
 * recipe, unlocked or not, in single player and on servers alike;</li>
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

	/** Every recipe the world or server sent this game: all of them, unlocked or not. */
	private static List<FoundRecipe> fromWorld(Minecraft mc, Item item) {
		RegistryAccess registries = mc.level.registryAccess();
		try {
			List<FoundRecipe> out = new ArrayList<>();
			for (Recipe<?> each : List.copyOf(mc.level.getRecipeManager().getRecipes())) {
				FoundRecipe recipe = fromRecipe(each, item, registries);
				if (recipe != null) {
					out.add(recipe);
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
		RegistryAccess registries = mc.level.registryAccess();
		List<FoundRecipe> out = new ArrayList<>();
		for (RecipeCollection collection : mc.player.getRecipeBook().getCollections()) {
			for (Recipe<?> each : collection.getRecipes()) {
				FoundRecipe recipe = fromRecipe(each, item, registries);
				if (recipe != null) {
					out.add(recipe);
				}
			}
		}
		return out;
	}

	/** A vanilla recipe, if it makes the item. */
	static FoundRecipe fromRecipe(Recipe<?> recipe, Item item, RegistryAccess registries) {
		if (recipe.isSpecial()) {
			// Dyeing armour, cloning maps and the like: no fixed ingredients to gather.
			return null;
		}
		ItemStack result = recipe.getResultItem(registries);
		if (!result.is(item) || result.getCount() <= 0) {
			return null;
		}
		List<Entry> ingredients = Pins.ingredients(recipe);
		if (ingredients.isEmpty()) {
			return null;
		}
		ItemStack station = recipe.getToastSymbol();
		Component where = station.isEmpty() ? Component.translatable("pocket_list.recipe.anywhere") : station.getHoverName();
		return new FoundRecipe(ingredients, result.getCount(), station, where);
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
			FoundRecipe main = group.get(0);
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
		return recipe.makes() == 9 && recipe.ingredients().size() == 1 && recipe.ingredients().get(0).need == 1;
	}
}
