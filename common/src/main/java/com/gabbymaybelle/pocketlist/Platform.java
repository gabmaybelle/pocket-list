package com.gabbymaybelle.pocketlist;

import java.nio.file.Path;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The few things Pocket List asks of the mod loader. The Fabric and NeoForge jars each set this
 * first thing, before any other Pocket List class is used.
 */
public final class Platform {
	/** What each loader answers. */
	public interface Hooks {
		/** The game's config folder. */
		Path configDir();

		/** Whether a mod with this id is installed. */
		boolean isModLoaded(String id);

		/**
		 * What a block holds, as this game sees it, through the loader's item storage: one stack
		 * per slot or view (empty ones are fine), or null when the block has no item storage. Only
		 * ever asked about a block the player has just used.
		 */
		List<ItemStack> blockItems(Level level, BlockPos pos);
	}

	private static Hooks hooks;

	private Platform() {
	}

	public static void set(Hooks loaderHooks) {
		hooks = loaderHooks;
	}

	static Path configDir() {
		return hooks.configDir();
	}

	public static boolean isModLoaded(String id) {
		return hooks.isModLoaded(id);
	}

	static List<ItemStack> blockItems(Level level, BlockPos pos) {
		return hooks.blockItems(level, pos);
	}
}
