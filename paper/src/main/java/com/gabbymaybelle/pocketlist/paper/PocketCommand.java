package com.gabbymaybelle.pocketlist.paper;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * {@code /pocket} (and {@code /pocketlist}): the whole mod as one command, since a plugin has no
 * keys and no screens.
 * <ul>
 * <li>{@code /pocket}: the active list in chat with its buttons.</li>
 * <li>{@code add <item> [amount|stack]}, {@code add hand [amount|stack]}, {@code add recipe <item> [stack]}.</li>
 * <li>{@code need <item> <amount>}, {@code remove <item>}, {@code clear [done]}.</li>
 * <li>{@code new [name]}, {@code use <name>}, {@code next}, {@code prev}, {@code lists}, {@code rename <name>}, {@code delete}.</li>
 * <li>{@code breakdown <item>} and {@code pick <n|cancel>} when there is more than one recipe.</li>
 * <li>{@code hud on|off}, {@code hud lines <n>}, {@code page <n>}, {@code help}.</li>
 * <li>{@code selftest} (operators, from the console too): the recipe reading and the maths against the server's real recipes.</li>
 * </ul>
 */
final class PocketCommand implements BasicCommand {
	private static final List<String> WORDS = List.of("add", "need", "remove", "clear", "new", "use", "next", "prev", "lists", "rename", "delete", "breakdown", "pick", "hud", "page", "help", "selftest");
	private static final long CONFIRM_MS = 3000;

	private final PlayerData data;
	private final Counter counter;
	private final Map<UUID, Pending> pending = new HashMap<>();
	private final Map<UUID, Long> deleteAsked = new HashMap<>();

	private record Pending(String key, List<FoundRecipe> recipes) {
	}

	PocketCommand(PlayerData data, Counter counter) {
		this.data = data;
		this.counter = counter;
	}

	@Override
	public String permission() {
		return "pocketlist.use";
	}

	@Override
	public Collection<String> suggest(CommandSourceStack source, String[] args) {
		if (args.length <= 1) return starting(WORDS, args.length == 1 ? args[0] : "");
		String word = args[0].toLowerCase(Locale.ROOT);
		if (args.length == 2) {
			switch (word) {
				case "add" -> {
					List<String> all = new ArrayList<>(List.of("hand", "recipe"));
					all.addAll(itemWords(args[1]));
					return starting(all, args[1]);
				}
				case "need", "remove", "breakdown" -> {
					return itemWords(args[1]);
				}
				case "clear" -> {
					return starting(List.of("done", "all"), args[1]);
				}
				case "hud" -> {
					return starting(List.of("on", "off", "lines"), args[1]);
				}
				case "pick" -> {
					return starting(List.of("1", "2", "3", "cancel"), args[1]);
				}
				case "use" -> {
					if (source.getExecutor() instanceof Player p) {
						List<String> names = new ArrayList<>();
						for (PocketListData l : data.lists(p).lists) names.add(l.name);
						return starting(names, args[1]);
					}
				}
				default -> {
				}
			}
		}
		if (args.length == 3) {
			if (word.equals("add") && args[1].equalsIgnoreCase("recipe")) return itemWords(args[2]);
			if (word.equals("add")) return starting(List.of("1", "16", "64", "stack"), args[2]);
		}
		return List.of();
	}

	private static List<String> starting(List<String> words, String prefix) {
		String p = prefix.toLowerCase(Locale.ROOT);
		List<String> out = new ArrayList<>();
		for (String w : words) if (w.toLowerCase(Locale.ROOT).startsWith(p)) out.add(w);
		return out;
	}

	/** Every item's key path, so tab shows the whole item list as the mod's search box does. */
	private static List<String> itemWords(String prefix) {
		String p = prefix.toLowerCase(Locale.ROOT);
		List<String> out = new ArrayList<>();
		for (Material m : Material.values()) {
			if (!m.isItem() || m.isAir()) continue;
			String path = m.getKey().getKey();
			if (path.startsWith(p) || (p.contains(":") && m.getKey().toString().startsWith(p))) out.add(path);
			if (out.size() >= 200) break;
		}
		return out;
	}

