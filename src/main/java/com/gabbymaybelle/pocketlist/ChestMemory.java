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
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
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
 */
public final class ChestMemory {
	/** Chests further away than this (in blocks) don't count. */
	static final int RADIUS = 32;
	/** A container screen opening this soon (in ticks) after you used a block belongs to that block. */
	private static final int OPEN_WINDOW = 20;
	private static final Gson GSON = new GsonBuilder().create();
	private static final Path FOLDER = FabricLoader.getInstance().getConfigDir().resolve("pocket_list").resolve("chests");

	/** One remembered container: where it is, and how many of each item it held. */
	static final class Place {
		String dimension;
		int x;
		int y;
		int z;
		Map<String, Integer> items = new HashMap<>();

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
	/** The container that's open right now; it counts live from the screen, not from memory. */
	private static String open;

	private ChestMemory() {
	}

	static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide()) {
				lastUsed = hit.getBlockPos().immutable();
				lastUsedTick = level.getGameTime();
			}
			return InteractionResult.PASS;
		});
		ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
			if (!(screen instanceof AbstractContainerScreen<?> containerScreen) || !remembers(containerScreen.getMenu())
					|| mc.level == null || lastUsed == null || mc.level.getGameTime() - lastUsedTick > OPEN_WINDOW) {
				return;
			}
			if (mc.level.getBlockState(lastUsed).getBlock() instanceof EnderChestBlock) {
				// Its contents are yours wherever you open it; they'd count once per ender chest.
				return;
			}
			BlockPos pos = canonical(mc.level, lastUsed);
			String dimension = mc.level.dimension().identifier().toString();
			lastUsed = null;
			Place place = new Place();
			place.dimension = dimension;
			place.x = pos.getX();
			place.y = pos.getY();
			place.z = pos.getZ();
			open = place.key();
			ScreenEvents.remove(screen).register(closed -> {
				Map<Item, Integer> totals = new HashMap<>();
				for (Slot slot : containerScreen.getMenu().slots) {
					if (!(slot.container instanceof Inventory)) {
						Counter.add(slot.getItem(), totals, 0);
					}
				}
				totals.forEach((item, count) -> place.items.put(BuiltInRegistries.ITEM.getKey(item).toString(), count));
				REMEMBERED.put(place.key(), place);
				if (place.key().equals(open)) {
					open = null;
				}
				save();
			});
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> mc.execute(() -> load(mc)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
			save();
			REMEMBERED.clear();
			file = null;
			lastUsed = null;
			open = null;
		});
	}

	/** Chests, barrels, shulker boxes, hoppers, dispensers and droppers; not furnaces or crafting tables. */
	private static boolean remembers(AbstractContainerMenu menu) {
		return menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu || menu instanceof HopperMenu || menu instanceof DispenserMenu;
	}

	/** Both halves of a double chest are kept under one place, so it's never counted twice. */
	private static BlockPos canonical(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
			BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state)).immutable();
			String dimension = level.dimension().identifier().toString();
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
		String dimension = mc.level.dimension().identifier().toString();
		BlockPos here = mc.player.blockPosition();
		boolean forgot = false;
		Iterator<Place> it = REMEMBERED.values().iterator();
		while (it.hasNext()) {
			Place place = it.next();
			if (!place.dimension.equals(dimension) || place.key().equals(open)) {
				continue;
			}
			BlockPos pos = place.pos();
			if (mc.level.isLoaded(pos) && mc.level.getBlockEntity(pos) == null) {
				// Broken or moved since.
				it.remove();
				forgot = true;
				continue;
			}
			if (pos.closerThan(here, RADIUS)) {
				place.items.forEach((id, count) -> {
					Identifier parsed = Identifier.tryParse(id);
					if (parsed != null) {
						BuiltInRegistries.ITEM.getOptional(parsed).ifPresent(item -> into.merge(item, count, Integer::sum));
					}
				});
			}
		}
		if (forgot) {
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
			PocketList.LOGGER.warn("Couldn't read remembered chests from {}; starting fresh", file, e);
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
			PocketList.LOGGER.warn("Couldn't save remembered chests to {}", file, e);
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
