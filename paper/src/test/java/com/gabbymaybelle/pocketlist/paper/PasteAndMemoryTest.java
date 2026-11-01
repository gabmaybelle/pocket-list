package com.gabbymaybelle.pocketlist.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Paste's reading of pasted text and the remembered-container list, with no server. */
class PasteAndMemoryTest {
	private static List<String> read(String text) {
		List<String> out = new ArrayList<>();
		for (Paste.Line l : Paste.lines(text)) out.add(l.amount() + " " + l.name());
		return out;
	}

	@Test
	void readsTheMods_lineFormats() {
		assertEquals(List.of("64 oak planks", "64 Oak Planks", "64 minecraft:oak_planks", "3 torches", "16 stone", "1 lantern"),
				read("64 oak planks\nOak Planks x64\nminecraft:oak_planks 64\n- [ ] 3 torches\nstone: 16\nlantern"));
	}

	@Test
	void readsALitematicaTableAndCsv() {
		String table = "+------+-------+\n| Item | Total |\n+------+-------+\n| Oak Planks | 128 |\n| Glass | 12 |\n+------+-------+";
		assertEquals(List.of("128 Oak Planks", "12 Glass"), read(table));
		assertEquals(List.of("40 Stone Bricks"), read("\"Item\",\"Total\",\"Missing\"\n\"Stone Bricks\",\"40\",\"40\""));
	}

	@Test
	void chatSplitsOnCommasAndSemicolons() {
		assertEquals(List.of("64 oak planks", "32 cobblestone", "3 torches"), read(Paste.chatLines("64 oak planks, 32 cobblestone;3 torches")));
	}

	@Test
	void unknownNamesComeBackAndNothingIsGuessed() {
		Map<String, List<String>> known = Map.of("oak planks", List.of("minecraft:oak_planks"), "torch", List.of("minecraft:torch"));
		Paste.Result r = Paste.read("64 oak planks\n3 torches\n2 nonsense", name -> {
			for (String c : Paste.candidates(name)) if (known.containsKey(c)) return known.get(c);
			return null;
		});
		assertEquals(2, r.entries().size());
		assertEquals(64, r.entries().get(0).need);
		assertEquals(List.of("minecraft:torch"), r.entries().get(1).items);
		assertEquals(List.of("nonsense"), r.unknown());
	}

	@Test
	void amountsAreCappedLikeTheMods() {
		assertEquals(List.of(Entry.MAX_NEED + " stone"), read("999999999999 stone"));
		assertEquals(List.of(), read("0 stone"));
	}

	@Test
	void rememberedKeepsTheNewestAndForgetsTheOldest() {
		UUID world = UUID.randomUUID();
		Remembered r = new Remembered();
		for (int i = 0; i < Remembered.MAX + 3; i++) r.add(new Remembered.Place(world, i, 64, 0));
		assertEquals(Remembered.MAX, r.size());
		assertEquals(3, r.all().get(0).x());
		// Opening an old one again makes it the newest, so it isn't the next to go: 4 goes instead.
		r.add(new Remembered.Place(world, 3, 64, 0));
		r.add(new Remembered.Place(world, 9999, 64, 0));
		assertEquals(5, r.all().get(0).x());
		assertEquals(3, r.all().get(Remembered.MAX - 2).x());
		assertEquals(9999, r.all().get(Remembered.MAX - 1).x());
	}

	@Test
	void rememberedSavesAndLoads() {
		UUID world = UUID.randomUUID();
		Remembered r = new Remembered();
		r.add(new Remembered.Place(world, 1, 2, 3));
		r.add(new Remembered.Place(world, -4, 70, 12));
		Remembered back = Remembered.load(r.save() + "not a place\n");
		assertEquals(r.all(), back.all());
		assertTrue(Remembered.load(null).isEmpty());
		assertNull(Remembered.Place.parse("1 2 3"));
	}
}
