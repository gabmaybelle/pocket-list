package com.gabbymaybelle.pocketlist.paper;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.jspecify.annotations.Nullable;

/**
 * The list at the side of the screen: a scoreboard sidebar per player with the red numbers hidden,
 * drawn like the mod's HUD (title "Name  2/5" in gold, or "Name: all gathered!" in green; lines
 * "24/64 Oak Planks", green when done, those still to gather first; "...and N more" past the line
 * limit). The sidebar is one slot shared with every plugin, so this one takes it only while the
 * active list has lines and the player has it on, and gives the player's previous scoreboard back
 * otherwise.
 */
final class Sidebar {
	static final TextColor GOLD = TextColor.color(0xFFDD77), GREEN = TextColor.color(0x77DD77), GREY = TextColor.color(0xAAAAAA);
	private static final String OBJECTIVE = "pocketlist";

	private final Map<UUID, Scoreboard> ours = new HashMap<>();
	private final Map<UUID, Scoreboard> previous = new HashMap<>();

	void show(Player player, PocketListData list, boolean on, int lines) {
		if (!on || list.entries.isEmpty()) {
			hide(player);
			return;
		}
		UUID id = player.getUniqueId();
		Scoreboard board = ours.get(id);
		if (board == null || player.getScoreboard() != board) {
			// Another plugin's board (or the main one) is up: remember it to give back later.
			previous.put(id, player.getScoreboard());
			board = Bukkit.getScoreboardManager().getNewScoreboard();
			ours.put(id, board);
		}
		Objective old = board.getObjective(OBJECTIVE);
		if (old != null) old.unregister();
		Objective objective = board.registerNewObjective(OBJECTIVE, Criteria.DUMMY, title(list));
		objective.setDisplaySlot(DisplaySlot.SIDEBAR);
		objective.numberFormat(NumberFormat.blank());
		List<Component> rows = rows(list, lines);
		for (int i = 0; i < rows.size(); i++) {
			Score score = objective.getScore("line" + i);
			score.setScore(rows.size() - i);
			score.customName(rows.get(i));
		}
		if (player.getScoreboard() != board) player.setScoreboard(board);
	}

	void hide(Player player) {
		UUID id = player.getUniqueId();
		Scoreboard board = ours.remove(id);
		Scoreboard back = previous.remove(id);
		if (board != null && player.getScoreboard() == board) {
			player.setScoreboard(back != null ? back : Bukkit.getScoreboardManager().getMainScoreboard());
		}
	}

	void forget(Player player) {
		ours.remove(player.getUniqueId());
		previous.remove(player.getUniqueId());
	}

	static Component title(PocketListData list) {
		return list.allDone()
				? Component.text(list.name + ": all gathered!", GREEN)
				: Component.text(list.name + "  " + list.gathered() + "/" + list.entries.size(), GOLD);
	}

	/** The sidebar's rows: at most {@code lines} entries, then "...and N more" if any are left over. */
	static List<Component> rows(PocketListData list, int lines) {
		List<Entry> order = list.hudOrder();
		int shown = Math.min(order.size(), lines);
		List<Component> rows = new java.util.ArrayList<>();
		for (int i = 0; i < shown; i++) {
			Entry e = order.get(i);
			rows.add(Component.text(Math.min(e.have, e.need) + "/" + e.need + " ", e.done() ? GREEN : NamedTextColor.WHITE)
					.append(name(e).color(e.done() ? GREEN : NamedTextColor.WHITE)));
		}
		if (order.size() > shown) rows.add(Component.text("...and " + (order.size() - shown) + " more", GREY));
		return rows;
	}

	/** The item's name in the player's own language where the client knows it, the English name otherwise. */
	static Component name(Entry e) {
		Component first = translated(e.mainItem());
		return e.items.size() > 1 ? first.append(Component.text(" (or similar)")) : first;
	}

	static Component translated(String key) {
		@Nullable Material m = Material.matchMaterial(key);
		return m != null && m.isItem() ? Component.translatable(m.getItemTranslationKey()) : Component.text(Names.english(key));
	}
}
