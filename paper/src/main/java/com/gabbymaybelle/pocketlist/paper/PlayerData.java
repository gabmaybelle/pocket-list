package com.gabbymaybelle.pocketlist.paper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Each player's lists and HUD settings, kept in the player's own persistent data (so they live on
 * that server with the player, and nothing is written to a plugin file). Read once per login and
 * written after every change.
 */
final class PlayerData {
	static final int DEFAULT_LINES = 8, MIN_LINES = 1, MAX_LINES = 15;

	private final NamespacedKey listsKey, hudKey, linesKey, welcomedKey;
	private final Map<UUID, Lists> cache = new HashMap<>();

	PlayerData(Plugin plugin) {
		listsKey = new NamespacedKey(plugin, "lists");
		hudKey = new NamespacedKey(plugin, "hud");
		linesKey = new NamespacedKey(plugin, "hud_lines");
		welcomedKey = new NamespacedKey(plugin, "welcomed");
	}

	Lists lists(Player player) {
		return cache.computeIfAbsent(player.getUniqueId(), id -> Lists.fromJson(player.getPersistentDataContainer().get(listsKey, PersistentDataType.STRING)));
	}

	void save(Player player) {
		Lists l = cache.get(player.getUniqueId());
		if (l != null) player.getPersistentDataContainer().set(listsKey, PersistentDataType.STRING, l.toJson());
	}

	void forget(Player player) {
		save(player);
		cache.remove(player.getUniqueId());
	}

	boolean hud(Player player) {
		Byte b = player.getPersistentDataContainer().get(hudKey, PersistentDataType.BYTE);
		return b == null || b != 0;
	}

	void hud(Player player, boolean on) {
		player.getPersistentDataContainer().set(hudKey, PersistentDataType.BYTE, (byte) (on ? 1 : 0));
	}

	int lines(Player player) {
		Integer n = player.getPersistentDataContainer().get(linesKey, PersistentDataType.INTEGER);
		return n == null ? DEFAULT_LINES : Math.max(MIN_LINES, Math.min(MAX_LINES, n));
	}

	void lines(Player player, int n) {
		player.getPersistentDataContainer().set(linesKey, PersistentDataType.INTEGER, Math.max(MIN_LINES, Math.min(MAX_LINES, n)));
	}

	/** True the first time only: the welcome line is shown once per player. */
	boolean firstWelcome(Player player) {
		PersistentDataContainer pdc = player.getPersistentDataContainer();
		if (pdc.has(welcomedKey, PersistentDataType.BYTE)) return false;
		pdc.set(welcomedKey, PersistentDataType.BYTE, (byte) 1);
		return true;
	}
}
