package com.gabbymaybelle.pocketlist.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The pure parts, with no server: entries, lists and their JSON, the HUD order, the chime rule, recipe tidying and the breakdown maths. */
class LogicTest {
	private static FoundRecipe recipe(String station, int order, int makes, Object... ingredients) {
		LinkedHashMap<List<String>, Integer> map = new LinkedHashMap<>();
		for (int i = 0; i < ingredients.length; i += 2) {
			Object key = ingredients[i];
			@SuppressWarnings("unchecked")
			List<String> items = key instanceof String s ? List.of(s) : (List<String>) key;
			map.put(items, (Integer) ingredients[i + 1]);
		}
		return new FoundRecipe(station, order, map, makes);
	}

	@Test
	void namesComeFromKeys() {
		assertEquals("Oak Planks", Names.english("minecraft:oak_planks"));
		assertEquals("Blue Ice", Names.english("blue_ice"));
		assertEquals("minecraft:oak_planks", Names.key("Oak Planks"));
		assertEquals("minecraft:oak_planks", Names.key("oak_planks"));
		assertEquals("cozy_core:hearth", Names.key("cozy_core:hearth"));
	}

	@Test
	void unknownWordThatNamesAnItemSuggestsAdd() {
		java.util.Set<String> items = java.util.Set.of("minecraft:chest", "minecraft:oak_log");
		assertEquals("add chest 2", Names.suggestAdd(new String[] {"chest", "2"}, items::contains));
		assertEquals("add oak log 8", Names.suggestAdd(new String[] {"oak", "log", "8"}, items::contains));
		assertEquals("add oak log stack", Names.suggestAdd(new String[] {"oak", "log", "stack"}, items::contains));
		assertEquals("add chest", Names.suggestAdd(new String[] {"chest"}, items::contains));
		assertNull(Names.suggestAdd(new String[] {"chests", "2"}, items::contains));
		assertNull(Names.suggestAdd(new String[] {"frobnicate"}, items::contains));
		assertNull(Names.suggestAdd(new String[0], items::contains));
		assertEquals(List.of("chest"), List.of(Names.withoutAmount(new String[] {"chest", "2"})));
		assertEquals(List.of("oak", "log"), List.of(Names.withoutAmount(new String[] {"oak", "log", "stack"})));
		assertEquals(List.of("oak", "log"), List.of(Names.withoutAmount(new String[] {"oak", "log"})));
		assertEquals(List.of("2"), List.of(Names.withoutAmount(new String[] {"2"})));
	}

	@Test
	void entryLineCapsHaveAtNeed() {
		Entry e = new Entry("minecraft:oak_planks", 64);
		e.have = 100;
		assertEquals("64/64 Oak Planks", e.line());
		assertTrue(e.done());
		assertEquals(0, e.left());
		e.have = 24;
		assertEquals("24/64 Oak Planks", e.line());
		assertEquals(40, e.left());
		assertEquals("Oak Planks (or similar)", new Entry(List.of("minecraft:oak_planks", "minecraft:birch_planks"), 1).name());
		assertEquals(Entry.MAX_NEED, new Entry("minecraft:stick", 1_000_000).need);
	}

	@Test
	void sameKeyTopsUpInsteadOfDoubling() {
		PocketListData list = new PocketListData("Cottage");
		list.topUp(List.of("minecraft:stick"), 8);
		list.topUp(List.of("minecraft:stick"), 4);
		list.topUp(List.of("minecraft:torch"), 16);
		assertEquals(2, list.entries.size());
		assertEquals(12, list.find("minecraft:stick").need);
	}

	@Test
	void hudOrderPutsUnfinishedFirstAndKeepsListOrder() {
		PocketListData list = new PocketListData("x");
		Entry a = list.topUp(List.of("minecraft:a"), 1);
		Entry b = list.topUp(List.of("minecraft:b"), 1);
		Entry c = list.topUp(List.of("minecraft:c"), 1);
		a.have = 1;
		assertEquals(List.of(b, c, a), list.hudOrder());
		assertEquals(1, list.gathered());
		assertFalse(list.allDone());
		list.clearDone();
		assertEquals(List.of(b, c), list.entries);
	}

