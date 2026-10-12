package com.gabbymaybelle.pocketlist;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu's Config button opens the settings. Only loaded when Mod Menu is installed. */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return PocketListSettingsScreen::new;
	}
}
