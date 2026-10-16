package com.gabbymaybelle.pocketlist;

import com.mojang.blaze3d.platform.InputConstants;
import com.gabbymaybelle.pocketlist.compat.Viewers;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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

	/** The Controls screen's "Pocket List" group; each loader registers it. */
	public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(MOD_ID, "keys"));
	// The default keys are looked up by name, not with InputConstants.KEY_K: 26.3 renumbered every
	// key (K is 75 on 26.2 and 14 on 26.3), and those constants are copied into the jar when it's
	// compiled, so a jar built against 26.3 would bind nonsense keys on 26.2.
	public static final KeyMapping OPEN_KEY = new KeyMapping("key.pocket_list.open", InputConstants.getKey("key.keyboard.k").getValue(), CATEGORY);
	public static final KeyMapping PIN_KEY = new KeyMapping("key.pocket_list.pin", InputConstants.getKey("key.keyboard.z").getValue(), CATEGORY);
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
		if (ticks == 0) {
			repairKeys(mc);
		}
		while (openKey.consumeClick()) {
			if (mc.gui.screen() == null) {
				mc.gui.setScreen(new PocketListScreen(null));
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
	 * Version 0.1.0 saved its default keys as 26.3's numbers on 26.2, where they're no key at all
	 * and show in the options file as a bare number ("key.keyboard.14"). A real key always has a
	 * name there, so a bare number means one of those, and it's put back to the default once.
	 */
	private static void repairKeys(Minecraft mc) {
		boolean changed = false;
		for (KeyMapping key : new KeyMapping[] { OPEN_KEY, PIN_KEY }) {
			if (key.saveString().matches("key\\.keyboard\\.-?\\d+")) {
				key.setKey(key.getDefaultKey());
				changed = true;
			}
		}
		if (changed) {
			KeyMapping.resetMapping();
			mc.options.save();
			LOGGER.info("Pocket List: put the keys saved as unknown key numbers back to their defaults");
		}
	}

	/**
	 * A key pressed on a screen. The pin key works over items and recipes in inventories and in
	 * recipe viewers' screens, unless you're typing (in a search box, say). Returns true when
	 * the key pinned something, so the screen shouldn't also act on it.
	 */
	public static boolean onScreenKey(Minecraft mc, Screen screen, KeyEvent key) {
		return !(screen instanceof PocketListScreen) && pinKey.matches(key) && !Compat.typing(screen) && !Pins.typingInViewer()
				&& Pins.pinHovered(mc, screen, key.hasShiftDown() || mc.hasShiftDown());
	}

	/** Joined a world or server: a one-time hello, so a new player knows where to start. */
	public static void onJoin(Minecraft mc) {
		mc.execute(() -> {
			if (!Lists.data().welcomed) {
				Lists.data().welcomed = true;
				Lists.save();
				SystemToast.add(mc.gui.toastManager(), WELCOME,
						Component.translatable("pocket_list.welcome.title"),
						Component.translatable("pocket_list.welcome.text", openKey.getTranslatedKeyMessage()));
			}
		});
	}
}
