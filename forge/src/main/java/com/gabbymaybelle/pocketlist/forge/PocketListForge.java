package com.gabbymaybelle.pocketlist.forge;

import com.gabbymaybelle.pocketlist.PocketList;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge's way in. A dedicated server loads this class too, so it must not touch anything that only
 * exists on a client (the game's screens, GUI classes): everything that does is in
 * {@link PocketListForgeClient}, which is only loaded on a client. On a server Pocket List does nothing.
 */
@Mod(PocketList.MOD_ID)
public class PocketListForge {
	public PocketListForge() {
		DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> PocketListForgeClient::init);
	}
}
