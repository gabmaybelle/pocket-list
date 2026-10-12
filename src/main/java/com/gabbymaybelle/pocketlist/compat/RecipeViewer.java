package com.gabbymaybelle.pocketlist.compat;

import com.gabbymaybelle.pocketlist.FoundRecipe;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** What Pocket List needs from a recipe viewer such as JEI or REI. */
public interface RecipeViewer {
	/** The item under the mouse in the viewer's own lists or recipe screens, or empty. */
	ItemStack hovered(Screen screen, double mouseX, double mouseY);

	/** True while the player is typing in the viewer's search box, so the pin key types instead. */
	boolean typing();

	/** Every way the viewer knows to make this item. */
	List<FoundRecipe> recipesFor(Item item);
}