	@Test
	void chimeOnlyOnTheTransitionToDone() {
		PocketListData list = new PocketListData("x");
		list.topUp(List.of("minecraft:stick"), 4);
		// Already complete at the first count: no chime.
		assertFalse(list.applyCounts(Map.of("minecraft:stick", 4)));
		assertFalse(list.applyCounts(Map.of("minecraft:stick", 4)));
		assertFalse(list.applyCounts(Map.of("minecraft:stick", 1)));
		assertTrue(list.applyCounts(Map.of("minecraft:stick", 9)));
		assertFalse(list.applyCounts(Map.of("minecraft:stick", 9)));
		assertEquals(9, list.entries.get(0).have);
	}

	@Test
	void listsRoundTripThroughJsonAndWrap() {
		Lists lists = new Lists();
		lists.addList("Cottage").topUp(List.of("minecraft:oak_planks"), 64);
		lists.addList("Barn").topUp(List.of("minecraft:spruce_log"), 10);
		assertEquals("Barn", lists.active().name);
		Lists back = Lists.fromJson(lists.toJson());
		assertEquals(2, back.lists.size());
		assertEquals(1, back.active);
		assertEquals(64, back.lists.get(0).find("minecraft:oak_planks").need);
		assertEquals(0, back.lists.get(0).find("minecraft:oak_planks").have, "have is never saved");
		back.step(1);
		assertEquals("Cottage", back.active().name);
		assertTrue(back.use("barn"));
		assertEquals("Barn", back.active().name);
		back.deleteActive();
		back.deleteActive();
		assertEquals("List 1", back.active().name, "deleting the last list leaves a fresh one");
		assertEquals(0, Lists.fromJson("not json").lists.size());
		assertEquals(0, Lists.fromJson(null).lists.size());
		Lists three = new Lists();
		three.addList("a");
		three.addList("b");
		assertEquals("List 3", three.addList("  ").name);
	}

	@Test
	void tidyMergesIdenticalRecipesAndSorts() {
		FoundRecipe furnace = recipe("Furnace", FoundRecipe.FURNACE, 1, "minecraft:raw_iron", 1);
		FoundRecipe blast = recipe("Blast Furnace", FoundRecipe.FURNACE, 1, "minecraft:raw_iron", 1);
		FoundRecipe unpack = recipe("Crafting Table", FoundRecipe.CRAFTING, 9, "minecraft:iron_block", 1);
		FoundRecipe nuggets = recipe("Crafting Table", FoundRecipe.CRAFTING, 1, "minecraft:iron_nugget", 9);
		List<FoundRecipe> tidy = FoundRecipe.tidy(List.of(unpack, furnace, blast, nuggets));
		assertEquals(3, tidy.size());
		assertEquals("Crafting Table", tidy.get(0).station());
		assertEquals(9, tidy.get(0).ingredients().get(List.of("minecraft:iron_nugget")));
		assertEquals("Furnace (or Blast Furnace)", tidy.get(1).station());
		assertTrue(tidy.get(2).unpacking(), "storage-block unpacking sorts last");
	}

