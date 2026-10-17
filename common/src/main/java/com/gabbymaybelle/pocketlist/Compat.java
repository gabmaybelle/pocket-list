package com.gabbymaybelle.pocketlist;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

/** Small questions about the game's screens that the game has no one call for. */
final class Compat {
	private Compat() {
	}

	/** Whether the player is typing in this screen (a search box, say), so keys should go to the text. */
	static boolean typing(Screen screen) {
		// Follow the focus down to the widget that has it, and check it's a text box.
		GuiEventListener focused = screen.getFocused();
		while (focused instanceof ContainerEventHandler container && !(focused instanceof EditBox) && container.getFocused() != null) {
			focused = container.getFocused();
		}
		return focused instanceof EditBox box && box.isFocused() && box.isActive();
	}
}
