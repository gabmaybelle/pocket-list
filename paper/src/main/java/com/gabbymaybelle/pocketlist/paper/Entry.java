package com.gabbymaybelle.pocketlist.paper;

import java.util.ArrayList;
import java.util.List;

/**
 * One line of a list: an item (or a few that all count, the mod's "or similar" line) and how many
 * are wanted. {@code have} is worked out at each count and never saved. Items are keys such as
 * {@code minecraft:oak_planks}, plain strings so this class needs no server. Same rules as the mod's
 * {@code Entry}: no saved done flag (done is have at least need), a line is known by its key, and a
 * need above {@link #MAX_NEED} is cut to it.
 */
public final class Entry {
	public static final int MAX_NEED = 99_999;

	public List<String> items = new ArrayList<>();
	public int need;
	public transient int have;

	public Entry() {
	}

	public Entry(List<String> items, int need) {
		this.items = new ArrayList<>(items);
		this.need = clamp(need);
	}

	public Entry(String item, int need) {
		this(List.of(item), need);
	}

	public static int clamp(int need) {
		return Math.max(1, Math.min(MAX_NEED, need));
	}

	/** What identifies the line: its items joined with {@code |}. The same key tops up, never doubles. */
	public String key() {
		return String.join("|", items);
	}

	public String mainItem() {
		return items.isEmpty() ? "minecraft:air" : items.get(0);
	}

	public boolean done() {
		return have >= need;
	}

	/** How many are still to gather, never below 0. */
	public int left() {
		return Math.max(0, need - have);
	}

	/** The English name: "Oak Planks", or "Oak Planks (or similar)" for a line with alternatives. */
	public String name() {
		String first = Names.english(mainItem());
		return items.size() > 1 ? first + " (or similar)" : first;
	}

	/** The HUD line, "24/64 Oak Planks": have capped at need, like the mod. */
	public String line() {
		return Math.min(have, need) + "/" + need + " " + name();
	}
}
