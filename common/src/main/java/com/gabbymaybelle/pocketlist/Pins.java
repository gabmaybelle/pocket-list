package com.gabbymaybelle.pocketlist;

import com.gabbymaybelle.pocketlist.mixin.AbstractContainerScreenAccessor;
import com.gabbymaybelle.pocketlist.mixin.RecipeBookComponentAccessor;
import com.gabbymaybelle.pocketlist.mixin.RecipeBookPageAccessor;
import com.gabbymaybelle.pocketlist.mixin.SmithingTransformRecipeAccessor;
import com.gabbymaybelle.pocketlist.compat.RecipeViewer;
import com.gabbymaybelle.pocketlist.compat.Viewers;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;

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
		double x = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
		double y = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
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
		if (!(screen instanceof RecipeUpdateListener listener)) {
			return null;
		}
		RecipeBookComponent book = listener.getRecipeBookComponent();
		if (book == null || !book.isVisible()) {
			return null;
		}
		return ((RecipeBookPageAccessor) ((RecipeBookComponentAccessor) book).pocketList$page()).pocketList$hoveredButton();
	}

	private static boolean pinRecipe(Minecraft mc, RecipeButton button, boolean shift) {
		RecipeHolder<?> holder = button.getRecipe();
		if (holder == null) {
			return false;
		}
		List<Entry> ingredients = ingredients(holder.value());
		if (ingredients.isEmpty()) {
			return false;
		}
		int crafts = 1;
		if (shift) {
			// Enough crafts for a full stack of whatever the recipe makes.
			ItemStack result = holder.value().getResultItem(mc.level.registryAccess());
			if (!result.isEmpty()) {
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
	static List<Entry> ingredients(Recipe<?> recipe) {
		List<Ingredient> slots;
		if (recipe instanceof SmithingTransformRecipe smithing) {
			// Smithing recipes don't list their inputs as ingredients on 1.21.1.
			SmithingTransformRecipeAccessor inputs = (SmithingTransformRecipeAccessor) smithing;
			slots = List.of(inputs.pocketList$template(), inputs.pocketList$base(), inputs.pocketList$addition());
		} else {
			slots = recipe.getIngredients();
		}
		List<Entry> entries = new ArrayList<>();
		for (Ingredient slot : slots) {
			Entry entry = toEntry(slot);
			if (entry != null) {
				entries.add(entry);
			}
		}
		return Recipes.merge(entries);
	}

	private static Entry toEntry(Ingredient slot) {
		if (slot == null || slot.isEmpty()) {
			return null;
		}
		TagKey<Item> tag = tagOf(slot);
		if (tag != null) {
			return Entry.ofTag(tag, 1);
		}
		// Anything else (one item, a few choices): whatever it shows.
		List<Item> items = new ArrayList<>();
		for (ItemStack stack : slot.getItems()) {
			if (!stack.isEmpty() && !items.contains(stack.getItem())) {
				items.add(stack.getItem());
			}
		}
		return items.isEmpty() ? null : Entry.ofItems(items, 1);
	}

	/**
	 * The item tag an ingredient stands for ("any planks"), if it's just one tag. The game keeps that
	 * to itself on 1.21.1, but writes it out the way a recipe file says it: {"tag": "minecraft:planks"}.
	 */
	private static TagKey<Item> tagOf(Ingredient ingredient) {
		try {
			JsonElement json = Ingredient.CODEC_NONEMPTY.encodeStart(JsonOps.INSTANCE, ingredient).result().orElse(null);
			if (json instanceof JsonObject object && object.size() == 1 && object.has("tag")) {
				ResourceLocation id = ResourceLocation.tryParse(object.get("tag").getAsString());
				return id == null ? null : TagKey.create(Registries.ITEM, id);
			}
		} catch (RuntimeException e) {
			// A mod's own kind of ingredient: shown as its items instead.
		}
		return null;
	}
}
