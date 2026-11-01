package com.gabbymaybelle.pocketlist.paper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The containers one player has opened, as places ("world x y z"), oldest first. Plain Java so the
 * rules have unit tests: at most {@link #MAX} are kept and opening one again makes it the newest, so
 * the one forgotten to make room is the one used longest ago. Saved in the player's persistent data
 * as one line per place.
 */
public final class Remembered {
	public static final int MAX = 256;

	/** A container's place: its world and block. */
	public record Place(UUID world, int x, int y, int z) {
		public String key() {
			return world + " " + x + " " + y + " " + z;
		}

		public static @Nullable Place parse(String key) {
			String[] p = key.strip().split(" ");
			if (p.length != 4) return null;
			try {
				return new Place(UUID.fromString(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
			} catch (IllegalArgumentException e) {
				return null;
			}
		}
	}

	private final LinkedHashSet<Place> places = new LinkedHashSet<>();

	/** Remembers the place as the newest; forgets the oldest when there are more than {@link #MAX}. */
	public void add(Place place) {
		places.remove(place);
		places.add(place);
		while (places.size() > MAX) places.remove(places.iterator().next());
	}

	public boolean remove(Place place) {
		return places.remove(place);
	}

	public boolean isEmpty() {
		return places.isEmpty();
	}

	public int size() {
		return places.size();
	}

	/** A copy, oldest first, so the caller can forget places while going through it. */
	public List<Place> all() {
		return new ArrayList<>(places);
	}

	public String save() {
		StringBuilder out = new StringBuilder();
		for (Place p : places) out.append(p.key()).append('\n');
		return out.toString();
	}

	public static Remembered load(@Nullable String saved) {
		Remembered r = new Remembered();
		if (saved == null) return r;
		for (String line : saved.split("\n")) {
			Place p = Place.parse(line);
			if (p != null) r.add(p);
		}
		return r;
	}
}
