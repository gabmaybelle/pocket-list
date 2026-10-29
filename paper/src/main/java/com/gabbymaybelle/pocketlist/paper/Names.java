package com.gabbymaybelle.pocketlist.paper;

import java.util.Locale;

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

	/** "Oak Planks", "oak planks", "oak_planks" or "minecraft:oak_planks" to {@code minecraft:oak_planks}. */
	public static String key(String typed) {
		String s = typed.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", "_");
		return s.contains(":") ? s : "minecraft:" + s;
	}
}
