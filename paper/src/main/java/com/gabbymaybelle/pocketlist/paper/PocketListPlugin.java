package com.gabbymaybelle.pocketlist.paper;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Pocket List as a Paper plugin: a gathering list at the side of the screen that ticks itself off
 * as you collect. Lists live on the player ({@link PlayerData}), counts move when an inventory does
 * ({@link Counter}), the list is a scoreboard sidebar ({@link Sidebar}), and every key the mod has
 * is a {@code /pocket} command ({@link PocketCommand}) with the list screen as clickable chat
 * ({@link ChatList}). Break down reads the server's own recipes ({@link ServerRecipes}). Containers a
 * player opened count while they're near ({@link ChestMemory}), lists can be pasted from chat or a
 * book ({@link Paste}), and the server owner's settings are {@link Settings}.
 */
public final class PocketListPlugin extends JavaPlugin {
	private PlayerData data;
	private Sidebar sidebar;

	@Override
	public void onEnable() {
		saveDefaultConfig();
		Settings settings = Settings.from(getConfig());
		data = new PlayerData(this, settings);
		sidebar = new Sidebar();
		Counter counter = new Counter(this, data, sidebar);
		Bukkit.getPluginManager().registerEvents(counter, this);
		if (settings.chestMemory()) {
			ChestMemory chests = new ChestMemory(data, settings, counter);
			Bukkit.getPluginManager().registerEvents(chests, this);
			counter.useChests(chests);
		}
		getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
				event.registrar().register("pocket", "The gathering list: /pocket help", List.of("pocketlist"), new PocketCommand(data, counter, this, settings)));
		for (Player p : Bukkit.getOnlinePlayers()) counter.mark(p);
		getLogger().info("Pocket List: ready, /pocket for the list" + (settings.chestMemory() ? ", chest memory within " + settings.chestRadius() + " blocks" : ", chest memory off"));
	}

	@Override
	public void onDisable() {
		for (Player p : Bukkit.getOnlinePlayers()) {
			if (sidebar != null) sidebar.hide(p);
			if (data != null) data.forget(p);
		}
	}
}
