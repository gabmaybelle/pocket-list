package com.gabbymaybelle.pocketlist;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * One line on a list: how many of something you want. The something is a single item, an
 * item tag ("any planks"), or a few items where any of them will do.
 */
public final class Entry {
	/** An item tag such as "minecraft:planks", or null. When set, it wins over {@link #items}. */
	public String tag;
	/** Item ids; any of them counts. Usually just one. */
	public List<String> items = new ArrayList<>();
	public int need;

	/** How many you have, worked out by {@link Counter}. */
	transient int have;
	/** Until when (in millis) the HUD highlights this line, after it was just added to. */
	transient long flashUntil;
	/** Parsed once: the tag, or the items. */
	private transient TagKey<Item> tagKey;
	private transient Set<Item> itemSet;

	public static Entry ofItem(Item item, int need) {
		Entry entry = new Entry();
		entry.items.add(BuiltInRegistries.ITEM.getKey(item).toString());
		entry.need = need;
		return entry;
	}

	public static Entry ofTag(TagKey<Item> tag, int need) {
		Entry entry = new Entry();
		entry.tag = tag.location().toString();
		entry.need = need;
		return entry;
	}

	public static Entry ofItems(List<Item> items, int need) {
		if (items.size() == 1) {
			return ofItem(items.getFirst(), need);
		}
		Entry entry = new Entry();
		for (Item item : items) {
			entry.items.add(BuiltInRegistries.ITEM.getKey(item).toString());
		}
		entry.need = need;
		return entry;
	}

	Entry copyWithNeed(int need) {
		Entry copy = new Entry();
		copy.tag = tag;
		copy.items = new ArrayList<>(items);
		copy.need = need;
		return copy;
	}

	/** Two entries with the same key are the same line, so adding one tops up the other. */
	public String key() {
		return tag != null ? "#" + tag : String.join("|", items);
	}

	public int have() {
		return have;
	}

	public boolean done() {
		return have >= need;
	}

	public boolean matches(ItemStack stack) {
		return !stack.isEmpty() && matches(stack.getItem());
	}

	public boolean matches(Item item) {
		if (tag != null) {
			TagKey<Item> key = tagKey();
			return key != null && item.builtInRegistryHolder().is(key);
		}
		if (itemSet == null) {
			itemSet = new HashSet<>(options());
		}
		return itemSet.contains(item);
	}

	/** The item to look up recipes for: the line's own item, or the first that fits. */
	public Item mainItem() {
		List<Item> options = options();
		return options.isEmpty() ? null : options.getFirst();
	}

	/** What to show: the item, or for tags and choices, each option in turn, a second apiece. */
	public ItemStack icon() {
		List<Item> options = options();
		if (options.isEmpty()) {
			return new ItemStack(Items.BARRIER);
		}
		return new ItemStack(options.get((int) (Util.getMillis() / 1000 % options.size())));
	}

	public Component name() {
		if (tag != null) {
			Identifier id = Identifier.tryParse(tag);
			String words = (id == null ? tag : id.getPath()).replace('/', ' ').replace('_', ' ').toLowerCase(Locale.ROOT);
			return Component.translatable("pocket_list.any", words);
		}
		List<Item> options = options();
		if (options.isEmpty()) {
			return Component.literal(items.isEmpty() ? "?" : items.getFirst());
		}
		Component first = new ItemStack(options.getFirst()).getHoverName();
		return options.size() == 1 ? first : Component.translatable("pocket_list.or_similar", first);
	}

	/** The largest stack of this entry's first item, for shift-click amounts. */
	public int stackSize() {
		List<Item> options = options();
		return options.isEmpty() ? 64 : new ItemStack(options.getFirst()).getMaxStackSize();
	}

	List<Item> options() {
		List<Item> out = new ArrayList<>();
		if (tag != null) {
			TagKey<Item> key = tagKey();
			if (key != null) {
				BuiltInRegistries.ITEM.get(key).map(HolderSet::stream).ifPresent(s -> s.map(Holder::value).forEach(out::add));
			}
			return out;
		}
		for (String id : items) {
			Identifier parsed = Identifier.tryParse(id);
			if (parsed != null) {
				BuiltInRegistries.ITEM.getOptional(parsed).ifPresent(out::add);
			}
		}
		return out;
	}

	private TagKey<Item> tagKey() {
		if (tagKey == null) {
			Identifier id = Identifier.tryParse(tag);
			tagKey = id == null ? null : TagKey.create(Registries.ITEM, id);
		}
		return tagKey;
	}
}
