package com.gabbymaybelle.pocketlist.paper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A player's lists: any number, one active, the same model as the mod's {@code Lists.Data} minus
 * the client settings. Saved as one JSON string in the player's own persistent data by
 * {@link PlayerData}; this class is plain Java so the unit tests can run it with no server.
 */
public final class Lists {
	private static final Gson GSON = new GsonBuilder().create();

	public List<PocketListData> lists = new ArrayList<>();
	public int active;

	public static Lists fromJson(@Nullable String json) {
		if (json == null || json.isBlank()) return new Lists();
		try {
			Lists l = GSON.fromJson(json, Lists.class);
			if (l == null) return new Lists();
			if (l.lists == null) l.lists = new ArrayList<>();
			l.lists.removeIf(d -> d == null);
			for (PocketListData d : l.lists) {
				if (d.entries == null) d.entries = new ArrayList<>();
				d.entries.removeIf(e -> e == null || e.items == null || e.items.isEmpty() || e.need <= 0);
				if (d.name == null || d.name.isBlank()) d.name = "List";
			}
			return l;
		} catch (JsonSyntaxException e) {
			return new Lists();
		}
	}

	public String toJson() {
		return GSON.toJson(this);
	}

	/** The active list, made if there is none; the index wraps like the mod's. */
	public PocketListData active() {
		if (lists.isEmpty()) lists.add(new PocketListData("List 1"));
		active = Math.floorMod(active, lists.size());
		return lists.get(active);
	}

	/** A new list, switched to; a blank name becomes "List N". */
	public PocketListData addList(@Nullable String name) {
		PocketListData d = new PocketListData(PocketListData.tidyName(name, "List " + (lists.size() + 1)));
		lists.add(d);
		active = lists.size() - 1;
		return d;
	}

	/** Deletes the active list; deleting the last one leaves a fresh "List 1". */
	public void deleteActive() {
		if (lists.isEmpty()) return;
		lists.remove(Math.floorMod(active, lists.size()));
		if (active > 0) active--;
		active();
	}

	/** Switches to the list with this name (case-insensitive); false if there is none. */
	public boolean use(String name) {
		for (int i = 0; i < lists.size(); i++) {
			if (lists.get(i).name.equalsIgnoreCase(name.strip())) {
				active = i;
				return true;
			}
		}
		return false;
	}

	/** The next or previous list, wrapping. */
	public void step(int by) {
		active();
		active = Math.floorMod(active + by, lists.size());
	}
}
