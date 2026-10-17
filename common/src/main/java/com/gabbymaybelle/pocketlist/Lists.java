package com.gabbymaybelle.pocketlist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;

/**
 * Your lists and settings, kept in config/pocket_list.json. They're yours, not the world's,
 * so the same lists follow you from world to world and server to server.
 */
public final class Lists {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = Platform.configDir().resolve("pocket_list.json");
	/** How long a freshly added line stays highlighted on the HUD. */
	private static final long FLASH_MILLIS = 1500;
	/** No line asks for more than this. */
	public static final int MAX_NEED = 99_999;
	/** The most lines the HUD will show. */
	public static final int MAX_HUD_LINES = 20;

	private static Data data = new Data();

	public static final class Data {
		public List<PocketListData> lists = new ArrayList<>();
		public int active;
		/** Show the active list in the corner of the screen. */
		public boolean hud = true;
		/** Which corner. */
		public HudPosition hudPosition = HudPosition.TOP_LEFT;
		/** Count what's in chests you've opened nearby. */
		public boolean countChests = true;
		/** At most this many lines on the HUD; the rest are summed up as "and 3 more". */
		public int hudLines = 8;
		/** The one-time "press K to start a list" toast has been shown. */
		public boolean welcomed;
	}

	/** The corner of the screen the HUD sits in. */
	public enum HudPosition {
		TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

		public boolean top() {
			return this == TOP_LEFT || this == TOP_RIGHT;
		}

		public boolean right() {
			return this == TOP_RIGHT || this == BOTTOM_RIGHT;
		}
	}

	public static final class PocketListData {
		public String name;
		public List<Entry> entries = new ArrayList<>();
		/** Whether everything was gathered at the last count; null until the first count. */
		transient Boolean wasFinished;
	}

	private Lists() {
	}

	public static Data data() {
		return data;
	}

	public static PocketListData active() {
		if (data.lists.isEmpty()) {
			data.lists.add(newList(1));
		}
		data.active = Math.floorMod(data.active, data.lists.size());
		PocketListData list = data.lists.get(data.active);
		if (DEFAULT_NAME.equals(list.name)) {
			// Named while the game was still starting, before its language files were read, so the
			// name came out as the bare key. Named properly as soon as they're there.
			String named = Component.translatable(DEFAULT_NAME, data.active + 1).getString();
			if (!named.equals(DEFAULT_NAME)) {
				list.name = named;
			}
		}
		return list;
	}

	/** The translation key of a new list's name ("List 1"). */
	private static final String DEFAULT_NAME = "pocket_list.default_name";

	public static PocketListData newList(int number) {
		PocketListData list = new PocketListData();
		list.name = Component.translatable(DEFAULT_NAME, number).getString();
		return list;
	}

	/** Adds to the active list, topping up a line that's already there. */
	public static void add(Entry entry) {
		List<Entry> entries = active().entries;
		topUp(entries, entry, entries.size());
	}

	/**
	 * Tops up the line with this entry's key, or puts the entry in at {@code index} when there's no
	 * such line. Either way the line is highlighted on the HUD for a moment. True if a line was added.
	 */
	private static boolean topUp(List<Entry> entries, Entry entry, int index) {
		if (entry.need <= 0) {
			return false;
		}
		Entry existing = entries.stream().filter(e -> e.key().equals(entry.key())).findFirst().orElse(null);
		if (existing == null) {
			entry.need = Math.min(MAX_NEED, entry.need);
			entries.add(Math.min(index, entries.size()), entry);
			existing = entry;
		} else {
			existing.need = Math.min(MAX_NEED, existing.need + entry.need);
		}
		existing.flashUntil = Util.getMillis() + FLASH_MILLIS;
		return existing == entry;
	}

	/**
	 * Replaces a line with the ingredients for however many crafts it still needs (what you
	 * already have counts), in the same place on the list.
	 */
	public static void breakDown(Entry entry, FoundRecipe recipe) {
		List<Entry> entries = active().entries;
		int index = entries.indexOf(entry);
		if (index < 0) {
			return;
		}
		int stillNeeded = Math.max(0, entry.need - entry.have);
		int crafts = (stillNeeded + recipe.makes() - 1) / recipe.makes();
		entries.remove(index);
		for (Entry ingredient : recipe.ingredientsTimes(crafts)) {
			if (topUp(entries, ingredient, index)) {
				index++;
			}
		}
	}

	/** Minimap mods that draw in the top-left corner, where the list would otherwise sit. */
	private static final List<String> MINIMAP_MODS = List.of("xaerominimap", "journeymap", "voxelmap");
	/** HUD mods that fill the top-left corner with text, but leave the top right free. */
	private static final List<String> TOP_LEFT_HUD_MODS = List.of("simplehudenhanced");

	public static void load() {
		boolean firstRun = !Files.exists(FILE);
		if (!firstRun) {
			try (Reader reader = Files.newBufferedReader(FILE)) {
				Data loaded = GSON.fromJson(reader, Data.class);
				if (loaded != null) {
					data = loaded;
				}
			} catch (IOException | RuntimeException e) {
				// Keep the broken file, so the next save doesn't quietly replace someone's lists.
				Path backup = FILE.resolveSibling("pocket_list.json.broken");
				PocketList.LOGGER.warn("Pocket List: couldn't read {}, starting with an empty list; the old file is kept as {}", FILE, backup, e);
				try {
					Files.copy(FILE, backup, StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException copyFailed) {
					PocketList.LOGGER.warn("Pocket List: couldn't back up {}", FILE, copyFailed);
				}
			}
		}
		if (data.lists == null) {
			data.lists = new ArrayList<>();
		}
		if (data.hudPosition == null) {
			// A corner name this version doesn't know (or none at all).
			data.hudPosition = HudPosition.TOP_LEFT;
		}
		if (firstRun && MINIMAP_MODS.stream().anyMatch(Platform::isModLoaded)) {
			// Keep out of a minimap's corner; once saved, the player's own choice stands.
			data.hudPosition = HudPosition.BOTTOM_RIGHT;
		} else if (firstRun && TOP_LEFT_HUD_MODS.stream().anyMatch(Platform::isModLoaded)) {
			// A HUD mod's text is in the top left: start top right, below any effect icons.
			data.hudPosition = HudPosition.TOP_RIGHT;
		}
		data.hudLines = Math.clamp(data.hudLines, 1, MAX_HUD_LINES);
		for (PocketListData list : data.lists) {
			if (list.entries == null) {
				list.entries = new ArrayList<>();
			}
			list.entries.removeIf(e -> e == null || (e.tag == null && (e.items == null || e.items.isEmpty())));
		}
		active();
	}

	public static void save() {
		try {
			SafeFiles.write(FILE, writer -> GSON.toJson(data, writer));
		} catch (IOException e) {
			PocketList.LOGGER.warn("Pocket List: couldn't save {}", FILE, e);
		}
	}
}
