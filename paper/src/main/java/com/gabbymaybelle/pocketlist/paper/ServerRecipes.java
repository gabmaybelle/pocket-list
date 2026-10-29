package com.gabbymaybelle.pocketlist.paper;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.inventory.TransmuteRecipe;
import org.jspecify.annotations.Nullable;

/**
 * The server's own recipes for an item (data packs included), read the way the mod's
 * {@code Pins.ingredients} reads vanilla's: shaped and shapeless crafting, the furnace family,
 * the stonecutter and smithing. A choice with several items becomes an "or similar" ingredient.
 */
final class ServerRecipes {
	private ServerRecipes() {
	}

	static List<FoundRecipe> lookup(Material item) {
		List<FoundRecipe> found = new ArrayList<>();
		for (Recipe recipe : Bukkit.getRecipesFor(new ItemStack(item))) {
			FoundRecipe f = read(recipe);
			if (f != null) found.add(f);
		}
		return FoundRecipe.tidy(found);
	}

	static @Nullable FoundRecipe read(Recipe recipe) {
		ItemStack result = recipe.getResult();
		int makes = result.getAmount();
		LinkedHashMap<List<String>, Integer> ingredients = new LinkedHashMap<>();
		String station;
		int order;
		switch (recipe) {
			case ShapedRecipe shaped -> {
				Map<Character, RecipeChoice> choices = shaped.getChoiceMap();
				for (String row : shaped.getShape()) {
					for (char c : row.toCharArray()) put(ingredients, choices.get(c), 1);
				}
				station = "Crafting Table";
				order = FoundRecipe.CRAFTING;
			}
			case ShapelessRecipe shapeless -> {
				for (RecipeChoice choice : shapeless.getChoiceList()) put(ingredients, choice, 1);
				station = "Crafting Table";
				order = FoundRecipe.CRAFTING;
			}
			case TransmuteRecipe transmute -> {
				put(ingredients, transmute.getInput(), 1);
				put(ingredients, transmute.getMaterial(), 1);
				station = "Crafting Table";
				order = FoundRecipe.CRAFTING;
			}
			case CookingRecipe<?> cooking -> {
				put(ingredients, cooking.getInputChoice(), 1);
				station = cooking instanceof BlastingRecipe ? "Blast Furnace" : cooking instanceof SmokingRecipe ? "Smoker" : cooking instanceof CampfireRecipe ? "Campfire" : "Furnace";
				order = FoundRecipe.FURNACE;
			}
			case StonecuttingRecipe cut -> {
				put(ingredients, cut.getInputChoice(), 1);
				station = "Stonecutter";
				order = FoundRecipe.OTHER;
			}
			case SmithingTransformRecipe smith -> {
				put(ingredients, smith.getTemplate(), 1);
				put(ingredients, smith.getBase(), 1);
				put(ingredients, smith.getAddition(), 1);
				station = "Smithing Table";
				order = FoundRecipe.OTHER;
			}
			default -> {
				return null;
			}
		}
		if (ingredients.isEmpty() || makes <= 0) return null;
		return new FoundRecipe(station, order, ingredients, makes);
	}

	private static void put(LinkedHashMap<List<String>, Integer> ingredients, @Nullable RecipeChoice choice, int count) {
		List<String> items = items(choice);
		if (items.isEmpty()) return;
		ingredients.merge(items, count, Integer::sum);
	}

	/** The item keys a choice accepts, in a fixed order so equal choices compare equal. */
	static List<String> items(@Nullable RecipeChoice choice) {
		List<String> keys = new ArrayList<>();
		switch (choice) {
			case null -> {
			}
			case RecipeChoice.MaterialChoice m -> {
				for (Material mat : m.getChoices()) keys.add(mat.getKey().toString());
			}
			case RecipeChoice.ExactChoice e -> {
				for (ItemStack s : e.getChoices()) keys.add(s.getType().getKey().toString());
			}
			case RecipeChoice.ItemTypeChoice t -> {
				for (ItemType type : t.itemTypes().resolve(RegistryAccess.registryAccess().getRegistry(RegistryKey.ITEM))) keys.add(type.getKey().toString());
			}
			default -> {
				try {
					ItemStack s = choice.getItemStack();
					if (s != null && !s.isEmpty()) keys.add(s.getType().getKey().toString());
				} catch (RuntimeException ignored) {
					// A predicate choice with no example item: nothing to list.
				}
			}
		}
		List<String> distinct = new ArrayList<>(new java.util.LinkedHashSet<>(keys));
		if (distinct.size() > 1) distinct.sort(null);
		return distinct;
	}
}
