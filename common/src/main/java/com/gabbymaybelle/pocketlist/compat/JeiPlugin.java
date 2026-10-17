package com.gabbymaybelle.pocketlist.compat;

import com.gabbymaybelle.pocketlist.PocketList;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

/** JEI hands its runtime over here. Only JEI loads this class. */
@mezz.jei.api.JeiPlugin
public class JeiPlugin implements IModPlugin {
	@Override
	public ResourceLocation getPluginUid() {
		return new ResourceLocation(PocketList.MOD_ID, "jei");
	}

	@Override
	public void onRuntimeAvailable(IJeiRuntime runtime) {
		JeiViewer.runtime = runtime;
	}

	@Override
	public void onRuntimeUnavailable() {
		JeiViewer.runtime = null;
	}
}
