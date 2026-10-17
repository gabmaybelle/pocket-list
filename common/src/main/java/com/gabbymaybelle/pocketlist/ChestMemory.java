package com.gabbymaybelle.pocketlist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Remembers what was in the chests, barrels and shulker boxes you've opened, as they were when
 * you closed them, so your list can count what's waiting in storage. Nothing is read from
 * chests you haven't opened yourself. The memory is kept per world (or per server address) in
 * config/pocket_list/chests, on your own computer.
 *
 * <p>Blocks that hold things without opening a screen (decorated pots, and other mods' storage
 * such as jars) are remembered once you've used them.
 * Those blocks tell every player's game what they hold, since they show it or can be checked at
 * a glance, so they're read through the loader's item storage and kept up to date while you're near.
 */
public final class ChestMemory {
	/** Chests further away than this (in blocks) don't count. */
	static final int RADIUS = 32;
	/** A container screen opening this soon (in ticks) after you used a block belongs to that block. */
	private static final int OPEN_WINDOW = 20;
	/** A block that opened no screen is read this long (in ticks) after you used it, once the game has heard back about it. */
	static final int STORAGE_WAIT = 10;
	private static final Gson GSON = new GsonBuilder().create();
	private static final Path FOLDER = Platform.configDir().resolve("pocket_list").resolve("chests");

	/** One remembered container: where it is, and how many of each item it held. */
	static final class Place {
		String dimension;
		int x;
		int y;
		int z;
		Map<String, Integer> items = new HashMap<>();
		/** True for a block that shows what it holds (read live through its item storage), false for one seen through its screen. */
		boolean live;

		BlockPos pos() {
			return new BlockPos(x, y, z);
		}

		String key() {
			return dimension + " " + x + " " + y + " " + z;
		}
	}

	private static final Map<String, Place> REMEMBERED = new HashMap<>();
	/** Where this world's memory is saved, or null when it shouldn't be (no known world). */
	private static Path file;
	private static BlockPos lastUsed;
	private static long lastUsedTick;
	/** The last block used that hasn't opened a screen yet, to be read through its item storage. */
	private static BlockPos usedBlock;
	/** The container that's open right now; it counts live from the screen, not from memory. */
	private static String open;

	private ChestMemory() {
	}

	/** The container screen whose contents are kept when it closes, and where that container is. */
	private static AbstractContainerScreen<?> watched;
	private static Place watchedPlace;

	/** The player used a block (each loader calls this from its use-block event, on the player's side only). */
	public static void onUseBlock(Level level, BlockPos pos) {
		if (level.isClientSide()) {
			lastUsed = pos.immutable();
			usedBlock = lastUsed;
			lastUsedTick = level.getGameTime();
		}
	}

	/** A screen has just been set up: if it's the container you just used, it's remembered when it closes. */
	public static void onScreenInit(Minecraft mc, Screen screen) {
		if (!(screen instanceof AbstractContainerScreen<?> containerScreen) || mc.level == null || lastUsed == null
				|| mc.level.getGameTime() - lastUsedTick > OPEN_WINDOW) {
			return;
		}
		// The block opened a screen, so it's seen through that (or, for a furnace, not at all).
		usedBlock = null;
		if (!remembers(containerScreen.getMenu())) {
			return;
		}
		if (mc.level.getBlockState(lastUsed).getBlock() instanceof EnderChestBlock) {
			// Its contents are yours wherever you open it; they'd count once per ender chest.
			return;
		}
		BlockPos pos = canonical(mc.level, lastUsed);
		String dimension = mc.level.dimension().location().toString();
		lastUsed = null;
		Place place = new Place();
		place.dimension = dimension;
		place.x = pos.getX();
		place.y = pos.getY();
		place.z = pos.getZ();
		open = place.key();
		watched = containerScreen;
		watchedPlace = place;
	}

	/** A screen is closing (or being replaced): the watched container's contents are kept as they are now. */
	public static void onScreenRemoved(Screen screen) {
		if (screen != watched || watchedPlace == null) {
			return;
		}
		Place place = watchedPlace;
		Map<Item, Integer> totals = new HashMap<>();
		for (Slot slot : watched.getMenu().slots) {
			if (!(slot.container instanceof Inventory)) {
				Counter.add(slot.getItem(), totals, 0);
			}
		}
		watched = null;
		watchedPlace = null;
		totals.forEach((item, count) -> place.items.put(BuiltInRegistries.ITEM.getKey(item).toString(), count));
		REMEMBERED.put(place.key(), place);
		if (place.key().equals(open)) {
			open = null;
		}
		save();
	}

	/** Every client tick: a used block that opened no screen is read once the game has heard back about it. */
	public static void onTick(Minecraft mc) {
		if (usedBlock != null && mc.level != null && mc.level.getGameTime() - lastUsedTick >= STORAGE_WAIT) {
			BlockPos pos = usedBlock;
			usedBlock = null;
			rememberStorage(mc, pos);
		}
	}

	/** Joined a world or server: load what was remembered there. */
	public static void onJoin(Minecraft mc) {
		mc.execute(() -> load(mc));
	}

	/** Left a world or server. */
	public static void onDisconnect() {
		save();
		REMEMBERED.clear();
		file = null;
		lastUsed = null;
		usedBlock = null;
		open = null;
		watched = null;
		watchedPlace = null;
	}

