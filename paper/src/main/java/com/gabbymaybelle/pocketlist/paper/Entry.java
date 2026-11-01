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

	/**
	 * The need after {@code /pocket need <item> <amount>}: a plain number sets it (0 takes the line
	 * off); {@code +n} or {@code -n} moves it from where it is, never below 1, like the mod's
	 * buttons. The chat buttons send {@code +1}, {@code -1} and {@code +64}, so clicking an old
	 * row twice counts twice. -1 when the amount isn't a number.
	 */
	public static int needAfter(int current, String amount) {
		int n;
		try {
			n = Integer.parseInt(amount);
		} catch (NumberFormatException e) {
			return -1;
		}
		if (amount.startsWith("+") || amount.startsWith("-")) return clamp(current + n);
		return n < 0 ? -1 : Math.min(MAX_NEED, n);
	}

	/** What identifies the line: its items joined with {@code |}. The same key tops up, never doubles. */
	public String key() {
		return String.join("|", items);
	}

	/**
	 * A short name for the line in chat-button commands: the item, or for an "or similar" line its
	 * first item and a {@code +}. The full key of a planks line runs past chat's 256-character
	 * command limit, and a longer command makes the server drop the player.
	 */
	public String ref() {
		return items.size() > 1 ? mainItem() + "+" : mainItem();
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
