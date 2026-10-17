package com.gabbymaybelle.pocketlist;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Counts what you have towards each line of the active list. */
public final class Counter {
	/** Shulker boxes and bundles are looked inside, and bundles inside those, but no deeper. */
	private static final int MAX_DEPTH = 2;

	private Counter() {
	}

	static void update(Minecraft mc) {
		Player player = mc.player;
		Lists.PocketListData list = Lists.active();
		List<Entry> entries = list.entries;
		if (player == null || entries.isEmpty()) {
			return;
		}
		Map<Item, Integer> totals = new HashMap<>();
		Inventory inventory = player.getInventory();
		for (ItemStack stack : inventory.items) {
			add(stack, totals, 0);
		}
		add(player.getOffhandItem(), totals, 0);
		AbstractContainerMenu menu = player.containerMenu;
		add(menu.getCarried(), totals, 0);
		if (mc.screen instanceof AbstractContainerScreen<?> screen && screen.getMenu() == menu
				&& !(screen instanceof CreativeModeInventoryScreen)) {
			// The chest, crafting grid or whatever else is open in front of you (not the creative
			// item picker: those items aren't really yours).
			for (Slot slot : menu.slots) {
				if (!(slot.container instanceof Inventory) && !(slot instanceof ResultSlot)) {
					add(slot.getItem(), totals, 0);
				}
			}
		}
		if (Lists.data().countChests) {
			ChestMemory.nearby(mc, totals);
		}
		boolean finished = true;
		for (Entry entry : entries) {
			int have = 0;
			for (Map.Entry<Item, Integer> total : totals.entrySet()) {
				if (entry.matches(total.getKey())) {
					have += total.getValue();
				}
			}
			entry.have = have;
			finished &= entry.done();
		}
		if (finished && Boolean.FALSE.equals(list.wasFinished)) {
			celebrate(mc, list);
		}
		list.wasFinished = finished;
	}

	/** A short "all gathered" moment, once, when the last line is ticked off. */
	private static void celebrate(Minecraft mc, Lists.PocketListData list) {
		mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.4F, 0.6F));
		PocketList.toast(mc, PocketList.FINISHED_MILLIS, Component.translatable("pocket_list.finished.title"),
				Component.translatable("pocket_list.finished.text", list.name));
	}

	/** Adds a stack, and what's inside shulker boxes and bundles, to the totals. */
	static void add(ItemStack stack, Map<Item, Integer> into, int depth) {
		if (stack.isEmpty()) {
			return;
		}
		into.merge(stack.getItem(), stack.getCount(), Integer::sum);
		if (depth >= MAX_DEPTH) {
			return;
		}
		// 1.20.1 keeps what's inside in the item's NBT: a shulker box (or any block item carrying
		// its block's contents) under BlockEntityTag.Items, a bundle under Items.
		CompoundTag blockEntity = BlockItem.getBlockEntityData(stack);
		if (blockEntity != null) {
			addAll(blockEntity.getList("Items", Tag.TAG_COMPOUND), into, depth);
		}
		if (stack.getItem() instanceof BundleItem && stack.getTag() != null) {
			addAll(stack.getTag().getList("Items", Tag.TAG_COMPOUND), into, depth);
		}
	}

	private static void addAll(ListTag items, Map<Item, Integer> into, int depth) {
		for (int i = 0; i < items.size(); i++) {
			add(ItemStack.of(items.getCompound(i)), into, depth + 1);
		}
	}
}
