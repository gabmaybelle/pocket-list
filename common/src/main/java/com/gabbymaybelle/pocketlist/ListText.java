package com.gabbymaybelle.pocketlist;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Lists as plain text: reading one that was pasted in or exported by Litematica, and writing one
 * out to copy. A line is an item and how many ("64 oak planks", "Oak Planks x64",
 * "minecraft:oak_planks 64", "#minecraft:planks 16"); Litematica's material list files (the
 * table in its .txt, or its .csv) work as they are, taking each row's Total.
 */
public final class ListText {
	/** One line read from the text: the name as written, and how many. */
	record Line(String name, int amount) {
	}

	/** What was read: the list's name if the text had one, its lines, and the names nothing matched. */
	public record Result(String title, List<Entry> entries, List<String> unknown) {
	}

	/** "64 oak planks", "64x oak planks", "64 x oak planks". */
	private static final Pattern AMOUNT_FIRST = Pattern.compile("^(\\d+)\\s*[x×]?\\s+(.+)$", Pattern.CASE_INSENSITIVE);
	/** "oak planks 64", "oak planks x64", "oak planks: 64", "oak planks = 64". */
	private static final Pattern AMOUNT_LAST = Pattern.compile("^(.+?)\\s*[x×*:=]?\\s*(\\d+)$", Pattern.CASE_INSENSITIVE);
	/** A bullet or checkbox in front of a line from a notes app: "- ", "* ", "• ", "[ ] ", "- [x] ". */
	private static final Pattern BULLET = Pattern.compile("^(?:[-*•]\\s*)?(?:\\[[ xX]?]\\s*)?");
	/** A Litematica table border: "+-------+-----+". */
	private static final Pattern BORDER = Pattern.compile("^[+\\-=|\\s]*$");
	private static final Pattern ID = Pattern.compile("^#?([a-z0-9_.\\-]+:)?[a-z0-9_./\\-]+$");

	private ListText() {
	}

	/** Reads text into list lines, matching each name to an item. Nothing is guessed: names that match no item are handed back. */
	public static Result read(String text, FeatureFlagSet enabled) {
		List<Line> lines = new ArrayList<>();
		String title = lines(text, lines);
		Map<String, Item> byName = names(enabled);
		List<Entry> entries = new ArrayList<>();
		List<String> unknown = new ArrayList<>();
		for (Line line : lines) {
			Entry entry = entry(line, byName);
			if (entry == null) {
				unknown.add(line.name());
			} else {
				entries.add(entry);
			}
		}
		return new Result(title, entries, unknown);
	}

	/**
	 * Splits the text into lines with an amount. Returns the title of a Litematica table (its
	 * schematic's name), or null.
	 */
	static String lines(String text, List<Line> out) {
		String title = null;
		for (String raw : text.split("\\R")) {
			String line = raw.strip();
			if (line.isEmpty() || BORDER.matcher(line).matches()) {
				continue;
			}
			if (line.indexOf('|') >= 0) {
				// A Litematica table row: | Item | Total | Missing | Available | ...
				List<String> cells = new ArrayList<>();
				for (String cell : line.split("\\|")) {
					if (!cell.isBlank()) {
						cells.add(cell.strip());
					}
				}
				if (cells.size() == 1 && title == null && out.isEmpty()) {
					title = cells.getFirst();
				} else if (cells.size() >= 2) {
					addIfCounted(cells.get(0), cells.get(1), out);
				}
				continue;
			}
			List<String> cells = csv(line);
			if (cells.size() >= 2) {
				// Litematica's .csv ("Item","Total",...), or "oak planks, 64".
				if (!addIfCounted(cells.get(0), cells.get(1), out) && isNumber(cells.get(0))) {
					addIfCounted(cells.get(1), cells.get(0), out);
				}
				continue;
			}
			line = BULLET.matcher(line).replaceFirst("").strip();
			if (line.isEmpty()) {
				continue;
			}
			Matcher first = AMOUNT_FIRST.matcher(line);
			Matcher last = AMOUNT_LAST.matcher(line);
			if (first.matches()) {
				addIfCounted(first.group(2), first.group(1), out);
			} else if (last.matches() && !isNumber(line)) {
				addIfCounted(last.group(1), last.group(2), out);
			} else if (!isNumber(line)) {
				out.add(new Line(line, 1));
			}
		}
		return title;
	}

	/** Adds the line if the amount is a number above 0 (headers such as "Item | Total" aren't). */
	private static boolean addIfCounted(String name, String amount, List<Line> out) {
		if (!isNumber(amount) || name.isBlank()) {
			return false;
		}
		long count;
		try {
			count = Long.parseLong(amount.strip());
		} catch (NumberFormatException e) {
			count = Lists.MAX_NEED;
		}
		if (count > 0) {
			out.add(new Line(name.strip(), (int) Math.min(Lists.MAX_NEED, count)));
		}
		return true;
	}

