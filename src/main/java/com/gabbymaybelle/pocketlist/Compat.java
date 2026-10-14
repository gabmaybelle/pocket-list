package com.gabbymaybelle.pocketlist;

import java.lang.reflect.Method;
import java.util.Optional;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.HolderSet;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.display.SlotDisplay;

/**
 * The few game calls that differ between Minecraft 26.2 and 26.3, so one jar works on both. Each
 * is looked up by name once, so neither version ever calls a method it doesn't have.
 */
final class Compat {
	/** 26.3: whether the focused widget is taking typed text. 26.2 has no such method. */
	private static final Method IS_INPUT_CAPTURED = find(Screen.class, "isInputCaptured");
	/** A recipe's tag ingredient: a TagKey on 26.2, a HolderSet (holding the TagKey) on 26.3. */
	private static final Method TAG = find(SlotDisplay.TagSlotDisplay.class, "tag");

	private Compat() {
	}

	private static Method find(Class<?> owner, String name) {
		try {
			return owner.getMethod(name);
		} catch (NoSuchMethodException e) {
			return null;
		}
	}

	/** Whether the player is typing in this screen (a search box, say), so keys should go to the text. */
	static boolean typing(Screen screen) {
		if (IS_INPUT_CAPTURED != null) {
			try {
				return (boolean) IS_INPUT_CAPTURED.invoke(screen);
			} catch (ReflectiveOperationException e) {
				// Fall through to the 26.2 way.
			}
		}
		// 26.2: follow the focus down to the widget that has it, and check it's a text box.
		GuiEventListener focused = screen.getFocused();
		while (focused instanceof ContainerEventHandler container && !(focused instanceof EditBox) && container.getFocused() != null) {
			focused = container.getFocused();
		}
		return focused instanceof EditBox box && box.isFocused() && box.isActive();
	}

	/** The item tag a recipe ingredient stands for, if it is one. */
	@SuppressWarnings("unchecked")
	static Optional<TagKey<Item>> tagOf(SlotDisplay.TagSlotDisplay display) {
		try {
			Object tag = TAG.invoke(display);
			if (tag instanceof TagKey<?> key) {
				return Optional.of((TagKey<Item>) key);
			}
			if (tag instanceof HolderSet<?> set) {
				return set.unwrapKey().map(key -> (TagKey<Item>) key);
			}
		} catch (ReflectiveOperationException e) {
			PocketList.LOGGER.warn("Couldn't read a recipe's tag", e);
		}
		return Optional.empty();
	}
}
