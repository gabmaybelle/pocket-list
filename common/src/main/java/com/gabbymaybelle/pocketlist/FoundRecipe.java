package com.gabbymaybelle.pocketlist;

import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * One way to make something: what goes in (for a single craft), how many come out, and
 * where it's made ("Crafting Table", "Furnace"). {@code station} may be empty.
 */
public record FoundRecipe(List<Entry> ingredients, int makes, ItemStack station, Component where) {
	/** Two recipes with the same ingredients and output are the same as far as a list cares. */
	String key() {
		return makes + ":" + ingredients.stream().map(e -> e.key() + "x" + e.need).sorted().collect(Collectors.joining(","));
	}

	/** Copies of the ingredients, so adding them to a list never changes this recipe. */
	List<Entry> ingredientsTimes(int crafts) {
		return ingredients.stream().map(e -> e.copyWithNeed(e.need * crafts)).toList();
	}
}
