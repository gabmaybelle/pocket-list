package com.gabbymaybelle.pocketlist.compat;

import com.gabbymaybelle.pocketlist.Entry;
import com.gabbymaybelle.pocketlist.FoundRecipe;
import com.gabbymaybelle.pocketlist.Recipes;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** JEI: pin from its item list, bookmarks and recipe screens, and use its recipes. */
final class JeiViewer implements RecipeViewer {
	/** Most recipes JEI is asked about, per category, so a huge modpack can't stall a click. */
	private static final int MAX_PER_CATEGORY = 64;

	/** Set by {@link JeiPlugin} once JEI is ready (after joining a world). */
	static IJeiRuntime runtime;

	@Override
	public ItemStack hovered(Screen screen, double mouseX, double mouseY) {
		IJeiRuntime jei = runtime;
		if (jei == null) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = jei.getIngredientListOverlay().getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
		if (stack != null && !stack.isEmpty()) {
			return stack;
		}
		stack = jei.getBookmarkOverlay().getItemStackUnderMouse();
		if (stack != null && !stack.isEmpty()) {
			return stack;
		}
		return jei.getRecipesGui().getIngredientUnderMouse(VanillaTypes.ITEM_STACK).orElse(ItemStack.EMPTY);
	}

	@Override
	public boolean typing() {
		IJeiRuntime jei = runtime;
		return jei != null && jei.getIngredientListOverlay().hasKeyboardFocus();
	}

	@Override
	public List<FoundRecipe> recipesFor(Item item) {
		IJeiRuntime jei = runtime;
		if (jei == null) {
			return List.of();
		}
		IFocusFactory focuses = jei.getJeiHelpers().getFocusFactory();
		IFocus<ItemStack> focus = focuses.createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, new ItemStack(item));
		IFocusGroup group = focuses.createFocusGroup(List.of(focus));
		IRecipeManager recipes = jei.getRecipeManager();
		List<FoundRecipe> out = new ArrayList<>();
		recipes.createRecipeCategoryLookup().limitFocus(List.of(focus)).get()
				.forEach(category -> addFrom(recipes, category, focus, group, item, out));
		return out;
	}

	private static <T> void addFrom(IRecipeManager recipes, IRecipeCategory<T> category, IFocus<ItemStack> focus, IFocusGroup group,
			Item item, List<FoundRecipe> out) {
		ItemStack station = recipes.createRecipeCatalystLookup(category.getRecipeType()).getItemStack().findFirst().orElse(ItemStack.EMPTY);
		recipes.createRecipeLookup(category.getRecipeType()).limitFocus(List.of(focus)).get().limit(MAX_PER_CATEGORY)
				.forEach(recipe -> recipes.createRecipeLayoutDrawable(category, recipe, group).ifPresent(layout -> {
					int makes = layout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.OUTPUT).stream()
							.flatMap(IRecipeSlotView::getItemStacks).filter(s -> s.is(item)).mapToInt(ItemStack::getCount).findFirst().orElse(0);
					if (makes <= 0) {
						return;
					}
					List<Entry> ingredients = new ArrayList<>();
					for (IRecipeSlotView slot : layout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.INPUT)) {
						Entry entry = toEntry(slot);
						if (entry != null) {
							ingredients.add(entry);
						}
					}
					if (!ingredients.isEmpty()) {
						out.add(new FoundRecipe(Recipes.merge(ingredients), makes, station, category.getTitle()));
					}
				}));
	}

	/** A slot's item tag ("any planks"): newer JEI 19 builds can say; older ones only list the items. */
	private static final Method GET_TAG_KEY = findTagKey();

	private static Method findTagKey() {
		try {
			return IRecipeSlotView.class.getMethod("getTagKey");
		} catch (NoSuchMethodException e) {
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private static Optional<TagKey<?>> tagKey(IRecipeSlotView slot) {
		if (GET_TAG_KEY != null) {
			try {
				return (Optional<TagKey<?>>) GET_TAG_KEY.invoke(slot);
			} catch (ReflectiveOperationException | RuntimeException e) {
				// Shown as its items instead.
			}
		}
		return Optional.empty();
	}

	@SuppressWarnings("unchecked")
	private static Entry toEntry(IRecipeSlotView slot) {
		List<ItemStack> stacks = slot.getItemStacks().filter(s -> !s.isEmpty()).toList();
		if (stacks.isEmpty()) {
			return null;
		}
		int count = stacks.get(0).getCount();
		Optional<TagKey<?>> tag = tagKey(slot);
		if (tag.isPresent() && tag.get().registry().equals(Registries.ITEM)) {
			return Entry.ofTag((TagKey<Item>) tag.get(), count);
		}
		return Entry.ofItems(stacks.stream().map(ItemStack::getItem).distinct().toList(), count);
	}
}