	/** Chests, barrels, shulker boxes, hoppers, dispensers and droppers; not furnaces or crafting tables. */
	private static boolean remembers(AbstractContainerMenu menu) {
		return menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu || menu instanceof HopperMenu || menu instanceof DispenserMenu;
	}

	/**
	 * A block you used that opened no screen. If it holds things this game can see (a decorated
	 * pot, a modded jar), it's remembered and kept up to date from here on. A chest used
	 * without opening it shows nothing from here, so it's left to its screen.
	 */
	private static void rememberStorage(Minecraft mc, BlockPos pos) {
		if (mc.level == null || !mc.level.isLoaded(pos) || mc.level.getBlockEntity(pos) == null) {
			return;
		}
		Map<String, Integer> items = contents(mc.level, pos);
		if (items == null || items.isEmpty()) {
			return;
		}
		BlockPos at = canonical(mc.level, pos);
		Place place = new Place();
		place.dimension = mc.level.dimension().location().toString();
		place.x = at.getX();
		place.y = at.getY();
		place.z = at.getZ();
		place.items = items;
		place.live = true;
		REMEMBERED.put(place.key(), place);
		save();
	}

	/**
	 * What a block holds, as this game sees it, through the loader's item storage: null when the
	 * block has no storage at all. Chests and the like show as empty from here until opened, since
	 * the server only tells you what's inside once you do.
	 */
	private static Map<String, Integer> contents(Level level, BlockPos pos) {
		List<ItemStack> stacks;
		try {
			stacks = Platform.blockItems(level, pos);
		} catch (RuntimeException e) {
			// A mod's storage that doesn't expect to be asked from the player's side of the game.
			return null;
		}
		if (stacks == null) {
			return null;
		}
		Map<Item, Integer> totals = new HashMap<>();
		for (ItemStack stack : stacks) {
			if (!stack.isEmpty()) {
				Counter.add(stack, totals, 0);
			}
		}
		Map<String, Integer> out = new HashMap<>();
		totals.forEach((item, count) -> out.put(BuiltInRegistries.ITEM.getKey(item).toString(), count));
		return out;
	}

	/** Both halves of a double chest are kept under one place, so it's never counted twice. */
	private static BlockPos canonical(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
			BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state)).immutable();
			String dimension = level.dimension().location().toString();
			BlockPos first = other.compareTo(pos) < 0 ? other : pos;
			BlockPos second = first == other ? pos : other;
			REMEMBERED.remove(dimension + " " + second.getX() + " " + second.getY() + " " + second.getZ());
			return first;
		}
		return pos;
	}

	/** Adds everything remembered within {@link #RADIUS} blocks of the player, except what's open. */
	static void nearby(Minecraft mc, Map<Item, Integer> into) {
		if (mc.level == null || mc.player == null) {
			return;
		}
		String dimension = mc.level.dimension().location().toString();
		BlockPos here = mc.player.blockPosition();
		boolean changed = false;
		Iterator<Place> it = REMEMBERED.values().iterator();
		while (it.hasNext()) {
			Place place = it.next();
			if (!place.dimension.equals(dimension) || place.key().equals(open)) {
				continue;
			}
			BlockPos pos = place.pos();
			boolean near = pos.closerThan(here, RADIUS);
			if (mc.level.isLoaded(pos)) {
				if (mc.level.getBlockEntity(pos) == null) {
					// Broken or moved since.
					it.remove();
					changed = true;
					continue;
				}
				if (place.live && near) {
					// A block that shows what it holds: whatever's in it now, however it got there.
					Map<String, Integer> fresh = contents(mc.level, pos);
					if (fresh == null) {
						// Something else stands there now.
						it.remove();
						changed = true;
						continue;
					}
					if (!fresh.equals(place.items)) {
						place.items = fresh;
						changed = true;
					}
				}
			}
			if (near) {
				place.items.forEach((id, count) -> {
					ResourceLocation parsed = ResourceLocation.tryParse(id);
					if (parsed != null) {
						BuiltInRegistries.ITEM.getOptional(parsed).ifPresent(item -> into.merge(item, count, Integer::sum));
					}
				});
			}
		}
		if (changed) {
			save();
		}
	}

	private static void load(Minecraft mc) {
		REMEMBERED.clear();
		file = fileFor(mc);
		if (file == null || !Files.exists(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file)) {
			List<Place> places = GSON.fromJson(reader, new TypeToken<List<Place>>() { }.getType());
			if (places != null) {
				for (Place place : places) {
					if (place != null && place.dimension != null && place.items != null) {
						REMEMBERED.put(place.key(), place);
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			PocketList.LOGGER.warn("Pocket List: couldn't read remembered chests from {}; starting fresh", file, e);
		}
	}

	private static void save() {
		if (file == null) {
			return;
		}
		try {
			List<Place> places = new ArrayList<>(REMEMBERED.values());
			SafeFiles.write(file, writer -> GSON.toJson(places, writer));
		} catch (IOException e) {
			PocketList.LOGGER.warn("Pocket List: couldn't save remembered chests to {}", file, e);
		}
	}

	/** One file per single-player world (by folder name) or per server (by address). */
	private static Path fileFor(Minecraft mc) {
		IntegratedServer server = mc.getSingleplayerServer();
		String name;
		if (server != null) {
			Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
			name = "world-" + root.getFileName();
		} else {
			ServerData data = mc.getCurrentServer();
			if (data == null || data.ip == null || data.ip.isBlank()) {
				return null;
			}
			name = "server-" + data.ip;
		}
		return FOLDER.resolve(name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_") + ".json");
	}
}
