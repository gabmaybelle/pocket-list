package com.gabbymaybelle.pocketlist.paper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** One named list. {@code wasFinished} is the chime's memory and is never saved (so a list that is already complete when first counted doesn't chime). */
public final class PocketListData {
	public static final int MAX_NAME = 40;

	public String name = "List 1";
	public List<Entry> entries = new ArrayList<>();
	public transient @Nullable Boolean wasFinished;

	public PocketListData() {
	}

	public PocketListData(String name) {
		this.name = tidyName(name, this.name);
	}

	public static String tidyName(@Nullable String name, String fallback) {
		if (name == null || name.isBlank()) return fallback;
		String s = name.strip();
		return s.length() > MAX_NAME ? s.substring(0, MAX_NAME) : s;
	}

	public @Nullable Entry find(String key) {
		for (Entry e : entries) if (e.key().equals(key)) return e;
		// A chat button's short name; it never matches a full key, which has no "+".
		for (Entry e : entries) if (e.ref().equals(key)) return e;
		return null;
	}

	/** Adds {@code need} more of the items, or a new line at the end; returns the line. */
	public Entry topUp(List<String> items, int need) {
		Entry e = find(String.join("|", items));
		if (e == null) {
			e = new Entry(items, need);
			entries.add(e);
		} else {
			e.need = Entry.clamp(e.need + need);
		}
		return e;
	}

	public int gathered() {
		int n = 0;
		for (Entry e : entries) if (e.done()) n++;
		return n;
	}

	public boolean allDone() {
		return !entries.isEmpty() && gathered() == entries.size();
	}

	/** The HUD order: lines still to gather first, otherwise list order (a stable sort, like the mod). */
	public List<Entry> hudOrder() {
		List<Entry> sorted = new ArrayList<>(entries);
		sorted.sort(Comparator.comparing(Entry::done));
		return sorted;
	}

	/**
	 * Puts the counts on the lines and says whether the list has just become complete: the chime's
	 * rule, which fires on the transition only (a list already complete at its first count is quiet).
	 * An empty list counts as finished here, as in the mod, so adding something you already have to
	 * an empty list doesn't chime; emptying a list doesn't chime either.
	 */
	public boolean applyCounts(java.util.Map<String, Integer> counts) {
		for (Entry e : entries) {
			int have = 0;
			for (String item : e.items) have += counts.getOrDefault(item, 0);
			e.have = have;
		}
		boolean finished = entries.isEmpty() || allDone();
		boolean justNow = !entries.isEmpty() && finished && Boolean.FALSE.equals(wasFinished);
		wasFinished = finished;
		return justNow;
	}

	public void clearDone() {
		entries.removeIf(Entry::done);
	}
}
