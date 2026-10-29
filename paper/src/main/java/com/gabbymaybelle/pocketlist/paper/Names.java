package com.gabbymaybelle.pocketlist.paper;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/** English names from item keys, the way the mod shows ids with no language file: {@code minecraft:oak_planks} is "Oak Planks". */
public final class Names {
	private Names() {
	}

	public static String english(String key) {
		String path = key.contains(":") ? key.substring(key.indexOf(':') + 1) : key;
		StringBuilder out = new StringBuilder();
		for (String word : path.split("[_/]")) {
			if (word.isEmpty()) continue;
			if (!out.isEmpty()) out.append(' ');
			out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return out.isEmpty() ? key : out.toString();
	}

	/**
	 * When someone types {@code /pocket chest 2} or {@code /pocket oak log 8}, the sub-command they
	 * meant: "add chest 2" or "add oak log 8", if the words before the number name an item; null
	 * otherwise. {@code isItem} answers for a key such as {@code minecraft:chest}.
	 */
	public static @Nullable String suggestAdd(String[] args, java.util.function.Predicate<String> isItem) {
		if (args.length == 0) return null;
		int words = args.length;
		String last = args[args.length - 1];
		boolean amount = args.length > 1 && (last.chars().allMatch(Character::isDigit) || last.equalsIgnoreCase("stack"));
		if (amount) words--;
		String item = String.join(" ", java.util.Arrays.copyOfRange(args, 0, words));
		if (!isItem.test(key(item))) return null;
		return "add " + item + (amount ? " " + last : "");
	}

	/** "Oak Planks", "oak planks", "oak_planks" or "minecraft:oak_planks" to {@code minecraft:oak_planks}. */
	public static String key(String typed) {
		String s = typed.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", "_");
		return s.contains(":") ? s : "minecraft:" + s;
	}
}
