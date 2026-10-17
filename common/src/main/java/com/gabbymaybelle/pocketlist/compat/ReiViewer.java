package com.gabbymaybelle.pocketlist.compat;

import com.gabbymaybelle.pocketlist.Entry;
import com.gabbymaybelle.pocketlist.FoundRecipe;
import com.gabbymaybelle.pocketlist.Recipes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import me.shedaniel.math.Point;
import me.shedaniel.rei.api.client.REIRuntime;
import me.shedaniel.rei.api.client.gui.widgets.TextField;
import me.shedaniel.rei.api.client.overlay.ScreenOverlay;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.client.view.ViewSearchBuilder;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** REI: pin from its item list, favourites and recipe screens, and use its recipes. */
final class ReiViewer implements RecipeViewer {
	/** Most recipes REI is asked about, so a huge modpack can't stall a click. */
	private static final int MAX_RECIPES = 128;

	@Override
	public ItemStack hovered(Screen screen, double mouseX, double mouseY) {
		// REI's item list and favourites first, then whatever REI knows is under the mouse elsewhere
		// (its recipe screens, for one).
		Optional<ScreenOverlay> overlay = REIRuntime.getInstance().getOverlay();
		if (overlay.isPresent()) {
			ItemStack stack = item(overlay.get().getEntryList().getFocusedStack());
			if (stack.isEmpty()) {
				stack = overlay.get().getFavoritesList().map(list -> item(list.getFocusedStack())).orElse(ItemStack.EMPTY);
			}
			if (!stack.isEmpty()) {
				return stack;
			}
		}
		return item(ScreenRegistry.getInstance().getFocusedStack(screen, new Point(mouseX, mouseY)));
	}

	private static ItemStack item(EntryStack<?> stack) {
		return stack != null && !stack.isEmpty() && stack.getType() == VanillaEntryTypes.ITEM ? stack.<ItemStack>castValue() : ItemStack.EMPTY;
	}

	@Override
	public boolean typing() {
		// REI 12's search field is a game widget underneath; its focus is the game's.
		TextField search = REIRuntime.getInstance().getSearchTextField();
		return search instanceof GuiEventListener widget && widget.isFocused();
	}

	@Override
	public List<FoundRecipe> recipesFor(Item item) {
		List<FoundRecipe> out = new ArrayList<>();
		ViewSearchBuilder.builder().addRecipesFor(EntryStacks.of(new ItemStack(item))).streamDisplays().limit(MAX_RECIPES).forEach(spec -> {
			Display display = spec.provideInternalDisplay();
			int makes = items(display.getOutputEntries()).stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).findFirst().orElse(0);
			if (makes <= 0) {
				return;
			}
			List<Entry> ingredients = new ArrayList<>();
			for (EntryIngredient ingredient : display.getInputEntries()) {
				List<ItemStack> stacks = items(List.of(ingredient));
				if (!stacks.isEmpty()) {
					ingredients.add(Entry.ofItems(stacks.stream().map(ItemStack::getItem).distinct().toList(), stacks.get(0).getCount()));
				}
			}
			if (!ingredients.isEmpty()) {
				out.add(new FoundRecipe(Recipes.merge(ingredients), makes, ItemStack.EMPTY, title(display)));
			}
		});
		return out;
	}

	private static List<ItemStack> items(List<EntryIngredient> ingredients) {
		List<ItemStack> out = new ArrayList<>();
		for (EntryIngredient ingredient : ingredients) {
			for (EntryStack<?> stack : ingredient) {
				if (!stack.isEmpty() && stack.getType() == VanillaEntryTypes.ITEM) {
					out.add(stack.<ItemStack>castValue());
				}
			}
		}
		return out;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static Component title(Display display) {
		try {
			return CategoryRegistry.getInstance().get((CategoryIdentifier) display.getCategoryIdentifier()).getCategory().getTitle();
		} catch (RuntimeException e) {
			return Component.literal(display.getCategoryIdentifier().toString());
		}
	}
}
