package com.gabbymaybelle.pocketlist.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The mod's Paste List ({@code ListText}) in plain Java: text from a notes app or a Litematica
 * material list read into list lines. A line is an item and how many ("64 oak planks", "Oak Planks
 * x64", "minecraft:oak_planks 64", "- [ ] 3 torches"); a Litematica table row takes its Total. Names
 * are matched by a resolver the caller gives (the server's items); nothing is guessed, and the names
 * nothing matched are handed back. In chat a list has no line breaks, so {@link #chatLines} splits it
 * on commas and semicolons first.
 */
public final class Paste {
	/** One line read from the text: the name as written, and how many. */
	public record Line(String name, int amount) {
	}

	/** What was read: the lines matched to items (as entries), and the names nothing matched. */
	public record Result(List<Entry> entries, List<String> unknown) {
	}

	/** "64 oak planks", "64x oak planks", "64 x oak planks". */
	private static final Pattern AMOUNT_FIRST = Pattern.compile("^(\\d+)\\s*[x×]?\\s+(.+)$", Pattern.CASE_INSENSITIVE);
	/** "oak planks 64", "oak planks x64", "oak planks: 64", "oak planks = 64". */
	private static final Pattern AMOUNT_LAST = Pattern.compile("^(.+?)\\s*[x×*:=]?\\s*(\\d+)$", Pattern.CASE_INSENSITIVE);
	/** A bullet or checkbox in front of a line from a notes app: "- ", "* ", "• ", "[ ] ", "- [x] ". */
	private static final Pattern BULLET = Pattern.compile("^(?:[-*•]\\s*)?(?:\\[[ xX]?]\\s*)?");
	/** A Litematica table border: "+-------+-----+". */
	private static final Pattern BORDER = Pattern.compile("^[+\\-=|\\s]*$");

	private Paste() {
	}

	/** A list typed in chat, "64 oak planks, 32 cobblestone; 3 torches", as one line per item. */
	public static String chatLines(String text) {
		return text.replaceAll("\\s*[,;]\\s*", "\n");
	}

	/**
	 * Reads the text into entries. {@code resolve} turns a name as written into the item keys its line
	 * counts (one item, or a tag's items), or null when nothing matches.
	 */
	public static Result read(String text, Function<String, @Nullable List<String>> resolve) {
		List<Entry> entries = new ArrayList<>();
		List<String> unknown = new ArrayList<>();
		for (Line line : lines(text)) {
			List<String> items = resolve.apply(line.name());
			if (items == null || items.isEmpty()) {
				unknown.add(line.name());
			} else {
				entries.add(new Entry(items, line.amount()));
			}
		}
		return new Result(entries, unknown);
	}

	/** Splits the text into lines with an amount (1 when a line names an item and no number). */
	public static List<Line> lines(String text) {
		List<Line> out = new ArrayList<>();
		for (String raw : text.split("\\R")) {
			String line = raw.strip();
			if (line.isEmpty() || BORDER.matcher(line).matches()) continue;
			if (line.indexOf('|') >= 0) {
				// A Litematica table row: | Item | Total | Missing | Available | ...
				List<String> cells = new ArrayList<>();
				for (String cell : line.split("\\|")) if (!cell.isBlank()) cells.add(cell.strip());
				if (cells.size() >= 2) addIfCounted(cells.get(0), cells.get(1), out);
				continue;
			}
			List<String> cells = csv(line);
			if (cells.size() >= 2) {
				// Litematica's .csv ("Item","Total",...), or "oak planks, 64" in a book.
				if (!addIfCounted(cells.get(0), cells.get(1), out) && isNumber(cells.get(0))) addIfCounted(cells.get(1), cells.get(0), out);
				continue;
			}
			line = BULLET.matcher(line).replaceFirst("").strip();
			if (line.isEmpty()) continue;
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
		return out;
	}

	/** Adds the line if the amount is a number above 0 (headers such as "Item | Total" aren't). */
	private static boolean addIfCounted(String name, String amount, List<Line> out) {
		if (!isNumber(amount) || name.isBlank()) return false;
		long count;
		try {
			count = Long.parseLong(amount.strip());
		} catch (NumberFormatException e) {
			count = Entry.MAX_NEED;
		}
		if (count > 0) out.add(new Line(name.strip(), (int) Math.min(Entry.MAX_NEED, count)));
		return true;
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

	private static boolean isNumber(String text) {
		String s = text.strip();
		return !s.isEmpty() && s.chars().allMatch(Character::isDigit);
	}

	/**
	 * The ways a name may be written for one item key: "oak planks" from {@code minecraft:oak_planks},
	 * and the plural forms the mod also takes ("torches" for a torch, "planks" stays "planks").
	 */
	public static List<String> candidates(String name) {
		String words = name.strip().toLowerCase(java.util.Locale.ROOT).replace('_', ' ').replaceAll("\\s+", " ");
		List<String> out = new ArrayList<>(List.of(words));
		if (words.endsWith("es")) out.add(words.substring(0, words.length() - 2));
		if (words.endsWith("s")) out.add(words.substring(0, words.length() - 1));
		return out;
	}
}
