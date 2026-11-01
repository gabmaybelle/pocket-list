package com.gabbymaybelle.pocketlist.paper;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * The server owner's settings, from {@code plugins/PocketList/config.yml}, read when the server
 * starts. Out-of-range numbers are brought into range rather than refused.
 */
record Settings(boolean sidebarForNewPlayers, int defaultLines, boolean chestMemory, int chestRadius) {
	static final int MIN_RADIUS = 8, MAX_RADIUS = 64;

	static Settings from(FileConfiguration config) {
		return new Settings(
				config.getBoolean("sidebarForNewPlayers", true),
				Math.max(PlayerData.MIN_LINES, Math.min(PlayerData.MAX_LINES, config.getInt("defaultLines", PlayerData.DEFAULT_LINES))),
				config.getBoolean("chestMemory", true),
				Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, config.getInt("chestRadius", 32))));
	}
}
