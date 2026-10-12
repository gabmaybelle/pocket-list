package com.gabbymaybelle.pocketlist;

import com.gabbymaybelle.pocketlist.mixin.AbstractContainerScreenAccessor;
import com.gabbymaybelle.pocketlist.mixin.AbstractRecipeBookScreenAccessor;
import com.gabbymaybelle.pocketlist.mixin.RecipeBookComponentAccessor;
import com.gabbymaybelle.pocketlist.mixin.RecipeBookPageAccessor;
import com.gabbymaybelle.pocketlist.compat.RecipeViewer;
import com.gabbymaybelle.pocketlist.compat.Viewers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;

/**
 * The pin key, pressed over something in an inventory screen. Over a recipe in the recipe book
 * it adds that recipe's ingredients; over an item it adds the item itself. Hold Shift to add
 * enough for a whole stack.
 */
public final class Pins {
	private Pins() {
	}

	/** True if something was pinned. */
	static boolean pinHovered(Minecraft mc, Screen screen, boolean shift) {
		if (mc.level == null) {
			return false;
		}
		RecipeButton recipe = hoveredRecipe(screen);
		if (recipe != null && pinRecipe(mc, recipe, shift)) {
			done(mc);
			return true;
		}
		ItemStack stack = hoveredItem(mc, screen);
		if (!stack.isEmpty()) {
			Lists.add(Entry.ofItem(stack.getItem(), shift ? stack.getMaxStackSize() : 1));
			done(mc);
			return true;
		}
		return false;
	}

	/** True while the player is typing in a recipe viewer's search box. */
	static boolean typingInViewer() {
		for (RecipeViewer viewer : Viewers.all()) {
			try {
				if (viewer.typing()) {
					return true;
				}
			} catch (LinkageError | RuntimeException e) {
				// A viewer that changed its API: treat as not typing.
			}
		}
		return false;
	}

	/** The item under the mouse: in an inventory slot, or in a recipe viewer's lists. */
	static ItemStack hoveredItem(Minecraft mc, Screen screen) {
		if (screen instanceof AbstractContainerScreen<?> containerScreen) {
			Slot slot = ((AbstractContainerScreenAccessor) containerScreen).pocketList$hoveredSlot();
			if (slot != null && slot.hasItem()) {
				return slot.getItem();
			}
		}
		double x = mc.mouseHandler.getScaledXPos(mc.getWindow());
		double y = mc.mouseHandler.getScaledYPos(mc.getWindow());
		for (RecipeViewer viewer : Viewers.all()) {
			try {
				ItemStack stack = viewer.hovered(screen, x, y);
				if (stack != null && !stack.isEmpty()) {
					return stack;
				}
			} catch (LinkageError | RuntimeException e) {
				PocketList.LOGGER.debug("A recipe viewer couldn't say what's under the mouse", e);
			}
		}
		return ItemStack.EMPTY;
	}

	private static void done(Minecraft mc) {
		Lists.save();
		Counter.update(mc);
		mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
	}

	private static RecipeButton hoveredRecipe(Screen screen) {
		if (!(screen instanceof AbstractRecipeBookScreen<?>)) {
			return null;
		}
		RecipeBookComponent<?> book = ((AbstractRecipeBookScreenAccessor) screen).pocketList$recipeBook();
		if (book == null || !book.isVisible()) {
			return null;
		}
		return ((RecipeBookPageAccessor) ((RecipeBookComponentAccessor) book).pocketList$page()).pocketList$hoveredButton();
	}

	private static boolean pinRecipe(Minecraft mc, RecipeButton button, boolean shift) {
		RecipeDisplayId id = button.getCurrentRecipe();
		Optional<RecipeDisplayEntry> found = button.getCollection().getRecipes().stream().filter(r -> r.id().equals(id)).findFirst();
		if (found.isEmpty()) {
			return false;
		}
		ContextMap context = SlotDisplayContext.fromLevel(mc.level);
		List<Entry> ingredients = ingredients(found.get().display(), context);
		if (ingredients.isEmpty()) {
			return false;
		}
		int crafts = 1;
		if (shift) {
			// Enough crafts for a full stack of whatever the recipe makes.
			List<ItemStack> results = found.get().resultItems(context);
			if (!results.isEmpty()) {
				ItemStack result = results.getFirst();
				crafts = Math.max(1, (result.getMaxStackSize() + result.getCount() - 1) / Math.max(1, result.getCount()));
			}
		}
		for (Entry entry : ingredients) {
			entry.need *= crafts;
			Lists.add(entry);
		}
		return true;
	}

	/** A recipe's ingredients for one craft, with repeats added up ("3 planks", not three lines of one). */
	static List<Entry> ingredients(RecipeDisplay display, ContextMap context) {
		List<SlotDisplay> slots = switch (display) {
			case ShapedCraftingRecipeDisplay shaped -> shaped.ingredients();
			case ShapelessCraftingRecipeDisplay shapeless -> shapeless.ingredients();
			case FurnaceRecipeDisplay furnace -> List.of(furnace.ingredient());
			case StonecutterRecipeDisplay stonecutter -> List.of(stonecutter.input());
			case SmithingRecipeDisplay smithing -> List.of(smithing.template(), smithing.base(), smithing.addition());
			default -> List.of();
		};
		Map<String, Entry> merged = new LinkedHashMap<>();
		for (SlotDisplay slot : slots) {
			Entry entry = toEntry(slot, context);
			if (entry != null) {
				merged.merge(entry.key(), entry, (a, b) -> {
					a.need += b.need;
					return a;
				});
			}
		}
		return new ArrayList<>(merged.values());
	}

	private static Entry toEntry(SlotDisplay slot, ContextMap context) {
		if (slot instanceof SlotDisplay.TagSlotDisplay tagged) {
			Optional<TagKey<Item>> key = tagged.tag().unwrapKey();
			if (key.isPresent()) {
				return Entry.ofTag(key.get(), 1);
			}
		}
		if (slot instanceof SlotDisplay.ItemSlotDisplay single) {
			return Entry.ofItem(single.item().value(), 1);
		}
		// Anything else (a few choices, an item with a leftover such as a bucket): whatever it shows.
		List<Item> items = new ArrayList<>();
		for (ItemStack stack : slot.resolveForStacks(context)) {
			if (!stack.isEmpty() && !items.contains(stack.getItem())) {
				items.add(stack.getItem());
			}
		}
		return items.isEmpty() ? null : Entry.ofItems(items, 1);
	}
}
