package com.gabbymaybelle.pocketlist;

import com.mojang.blaze3d.platform.InputConstants;
import com.gabbymaybelle.pocketlist.compat.Viewers;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pocket List: a little checklist of things to gather. Everything happens on your side, so it
 * works on any server without the server needing it.
 *
 * <p>The Fabric and NeoForge jars each have a small entry class that sets {@link Platform},
 * calls {@link #init()}, registers the keys, the HUD and the screens below, and passes the
 * loader's events on to the handlers here and in {@link ChestMemory}.
 */
public final class PocketList {
	public static final String MOD_ID = "pocket_list";
	public static final Logger LOGGER = LoggerFactory.getLogger("Pocket List");
	/** Recount twice a second; plenty for a checklist, and cheap. */
	private static final int COUNT_EVERY = 10;

	/** The Controls screen's "Pocket List" group. */
	public static final String CATEGORY = "key.category.pocket_list.keys";
	public static final KeyMapping OPEN_KEY = new KeyMapping("key.pocket_list.open", InputConstants.KEY_K, CATEGORY);
	public static final KeyMapping PIN_KEY = new KeyMapping("key.pocket_list.pin", InputConstants.KEY_Z, CATEGORY);
	public static final KeyMapping HUD_KEY = new KeyMapping("key.pocket_list.toggle_hud", InputConstants.UNKNOWN.getValue(), CATEGORY);
	static KeyMapping openKey = OPEN_KEY;
	static KeyMapping pinKey = PIN_KEY;
	static KeyMapping hudKey = HUD_KEY;
	private static int ticks;
	static final SystemToast.SystemToastId WELCOME = new SystemToast.SystemToastId(10000L);
	static final SystemToast.SystemToastId FINISHED = new SystemToast.SystemToastId(5000L);

	private PocketList() {
	}

	/** Loads the lists and looks for recipe viewers. Called once, after {@link Platform#set}. */
	public static void init() {
		Lists.load();
		Viewers.init();
	}

	/** Every client tick: the keys, and a recount twice a second. */
	public static void onTick(Minecraft mc) {
		while (openKey.consumeClick()) {
			if (mc.screen == null) {
				mc.setScreen(new PocketListScreen(null));
			}
		}
		while (hudKey.consumeClick()) {
			Lists.data().hud = !Lists.data().hud;
			Lists.save();
		}
		if (++ticks % COUNT_EVERY == 0) {
			Counter.update(mc);
		}
	}

	/**
	 * A key pressed on a screen. The pin key works over items and recipes in inventories and in
	 * recipe viewers' screens, unless you're typing (in a search box, say). Returns true when
	 * the key pinned something, so the screen shouldn't also act on it.
	 */
	public static boolean onScreenKey(Minecraft mc, Screen screen, int keyCode, int scanCode, int modifiers) {
		return !(screen instanceof PocketListScreen) && pinKey.matches(keyCode, scanCode) && !Compat.typing(screen) && !Pins.typingInViewer()
				&& Pins.pinHovered(mc, screen, (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 || Screen.hasShiftDown());
	}

	/** Joined a world or server: a one-time hello, so a new player knows where to start. */
	public static void onJoin(Minecraft mc) {
		mc.execute(() -> {
			if (!Lists.data().welcomed) {
				Lists.data().welcomed = true;
				Lists.save();
				SystemToast.add(mc.getToasts(), WELCOME,
						Component.translatable("pocket_list.welcome.title"),
						Component.translatable("pocket_list.welcome.text", openKey.getTranslatedKeyMessage()));
			}
		});
	}
}
