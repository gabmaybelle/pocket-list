package com.gabbymaybelle.pocketlist.paper;

import java.util.Map;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.Dispenser;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Dropper;
import org.bukkit.block.Hopper;
import org.bukkit.block.Shelf;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The mod's chest memory on a server: the containers a player has opened (chests, barrels, shulker
 * boxes, hoppers, dispensers, droppers) and the shelves they've used count toward their list while
 * they're within {@code chestRadius} blocks, in the same world. Only containers the player opened
 * themselves count, as in the mod; but the server can see inside them, so each one is read as it is
 * now rather than as it was left, and a chest a friend or a hopper filled since counts too. A double
 * chest is kept under one place (its left half) so it counts once, ender chests aren't remembered
 * (their contents are the player's own wherever they're opened), and a place that's no longer a
 * container is forgotten the next time it's read.
 */
final class ChestMemory implements Listener {
	private final PlayerData data;
	private final Settings settings;
	private final Counter counter;

	ChestMemory(PlayerData data, Settings settings, Counter counter) {
		this.data = data;
		this.settings = settings;
		this.counter = counter;
	}

	/** A container screen closing: its block is remembered (whatever the player did inside, the counter recounts). */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onClose(InventoryCloseEvent event) {
		if (!settings.chestMemory() || !(event.getPlayer() instanceof Player player)) return;
		Remembered.Place place = placeOf(event.getInventory());
		if (place == null) return;
		data.memory(player).add(place);
		data.saveMemory(player);
	}

	/** A shelf shows what it holds without a screen: using one remembers it, like the mod's storage blocks. */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onUse(PlayerInteractEvent event) {
		Block block = event.getClickedBlock();
		if (!settings.chestMemory() || event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null) return;
		if (!(block.getState(false) instanceof Shelf)) return;
		data.memory(event.getPlayer()).add(place(block));
		data.saveMemory(event.getPlayer());
		counter.mark(event.getPlayer());
	}

	/** The block place of a remembered kind of container's screen, or null (furnaces, ender chests, minecarts, the player's own). */
	static Remembered.@Nullable Place placeOf(Inventory inventory) {
		InventoryHolder holder = inventory.getHolder(false);
		if (holder instanceof DoubleChest dc && dc.getLeftSide(false) instanceof Chest left) return place(left.getBlock());
		if (holder instanceof Chest || holder instanceof Barrel || holder instanceof ShulkerBox || holder instanceof Hopper
				|| holder instanceof Dispenser || holder instanceof Dropper) {
			return canonical(((BlockState) holder).getBlock());
		}
		return null;
	}

	static Remembered.Place place(Block block) {
		return new Remembered.Place(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
	}

	/** Both halves of a double chest are kept under its left half, so it's never counted twice. */
	static Remembered.Place canonical(Block block) {
		if (block.getState(false) instanceof Chest chest && chest.getInventory() instanceof DoubleChestInventory dci
				&& dci.getLeftSide().getHolder(false) instanceof Chest left) {
			return place(left.getBlock());
		}
		return place(block);
	}

	/**
	 * Adds what the player's remembered containers nearby hold to the counts. The container open on
	 * the player's screen is left out, since the counter already counts its slots.
	 */
	void addNearby(Player player, Map<String, Integer> counts) {
		if (!settings.chestMemory()) return;
		Remembered memory = data.memory(player);
		if (memory.isEmpty()) return;
		Remembered.Place open = placeOf(player.getOpenInventory().getTopInventory());
		if (addNearby(memory, player.getLocation(), settings.chestRadius(), open, counts)) data.saveMemory(player);
	}

	/**
	 * The count itself, for any place to stand (the selftest has no player): adds what the remembered
	 * containers within {@code radius} of {@code here} hold, leaving out {@code open}. True when the
	 * memory changed (a container gone, or a chest that became a double chest's right half).
	 */
	static boolean addNearby(Remembered memory, Location here, int radius, Remembered.@Nullable Place open, Map<String, Integer> counts) {
		World world = here.getWorld();
		double reach = (double) radius * radius;
		boolean changed = false;
		java.util.Set<Remembered.Place> counted = new java.util.HashSet<>();
		for (Remembered.Place place : memory.all()) {
			if (!place.world().equals(world.getUID())) continue;
			if (here.distanceSquared(new Location(world, place.x() + 0.5, place.y() + 0.5, place.z() + 0.5)) > reach) continue;
			if (!world.isChunkLoaded(place.x() >> 4, place.z() >> 4)) continue;
			Block block = world.getBlockAt(place.x(), place.y(), place.z());
			Inventory inventory = inventoryAt(block);
			if (inventory == null) {
				// Broken, or something else stands there now.
				memory.remove(place);
				changed = true;
				continue;
			}
			Remembered.Place canonical = block.getState(false) instanceof Chest ? canonical(block) : place;
			if (!canonical.equals(place)) {
				// A single chest that became the right half of a double one: kept under its left half from now on.
				memory.remove(place);
				memory.add(canonical);
				changed = true;
			}
			// A double chest's inventory is both halves, so it's counted once whichever half was remembered.
			if (canonical.equals(open) || !counted.add(canonical)) continue;
			for (ItemStack stack : inventory.getContents()) Counter.add(counts, stack, 0);
		}
		return changed;
	}

	/** What a remembered block holds now: a container's inventory (both halves for a double chest), a shelf's, or null. */
	private static @Nullable Inventory inventoryAt(Block block) {
		BlockState state = block.getState(false);
		if (state instanceof Shelf shelf) return shelf.getInventory();
		if (state instanceof Chest || state instanceof Barrel || state instanceof ShulkerBox || state instanceof Hopper
				|| state instanceof Dispenser || state instanceof Dropper) {
			return ((Container) state).getInventory();
		}
		return null;
	}
}
