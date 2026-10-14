package com.gabbymaybelle.pocketlist;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.effect.MobEffectInstance;

/** The active list in the top-left corner: one line per thing, still-to-gather first. */
public final class PocketHud {
	private static final int MARGIN = 4;
	private static final int PADDING = 3;
	private static final int LINE = 17;
	/** Half of the vanilla hotbar's width (182 GUI pixels, centred at the bottom), with a little spare. */
	private static final int HOTBAR_HALF_WIDTH = 93;
	/** The hotbar, hearts, hunger and experience bar together reach about this far up from the bottom. */
	private static final int HOTBAR_BLOCK = 56;
	private static final int BACKDROP = 0x90000000;
	private static final int FLASH = 0x60FFFFFF;
	private static final int TITLE = 0xFFFFDD77;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int DONE = 0xFF77DD77;
	private static final int MUTED = 0xFFAAAAAA;

	private PocketHud() {
	}

	static void register() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(PocketList.MOD_ID, "list"), PocketHud::extract);
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		Lists.PocketListData list = Lists.active();
		if (!Lists.data().hud || list.entries.isEmpty() || mc.player == null
				|| mc.getDebugOverlay().showDebugScreen() || mc.gui.screen() instanceof PocketListScreen) {
			return;
		}
		Font font = mc.font;
		List<Entry> entries = new ArrayList<>(list.entries);
		// Still to gather first; the list's own order otherwise.
		entries.sort(Comparator.comparing(Entry::done));
		long gathered = entries.stream().filter(Entry::done).count();
		boolean allDone = gathered == entries.size();

		int maxLines = Math.max(1, Lists.data().hudLines);
		int shown = Math.min(entries.size(), maxLines);
		int hidden = entries.size() - shown;

		Component title = allDone
				? Component.translatable("pocket_list.hud.all_done", list.name)
				: Component.translatable("pocket_list.hud.title", list.name, gathered, entries.size());
		List<Component> lines = new ArrayList<>();
		int width = font.width(title);
		for (int i = 0; i < shown; i++) {
			Component line = line(entries.get(i));
			lines.add(line);
			width = Math.max(width, 18 + font.width(line));
		}
		Component more = hidden > 0 ? Component.translatable("pocket_list.hud.more", hidden) : null;
		if (more != null) {
			width = Math.max(width, font.width(more));
		}

		int height = 10 + shown * LINE + (more != null ? 10 : 0);
		int boxWidth = width + PADDING * 2;
		int boxHeight = height + PADDING * 2;
		Lists.HudPosition position = Lists.data().hudPosition;
		int x = position.right() ? graphics.guiWidth() - MARGIN - boxWidth : MARGIN;
		int y = !position.top() ? graphics.guiHeight() - MARGIN - boxHeight : position.right() ? belowEffects(mc) : MARGIN;
		if (!position.top() && x < graphics.guiWidth() / 2 + HOTBAR_HALF_WIDTH && x + boxWidth > graphics.guiWidth() / 2 - HOTBAR_HALF_WIDTH) {
			// On a narrow screen a bottom corner would run into the hotbar and the bars above it: sit above them.
			y -= HOTBAR_BLOCK;
		}
		graphics.fill(x, y, x + boxWidth, y + boxHeight, BACKDROP);
		x += PADDING;
		y += PADDING;
		graphics.text(font, title, x, y, allDone ? DONE : TITLE, true);
		y += 10;
		long now = Util.getMillis();
		for (int i = 0; i < shown; i++) {
			Entry entry = entries.get(i);
			if (entry.flashUntil > now) {
				graphics.fill(x - 1, y, x + width + 1, y + LINE - 1, FLASH);
			}
			graphics.item(entry.icon(), x, y);
			graphics.text(font, lines.get(i), x + 18, y + 4, entry.done() ? DONE : TEXT, true);
			y += LINE;
		}
		if (more != null) {
			graphics.text(font, more, x, y + 1, MUTED, true);
		}
	}

	/** "24/64 Oak Planks". */
	private static Component line(Entry entry) {
		return Component.translatable("pocket_list.hud.line", Math.min(entry.have(), entry.need), entry.need, entry.name());
	}

	/** Height of a row of vanilla effect icons, including the gap below it. */
	private static final int EFFECT_ROW = 26;

	/**
	 * In the top-right corner, the list sits below the effect icons vanilla draws there:
	 * beneficial effects in a row at y 1-25, harmful ones in a second row at y 27-51 (both 15
	 * lower in the demo). The icons are hidden while a screen that lists effects itself is open.
	 */
	static int belowEffects(Minecraft mc) {
		Screen screen = mc.gui.screen();
		if (screen != null && screen.showsActiveEffects()) {
			return MARGIN;
		}
		boolean beneficial = false;
		boolean harmful = false;
		for (MobEffectInstance effect : mc.player.getActiveEffects()) {
			if (effect.showIcon()) {
				if (effect.getEffect().value().isBeneficial()) {
					beneficial = true;
				} else {
					harmful = true;
				}
			}
		}
		int top = mc.isDemo() ? 16 : 1;
		return harmful ? top + EFFECT_ROW * 2 + MARGIN - 1 : beneficial ? top + EFFECT_ROW + MARGIN - 1 : MARGIN;
	}
}