	private static boolean isNumber(String text) {
		String s = text.strip();
		return !s.isEmpty() && s.chars().allMatch(Character::isDigit);
	}

	/** The cells of a comma-separated line, with quotes taken off. One cell if there's no comma. */
	private static List<String> csv(String line) {
		List<String> cells = new ArrayList<>();
		StringBuilder cell = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c == '"') {
				if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
					cell.append('"');
					i++;
				} else {
					quoted = !quoted;
				}
			} else if (c == ',' && !quoted) {
				cells.add(cell.toString().strip());
				cell.setLength(0);
			} else {
				cell.append(c);
			}
		}
		cells.add(cell.toString().strip());
		return cells;
	}

	/** Every item by its name in the game's language and by its id's words ("oak planks"), lower case. */
	private static Map<String, Item> names(FeatureFlagSet enabled) {
		Map<String, Item> byName = new HashMap<>();
		for (Item item : BuiltInRegistries.ITEM) {
			if (item == Items.AIR || (enabled != null && !item.isEnabled(enabled))) {
				continue;
			}
			byName.putIfAbsent(new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT), item);
		}
		for (Item item : BuiltInRegistries.ITEM) {
			if (item != Items.AIR && (enabled == null || item.isEnabled(enabled))) {
				byName.putIfAbsent(BuiltInRegistries.ITEM.getKey(item).getPath().replace('_', ' '), item);
			}
		}
		return byName;
	}

	private static Entry entry(Line line, Map<String, Item> byName) {
		String name = line.name().strip();
		String lower = name.toLowerCase(Locale.ROOT);
		if (ID.matcher(lower).matches() && lower.indexOf(':') >= 0 || lower.startsWith("#")) {
			Identifier id = Identifier.tryParse(lower.startsWith("#") ? lower.substring(1) : lower);
			if (id != null && lower.startsWith("#")) {
				TagKey<Item> tag = TagKey.create(Registries.ITEM, id);
				return BuiltInRegistries.ITEM.get(tag).isPresent() ? Entry.ofTag(tag, line.amount()) : null;
			}
			if (id != null) {
				Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
				return item == Items.AIR ? null : Entry.ofItem(item, line.amount());
			}
		}
		String words = lower.replace('_', ' ').replaceAll("\\s+", " ");
		Item item = byName.get(words);
		if (item == null && words.endsWith("es")) {
			item = byName.get(words.substring(0, words.length() - 2));
		}
		if (item == null && words.endsWith("s")) {
			item = byName.get(words.substring(0, words.length() - 1));
		}
		return item == null ? null : Entry.ofItem(item, line.amount());
	}

	/** The list as text to copy: one line per entry, "64 Oak Planks", or "16 #minecraft:planks" for a tag. */
	public static String write(Lists.PocketListData list) {
		StringBuilder out = new StringBuilder();
		for (Entry entry : list.entries) {
			String name;
			if (entry.tag != null) {
				name = "#" + entry.tag;
			} else {
				Item item = entry.mainItem();
				name = item == null ? (entry.items.isEmpty() ? "?" : entry.items.getFirst()) : new ItemStack(item).getHoverName().getString();
			}
			out.append(entry.need).append(' ').append(name).append('\n');
		}
		return out.toString();
	}

	/**
	 * The newest material list Litematica wrote (its Export button): config/litematica/material_list_*.txt
	 * or .csv, or a material_list folder beside the game's config, as older versions of Litematica use. Null if none.
	 */
	public static Path latestLitematicaList() {
		Path config = Platform.configDir();
		List<Path> dirs = new ArrayList<>(List.of(config.resolve("litematica")));
		if (config.getParent() != null) {
			dirs.add(config.getParent().resolve("litematica").resolve("material_list"));
		}
		Path newest = null;
		long newestTime = Long.MIN_VALUE;
		for (Path dir : dirs) {
			if (!Files.isDirectory(dir)) {
				continue;
			}
			try (Stream<Path> files = Files.list(dir)) {
				for (Path file : (Iterable<Path>) files::iterator) {
					String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
					if (name.startsWith("material_list") && (name.endsWith(".txt") || name.endsWith(".csv"))) {
						long time = Files.getLastModifiedTime(file).toMillis();
						if (time > newestTime) {
							newest = file;
							newestTime = time;
						}
					}
				}
			} catch (IOException e) {
				PocketList.LOGGER.warn("Pocket List: couldn't look in {}", dir, e);
			}
		}
		return newest;
	}
}
