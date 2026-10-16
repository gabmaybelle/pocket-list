package com.gabbymaybelle.pocketlist;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

/**
 * Pocket List's settings: the HUD (on or off, which corner, how many lines) and whether nearby
 * chests count. Built from vanilla option widgets, so it looks like the game's own options.
 * Changes show straight away and are saved when the screen closes.
 */
public class PocketListSettingsScreen extends OptionsSubScreen {
	public PocketListSettingsScreen(Screen parent) {
		super(parent, Minecraft.getInstance().options, Component.translatable("pocket_list.options.title"));
	}

	@Override
	protected void addOptions() {
		Lists.Data data = Lists.data();
		list.addSmall(
				toggle("hud", data.hud, v -> data.hud = v),
				choice("hudPosition", Lists.HudPosition.values(), data.hudPosition, v -> data.hudPosition = v),
				slider("hudLines", data.hudLines, 1, Lists.MAX_HUD_LINES, v -> data.hudLines = v),
				toggle("countChests", data.countChests, v -> data.countChests = v));
	}

	@Override
	public void removed() {
		super.removed();
		Lists.save();
	}

	private static OptionInstance<Boolean> toggle(String key, boolean value, Consumer<Boolean> set) {
		return OptionInstance.createBoolean("pocket_list.options." + key,
				OptionInstance.cachedConstantTooltip(Component.translatable("pocket_list.options." + key + ".tooltip")),
				value, set::accept);
	}

	private static OptionInstance<Integer> slider(String key, int value, int min, int max, Consumer<Integer> set) {
		return new OptionInstance<>("pocket_list.options." + key,
				OptionInstance.cachedConstantTooltip(Component.translatable("pocket_list.options." + key + ".tooltip")),
				(caption, v) -> Component.translatable("options.generic_value", caption, Component.literal(Integer.toString(v))),
				new OptionInstance.IntRange(min, max), value, set::accept);
	}

	/** A button that cycles through an enum's values, each named "pocket_list.options.<key>.<value>". */
	private static <E extends Enum<E>> OptionInstance<E> choice(String key, E[] values, E value, Consumer<E> set) {
		String prefix = "pocket_list.options." + key;
		return new OptionInstance<>(prefix,
				OptionInstance.cachedConstantTooltip(Component.translatable(prefix + ".tooltip")),
				// The button adds the caption itself, so this is just the value.
				(caption, v) -> Component.translatable(prefix + "." + v.name().toLowerCase(Locale.ROOT)),
				new OptionInstance.Enum<>(List.of(values), Codec.INT.xmap(i -> values[i], Enum::ordinal)),
				value, set::accept);
	}
}
