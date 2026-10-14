package com.gabbymaybelle.pocketlist.compat;

import com.gabbymaybelle.pocketlist.PocketList;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The recipe viewers that are installed. Each one's code is only loaded when that mod is
 * present, so Pocket List never needs JEI or REI.
 */
public final class Viewers {
	private static final List<RecipeViewer> ALL = new ArrayList<>();

	private Viewers() {
	}

	public static void init() {
		FabricLoader loader = FabricLoader.getInstance();
		try {
			if (loader.isModLoaded("jei")) {
				ALL.add(new JeiViewer());
			}
			if (loader.isModLoaded("roughlyenoughitems")) {
				ALL.add(new ReiViewer());
			}
		} catch (LinkageError e) {
			// A viewer version whose API changed: carry on without it.
			PocketList.LOGGER.warn("Pocket List: couldn't connect to a recipe viewer; pinning from it won't work", e);
		}
	}

	public static List<RecipeViewer> all() {
		return ALL;
	}
}
