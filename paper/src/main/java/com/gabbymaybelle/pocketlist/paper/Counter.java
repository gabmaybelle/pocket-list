package com.gabbymaybelle.pocketlist.paper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;

/**
 * Counts what a player has, the mod's {@code Counter}: the main inventory and hotbar, the off-hand,
 * the stack on the cursor, the open container's slots (not its result slot), and inside shulker
 * boxes and bundles two deep. Nothing runs on a timer: an inventory change marks the player and one
 * recount runs on the next tick, so a stack moving across ten slots counts once. After a count the
 * sidebar is redrawn and, when the active list has just become complete, the chime plays.
 */
final class Counter implements Listener {
	static final int MAX_DEPTH = 2;
	/** The slot of each container type that holds a result, not an ingredient (the mod skips {@code ResultSlot}s). */
	private static final Map<InventoryType, Integer> RESULT_SLOT = Map.ofEntries(
			Map.entry(InventoryType.WORKBENCH, 0), Map.entry(InventoryType.CRAFTING, 0),
			Map.entry(InventoryType.FURNACE, 2), Map.entry(InventoryType.BLAST_FURNACE, 2), Map.entry(InventoryType.SMOKER, 2),
			Map.entry(InventoryType.ANVIL, 2), Map.entry(InventoryType.SMITHING, 3), Map.entry(InventoryType.GRINDSTONE, 2),
			Map.entry(InventoryType.STONECUTTER, 1), Map.entry(InventoryType.LOOM, 3), Map.entry(InventoryType.CARTOGRAPHY, 2),
			Map.entry(InventoryType.MERCHANT, 2));

	private final Plugin plugin;
	private final PlayerData data;
	private final Sidebar sidebar;
	private final Set<UUID> dirty = new HashSet<>();
	private boolean scheduled;

	Counter(Plugin plugin, PlayerData data, Sidebar sidebar) {
		this.plugin = plugin;
		this.data = data;
		this.sidebar = sidebar;
	}

	/** What the player has of each item, by key. */
	static Map<String, Integer> count(Player player) {
		Map<String, Integer> counts = new HashMap<>();
		for (ItemStack stack : player.getInventory().getStorageContents()) add(counts, stack, 0);
		add(counts, player.getInventory().getItemInOffHand(), 0);
		add(counts, player.getItemOnCursor(), 0);
		InventoryView view = player.getOpenInventory();
		InventoryType type = view.getType();
		if (type != InventoryType.CREATIVE && type != InventoryType.PLAYER) {
			Inventory top = view.getTopInventory();
			Integer result = RESULT_SLOT.get(type);
			for (int slot = 0; slot < top.getSize(); slot++) {
				if (result != null && slot == result) continue;
				add(counts, top.getItem(slot), 0);
			}
		}
		return counts;
	}

	private static void add(Map<String, Integer> counts, ItemStack stack, int depth) {
		if (stack == null || stack.isEmpty()) return;
		counts.merge(stack.getType().getKey().toString(), stack.getAmount(), Integer::sum);
		if (depth >= MAX_DEPTH || !stack.hasItemMeta()) return;
		ItemMeta meta = stack.getItemMeta();
		if (meta instanceof BlockStateMeta bsm && bsm.hasBlockState() && bsm.getBlockState() instanceof ShulkerBox box) {
			for (ItemStack inside : box.getInventory().getContents()) add(counts, inside, depth + 1);
		} else if (meta instanceof BundleMeta bundle) {
			for (ItemStack inside : bundle.getItems()) add(counts, inside, depth + 1);
		}
	}

	/** Puts the counts on the active list's lines; true when the list has just become complete. */
	static boolean apply(PocketListData list, Map<String, Integer> counts) {
		return list.applyCounts(counts);
	}

	/** Recounts now, redraws the sidebar, and celebrates a list that has just been finished. */
	void recount(Player player) {
		PocketListData list = data.lists(player).active();
		if (list.applyCounts(count(player))) {
			player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.4f);
			player.showTitle(Title.title(Component.text("All gathered!", NamedTextColor.GREEN),
					Component.text("You have everything on " + list.name + ".", NamedTextColor.WHITE)));
		}
		sidebar.show(player, list, data.hud(player), data.lines(player));
	}

	/** Marks the player for one recount on the next tick. */
	void mark(Player player) {
		dirty.add(player.getUniqueId());
		if (scheduled) return;
		scheduled = true;
		Bukkit.getScheduler().runTask(plugin, () -> {
			scheduled = false;
			List<UUID> ids = List.copyOf(dirty);
			dirty.clear();
			for (UUID id : ids) {
				Player p = Bukkit.getPlayer(id);
				if (p != null && p.isOnline()) recount(p);
			}
		});
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onSlotChange(PlayerInventorySlotChangeEvent event) {
		mark(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onClick(InventoryClickEvent event) {
		if (event.getWhoClicked() instanceof Player p) mark(p);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onDrag(InventoryDragEvent event) {
		if (event.getWhoClicked() instanceof Player p) mark(p);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onOpen(InventoryOpenEvent event) {
		if (event.getPlayer() instanceof Player p) mark(p);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onClose(InventoryCloseEvent event) {
		if (event.getPlayer() instanceof Player p) mark(p);
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		Player p = event.getPlayer();
		mark(p);
		if (data.firstWelcome(p)) {
			p.sendMessage(Component.text("Type /pocket to start a list of things to gather.", NamedTextColor.GOLD));
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		dirty.remove(event.getPlayer().getUniqueId());
		sidebar.forget(event.getPlayer());
		data.forget(event.getPlayer());
	}
}