	@Test
	void breakdownReplacesTheLineWithCraftsTimesIngredients() {
		PocketListData list = new PocketListData("x");
		list.topUp(List.of("minecraft:torch"), 3);
		Entry sticks = list.topUp(List.of("minecraft:stick"), 64);
		list.topUp(List.of("minecraft:oak_planks"), 2);
		sticks.have = 10;
		FoundRecipe planksToSticks = recipe("Crafting Table", FoundRecipe.CRAFTING, 4, "minecraft:oak_planks", 2);
		assertEquals(14, planksToSticks.crafts(sticks));
		planksToSticks.apply(list, sticks);
		assertNull(list.find("minecraft:stick"));
		assertEquals(2 + 28, list.find("minecraft:oak_planks").need, "an existing line is topped up, not doubled");
		assertEquals(List.of("minecraft:torch", "minecraft:oak_planks"), list.entries.stream().map(Entry::key).toList());

		PocketListData two = new PocketListData("y");
		Entry torch = two.topUp(List.of("minecraft:torch"), 10);
		two.topUp(List.of("minecraft:dirt"), 1);
		FoundRecipe torchRecipe = recipe("Crafting Table", FoundRecipe.CRAFTING, 4, List.of("minecraft:charcoal", "minecraft:coal"), 1, "minecraft:stick", 1);
		torchRecipe.apply(two, torch);
		assertEquals(List.of("minecraft:charcoal|minecraft:coal", "minecraft:stick", "minecraft:dirt"), two.entries.stream().map(Entry::key).toList(), "ingredients take the line's place");
		assertEquals(3, two.entries.get(0).need);
		assertEquals("Charcoal (or similar)", two.entries.get(0).name());
		assertEquals("1 Charcoal (or similar), 1 Stick, makes 4", torchRecipe.describe());
		assertNotNull(two.find("minecraft:stick"));
	}

	@Test
	void copyTextIsOneLinePerEntry() {
		PocketListData list = new PocketListData("x");
		list.topUp(List.of("minecraft:oak_planks"), 64);
		list.topUp(List.of("minecraft:torch"), 16);
		assertEquals("64 Oak Planks\n16 Torch\n", ChatList.copyText(list));
	}

	@Test
	void chatButtonsNameAnOrSimilarLineShortly() {
		List<String> planks = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "pale_oak", "crimson", "warped", "mangrove", "bamboo", "cherry", "poplar")
				.stream().map(w -> "minecraft:" + w + "_planks").toList();
		PocketListData list = new PocketListData("x");
		Entry oakOnly = list.topUp(List.of("minecraft:oak_planks"), 4);
		Entry anyPlanks = list.topUp(planks, 16);
		// The longest button command must fit chat's 256-character limit, or the server drops the player.
		assertTrue(("/pocket need " + anyPlanks.ref() + " " + Entry.MAX_NEED).length() <= 256, anyPlanks.ref());
		assertEquals("minecraft:oak_planks+", anyPlanks.ref());
		assertEquals(anyPlanks, list.find(anyPlanks.ref()));
		assertEquals(oakOnly, list.find(oakOnly.ref()));
		assertEquals(anyPlanks, list.find(anyPlanks.key()));
	}

	@Test
	void addingWhatYouHaveToAnEmptyListDoesNotChime() {
		PocketListData list = new PocketListData("x");
		assertFalse(list.applyCounts(Map.of("minecraft:oak_log", 9)));
		list.topUp(List.of("minecraft:oak_log"), 4);
		assertFalse(list.applyCounts(Map.of("minecraft:oak_log", 9)), "nine logs in hand, four added: no chime, as in the mod");
		list.topUp(List.of("minecraft:torch"), 4);
		assertFalse(list.applyCounts(Map.of("minecraft:oak_log", 9)));
		assertTrue(list.applyCounts(Map.of("minecraft:oak_log", 9, "minecraft:torch", 4)), "the torches gathered: chime");
		list.topUp(List.of("minecraft:stick"), 3);
		assertFalse(list.applyCounts(Map.of()));
		list.entries.clear();
		assertFalse(list.applyCounts(Map.of()), "clearing an unfinished list: no chime");
	}

	@Test
	void chatButtonsStepFromTheCurrentNeed() {
		// An old chat row's [+] clicked twice must count twice: the buttons send +1, not "17".
		int need = 16;
		need = Entry.needAfter(need, "+1");
		need = Entry.needAfter(need, "+1");
		assertEquals(18, need);
		assertEquals(82, Entry.needAfter(18, "+64"));
		assertEquals(1, Entry.needAfter(1, "-1"));
		assertEquals(5, Entry.needAfter(18, "5"));
		assertEquals(0, Entry.needAfter(18, "0"));
		assertEquals(-1, Entry.needAfter(18, "lots"));
		assertEquals(Entry.MAX_NEED, Entry.needAfter(Entry.MAX_NEED, "+64"));
	}
}