	@Override
	public void execute(CommandSourceStack source, String[] args) {
		CommandSender sender = source.getSender();
		String word = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
		if (word.equals("selftest")) {
			if (!sender.hasPermission("pocketlist.admin")) {
				fail(sender, "Pocket List: selftest is for operators.");
				return;
			}
			SelfTest.run(sender);
			return;
		}
		if (word.equals("help")) {
			help(sender);
			return;
		}
		if (!(source.getExecutor() instanceof Player player)) {
			fail(sender, "Pocket List: the list belongs to a player; only /pocket selftest and /pocket help work from here.");
			return;
		}
		Lists lists = data.lists(player);
		PocketListData list = lists.active();
		String[] rest = args.length == 0 ? new String[0] : Arrays.copyOfRange(args, 1, args.length);
		boolean changed = true;
		switch (word) {
			case "" -> {
				changed = false;
				ChatList.show(player, lists, 1);
			}
			case "page" -> {
				changed = false;
				ChatList.show(player, lists, rest.length > 0 ? parseInt(rest[0], 1) : 1);
			}
			case "add" -> changed = add(player, list, rest);
			case "need" -> changed = need(player, list, rest);
			case "remove" -> {
				Entry e = rest.length > 0 ? list.find(String.join(" ", rest)) : null;
				if (e == null) e = rest.length > 0 ? list.find(Names.key(String.join(" ", rest))) : null;
				if (e == null) {
					fail(player, "Pocket List: that line isn't on the list.");
					changed = false;
				} else {
					list.entries.remove(e);
					say(player, "Took " + e.name() + " off the list.");
				}
			}
			case "clear" -> {
				if (rest.length > 0 && rest[0].equalsIgnoreCase("all")) {
					list.entries.clear();
					say(player, "Cleared the list.");
				} else {
					int before = list.entries.size();
					list.clearDone();
					say(player, "Took " + (before - list.entries.size()) + " finished line" + (before - list.entries.size() == 1 ? "" : "s") + " off.");
				}
			}
			case "new" -> {
				PocketListData made = lists.addList(rest.length > 0 ? String.join(" ", rest) : null);
				say(player, "Started \"" + made.name + "\". It's the list shown now.");
			}
			case "use" -> {
				if (rest.length == 0 || !lists.use(String.join(" ", rest))) {
					fail(player, "Pocket List: no list called that. /pocket lists shows them.");
					changed = false;
				} else {
					say(player, "Showing \"" + lists.active().name + "\".");
				}
			}
			case "next", "prev" -> {
				lists.step(word.equals("next") ? 1 : -1);
				changed = false;
				data.save(player);
				counter.recount(player);
				ChatList.show(player, lists, 1);
			}
			case "lists" -> {
				changed = false;
				for (int i = 0; i < lists.lists.size(); i++) {
					PocketListData l = lists.lists.get(i);
					player.sendMessage(Component.text((i == lists.active ? " > " : "   ") + l.name + "  " + l.gathered() + "/" + l.entries.size(), i == lists.active ? Sidebar.GOLD : NamedTextColor.GRAY)
							.clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/pocket use " + l.name)));
				}
			}
			case "rename" -> {
				if (rest.length == 0) {
					fail(player, "Pocket List: /pocket rename <name>");
					changed = false;
				} else {
					list.name = PocketListData.tidyName(String.join(" ", rest), list.name);
					say(player, "Renamed the list to \"" + list.name + "\".");
				}
			}
			case "delete" -> {
				Long asked = deleteAsked.get(player.getUniqueId());
				if (asked == null || System.currentTimeMillis() - asked > CONFIRM_MS) {
					deleteAsked.put(player.getUniqueId(), System.currentTimeMillis());
					player.sendMessage(Component.text("Delete \"" + list.name + "\"? ", NamedTextColor.GOLD)
							.append(ChatList.button("Really delete", "/pocket delete", "Gone for good")));
					changed = false;
				} else {
					deleteAsked.remove(player.getUniqueId());
					String gone = list.name;
					lists.deleteActive();
					say(player, "Deleted \"" + gone + "\".");
				}
			}
			case "breakdown" -> changed = breakDown(player, list, rest);
			case "pick" -> changed = pick(player, list, rest);
			case "hud" -> {
				changed = false;
				if (rest.length > 0 && rest[0].equalsIgnoreCase("lines")) {
					int n = rest.length > 1 ? parseInt(rest[1], -1) : -1;
					if (n < PlayerData.MIN_LINES || n > PlayerData.MAX_LINES) {
						fail(player, "Pocket List: lines is " + PlayerData.MIN_LINES + " to " + PlayerData.MAX_LINES + ".");
					} else {
						data.lines(player, n);
						say(player, "The sidebar shows " + n + " line" + (n == 1 ? "" : "s") + ".");
					}
				} else if (rest.length > 0 && (rest[0].equalsIgnoreCase("on") || rest[0].equalsIgnoreCase("off"))) {
					data.hud(player, rest[0].equalsIgnoreCase("on"));
					say(player, "The sidebar is " + rest[0].toLowerCase(Locale.ROOT) + ".");
				} else {
					fail(player, "Pocket List: /pocket hud on, /pocket hud off, or /pocket hud lines <1-" + PlayerData.MAX_LINES + ">");
				}
				counter.recount(player);
			}
			default -> {
				changed = false;
				fail(player, "Pocket List: unknown: " + args[0] + ". /pocket help lists the commands.");
			}
		}
		if (changed) {
			data.save(player);
			counter.recount(player);
		}
	}

	private boolean add(Player player, PocketListData list, String[] rest) {
		if (rest.length == 0) {
			fail(player, "Pocket List: /pocket add <item> [amount|stack], /pocket add hand [amount|stack], /pocket add recipe <item> [stack]");
			return false;
		}
		if (rest[0].equalsIgnoreCase("recipe")) {
			if (rest.length < 2) {
				fail(player, "Pocket List: /pocket add recipe <item> [stack]");
				return false;
			}
			boolean stack = rest[rest.length - 1].equalsIgnoreCase("stack");
			Material m = material(Arrays.copyOfRange(rest, 1, stack ? rest.length - 1 : rest.length));
			if (m == null) {
				fail(player, "Pocket List: no item called " + String.join(" ", Arrays.copyOfRange(rest, 1, rest.length)) + ".");
				return false;
			}
			List<FoundRecipe> recipes = ServerRecipes.lookup(m);
			if (recipes.isEmpty()) {
				fail(player, "Pocket List: no recipe found for " + Names.english(m.getKey().toString()) + ".");
				return false;
			}
			// One craft's ingredients, or enough crafts for a stack of the result (the mod's Shift).
			int crafts = stack ? Math.max(1, (m.getMaxStackSize() + recipes.get(0).makes() - 1) / recipes.get(0).makes()) : 1;
			if (recipes.size() == 1) {
				addIngredients(list, recipes.get(0), crafts);
				say(player, "Added what " + recipes.get(0).station() + " needs for " + Names.english(m.getKey().toString()) + (crafts > 1 ? " (" + crafts + " crafts)." : "."));
				return true;
			}
			pending.put(player.getUniqueId(), new Pending("+" + crafts + "|" + m.getKey(), recipes));
			offer(player, recipes, Names.english(m.getKey().toString()));
			return false;
		}
		int amount = 1;
		boolean stack = false;
		String[] itemWords = rest;
		if (rest.length > 1) {
			String last = rest[rest.length - 1];
			if (last.equalsIgnoreCase("stack")) {
				stack = true;
				itemWords = Arrays.copyOfRange(rest, 0, rest.length - 1);
			} else if (last.chars().allMatch(Character::isDigit)) {
				amount = parseInt(last, 1);
				itemWords = Arrays.copyOfRange(rest, 0, rest.length - 1);
			}
		}
		Material m;
		if (itemWords.length == 1 && itemWords[0].equalsIgnoreCase("hand")) {
			ItemStack held = player.getInventory().getItemInMainHand();
			if (held.isEmpty()) held = player.getInventory().getItemInOffHand();
			if (held.isEmpty()) {
				fail(player, "Pocket List: you're not holding anything.");
				return false;
			}
			m = held.getType();
		} else {
			m = material(itemWords);
			if (m == null) {
				fail(player, "Pocket List: no item called " + String.join(" ", itemWords) + ". Tab after /pocket add shows them.");
				return false;
			}
		}
		if (stack) amount = m.getMaxStackSize();
		if (amount <= 0) {
			fail(player, "Pocket List: the amount should be 1 or more.");
			return false;
		}
		Entry e = list.topUp(List.of(m.getKey().toString()), amount);
		say(player, "Added " + amount + " " + e.name() + " (" + e.need + " on the list).");
		return true;
	}

	private boolean need(Player player, PocketListData list, String[] rest) {
		if (rest.length < 2) {
			fail(player, "Pocket List: /pocket need <item> <amount>");
			return false;
		}
		int amount = parseInt(rest[rest.length - 1], -1);
		String key = String.join(" ", Arrays.copyOfRange(rest, 0, rest.length - 1));
		Entry e = list.find(key);
		if (e == null) e = list.find(Names.key(key));
		if (e == null) {
			Material m = material(Arrays.copyOfRange(rest, 0, rest.length - 1));
			if (m == null) {
				fail(player, "Pocket List: that line isn't on the list.");
				return false;
			}
			if (amount <= 0) return false;
			e = list.topUp(List.of(m.getKey().toString()), amount);
			say(player, "Added " + e.need + " " + e.name() + ".");
			return true;
		}
		if (amount <= 0) {
			list.entries.remove(e);
			say(player, "Took " + e.name() + " off the list.");
			return true;
		}
		e.need = Entry.clamp(amount);
		return true;
	}

	private boolean breakDown(Player player, PocketListData list, String[] rest) {
		if (rest.length == 0) {
			fail(player, "Pocket List: /pocket breakdown <item>");
			return false;
		}
		String key = String.join(" ", rest);
		Entry e = list.find(key);
		if (e == null) e = list.find(Names.key(key));
		if (e == null) {
			fail(player, "Pocket List: that line isn't on the list.");
			return false;
		}
		Counter.apply(list, Counter.count(player));
		if (e.done()) {
			say(player, "You have enough " + e.name() + " already.");
			return false;
		}
		Material m = Material.matchMaterial(e.mainItem());
		List<FoundRecipe> recipes = m == null ? List.of() : ServerRecipes.lookup(m);
		if (recipes.isEmpty()) {
			fail(player, "Pocket List: no recipe found for " + e.name() + ".");
			return false;
		}
		if (recipes.size() == 1) {
			recipes.get(0).apply(list, e);
			say(player, "Swapped " + e.name() + " for what " + recipes.get(0).station() + " needs.");
			return true;
		}
		pending.put(player.getUniqueId(), new Pending(e.key(), recipes));
		offer(player, recipes, e.name());
		return false;
	}

	private void offer(Player player, List<FoundRecipe> recipes, String what) {
		player.sendMessage(Component.text(what + " is made more than one way. Pick one:", Sidebar.GOLD));
		for (int i = 0; i < recipes.size(); i++) {
			FoundRecipe r = recipes.get(i);
			player.sendMessage(Component.text(" ").append(ChatList.button(String.valueOf(i + 1), "/pocket pick " + (i + 1), "Use this recipe"))
					.append(Component.text(" " + r.station() + ": " + r.describe(), NamedTextColor.WHITE)));
		}
		player.sendMessage(Component.text(" ").append(ChatList.button("cancel", "/pocket pick cancel", "Leave the list as it is")));
	}

	private boolean pick(Player player, PocketListData list, String[] rest) {
		Pending p = pending.get(player.getUniqueId());
		if (p == null) {
			fail(player, "Pocket List: nothing to pick; /pocket breakdown <item> first.");
			return false;
		}
		if (rest.length > 0 && rest[0].equalsIgnoreCase("cancel")) {
			pending.remove(player.getUniqueId());
			say(player, "Left the list as it is.");
			return false;
		}
		int n = rest.length > 0 ? parseInt(rest[0], 0) : 0;
		if (n < 1 || n > p.recipes.size()) {
			fail(player, "Pocket List: pick 1 to " + p.recipes.size() + ", or cancel.");
			return false;
		}
		pending.remove(player.getUniqueId());
		FoundRecipe r = p.recipes.get(n - 1);
		if (p.key.startsWith("+")) {
			int crafts = parseInt(p.key.substring(1, p.key.indexOf('|')), 1);
			addIngredients(list, r, crafts);
			say(player, "Added what " + r.station() + " needs.");
			return true;
		}
		Entry e = list.find(p.key);
		if (e == null) {
			fail(player, "Pocket List: that line is gone.");
			return false;
		}
		Counter.apply(list, Counter.count(player));
		r.apply(list, e);
		say(player, "Swapped " + e.name() + " for what " + r.station() + " needs.");
		return true;
	}

	private static void addIngredients(PocketListData list, FoundRecipe r, int crafts) {
		for (Map.Entry<List<String>, Integer> ing : r.ingredients().entrySet()) list.topUp(ing.getKey(), ing.getValue() * crafts);
	}

	/** The item named by the words ("oak planks", "oak_planks", "minecraft:oak_planks"), or null. */
	static @Nullable Material material(String[] words) {
		if (words.length == 0) return null;
		Material m = Material.matchMaterial(Names.key(String.join(" ", words)));
		return m != null && m.isItem() && !m.isAir() ? m : null;
	}

	private static int parseInt(String s, int fallback) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static void help(CommandSender sender) {
		sender.sendMessage(Component.text("Pocket List: a gathering list that ticks itself off as you collect.", Sidebar.GOLD));
		for (String line : List.of(
				"/pocket  shows the list with its buttons",
				"/pocket add <item> [amount|stack]  adds an item (tab shows every item)",
				"/pocket add hand [amount|stack]  adds what you're holding",
				"/pocket add recipe <item> [stack]  adds what a recipe needs",
				"/pocket need <item> <amount>, /pocket remove <item>, /pocket clear [done]",
				"/pocket breakdown <item>  swaps a line for what makes it",
				"/pocket new [name], use <name>, next, prev, lists, rename <name>, delete",
				"/pocket hud on|off, /pocket hud lines <1-15>")) {
			sender.sendMessage(Component.text(line, NamedTextColor.GRAY));
		}
	}

	static void say(CommandSender to, String text) {
		to.sendMessage(Component.text(text, NamedTextColor.GRAY));
	}

	static void fail(CommandSender to, String text) {
		to.sendMessage(Component.text(text, NamedTextColor.RED));
	}
}
