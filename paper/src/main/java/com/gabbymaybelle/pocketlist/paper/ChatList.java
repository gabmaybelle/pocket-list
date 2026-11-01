package com.gabbymaybelle.pocketlist.paper;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

/**
 * The list screen as chat: the mod's rows with their buttons, as clickable text. Each button runs
 * a /pocket command, so what a click does is exactly what typing it would.
 */
final class ChatList {
	static final int PAGE = 15;
	private static final TextColor BUTTON = TextColor.color(0x77BBFF), DANGER = TextColor.color(0xFF8877);

	private ChatList() {
	}

	static Component button(String label, String command, String hover, TextColor colour) {
		return Component.text("[" + label + "]", colour)
				.clickEvent(ClickEvent.runCommand(command))
				.hoverEvent(HoverEvent.showText(Component.text(hover)));
	}

	static Component button(String label, String command, String hover) {
		return button(label, command, hover, BUTTON);
	}

	static void show(Player player, Lists lists, int page) {
		PocketListData list = lists.active();
		int pages = Math.max(1, (list.entries.size() + PAGE - 1) / PAGE);
		page = Math.max(1, Math.min(pages, page));
		Component header = Component.text("", NamedTextColor.GRAY)
				.append(button("<", "/pocket prev", "The previous list"))
				.append(Component.text(" " + list.name + " ", Sidebar.GOLD))
				.append(Component.text(list.gathered() + "/" + list.entries.size(), NamedTextColor.GRAY))
				.append(Component.text(" "))
				.append(button(">", "/pocket next", "The next list"));
		if (lists.lists.size() > 1) header = header.append(Component.text("  (" + lists.lists.size() + " lists)", NamedTextColor.DARK_GRAY));
		player.sendMessage(header);
		if (list.entries.isEmpty()) {
			player.sendMessage(Component.text("Nothing on this list yet. /pocket add <item> [amount] adds one; /pocket add hand adds what you're holding; /pocket add recipe <item> adds what a recipe needs.", NamedTextColor.GRAY));
		}
		List<Entry> rows = list.entries;
		for (int i = (page - 1) * PAGE; i < Math.min(rows.size(), page * PAGE); i++) {
			Entry e = rows.get(i);
			String key = e.ref();
			TextColor colour = e.done() ? Sidebar.GREEN : NamedTextColor.WHITE;
			int stack = Math.max(1, stackOf(e));
			player.sendMessage(Component.text(" " + Math.min(e.have, e.need) + "/" + e.need + " ", colour)
					.append(Sidebar.name(e).color(colour))
					.append(Component.text("  "))
					.append(button("-", "/pocket need " + key + " -1", "One fewer"))
					.append(Component.text(" "))
					.append(button("+", "/pocket need " + key + " +1", "One more"))
					.append(Component.text(" "))
					.append(button("+" + stack, "/pocket need " + key + " +" + stack, "A stack more"))
					.append(Component.text(" "))
					.append(button("break down", "/pocket breakdown " + key, e.done() ? "You have enough of this already" : "Swap this line for what makes it"))
					.append(Component.text(" "))
					.append(button("x", "/pocket remove " + key, "Take this line off", DANGER)));
		}
		if (pages > 1) {
			player.sendMessage(Component.text(" Page " + page + " of " + pages + "  ", NamedTextColor.GRAY)
					.append(page > 1 ? button("prev", "/pocket page " + (page - 1), "The previous page") : Component.empty())
					.append(Component.text(" "))
					.append(page < pages ? button("next", "/pocket page " + (page + 1), "The next page") : Component.empty()));
		}
		Component footer = Component.text(" ")
				.append(button("Clear done", "/pocket clear done", "Take the finished lines off"))
				.append(Component.text(" "))
				.append(button("New list", "/pocket new", "Start another list"))
				.append(Component.text(" "));
		if (!list.entries.isEmpty()) {
			footer = footer.append(Component.text("[Copy]", BUTTON)
					.clickEvent(ClickEvent.copyToClipboard(copyText(list)))
					.hoverEvent(HoverEvent.showText(Component.text("Copy the list as text, one line per item"))));
			footer = footer.append(Component.text(" "));
		}
		footer = footer.append(button("Delete list", "/pocket delete", "Delete this list (asks again)", DANGER));
		player.sendMessage(footer);
	}

	/** The mod's Copy List text: "64 Oak Planks" per line, in list order. */
	static String copyText(PocketListData list) {
		StringBuilder out = new StringBuilder();
		for (Entry e : list.entries) out.append(e.need).append(' ').append(Names.english(e.mainItem())).append('\n');
		return out.toString();
	}

	private static int stackOf(Entry e) {
		org.bukkit.Material m = org.bukkit.Material.matchMaterial(e.mainItem());
		return m == null ? 64 : m.getMaxStackSize();
	}
}
