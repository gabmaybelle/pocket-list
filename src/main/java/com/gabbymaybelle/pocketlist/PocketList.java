package com.gabbymaybelle.pocketlist;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import com.gabbymaybelle.pocketlist.compat.Viewers;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pocket List: a little checklist of things to gather. Everything happens on your side, so it
 * works on any server without the server needing it.
 */
public final class PocketList implements ClientModInitializer {
	public static final String MOD_ID = "pocket_list";
	public static final Logger LOGGER = LoggerFactory.getLogger("Pocket List");
	/** Recount twice a second; plenty for a checklist, and cheap. */
	private static final int COUNT_EVERY = 10;

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "keys"));
	static KeyMapping openKey;
	static KeyMapping pinKey;
	static KeyMapping hudKey;
	private static int ticks;
	static final SystemToast.SystemToastId WELCOME = new SystemToast.SystemToastId(10000L);
	static final SystemToast.SystemToastId FINISHED = new SystemToast.SystemToastId(5000L);

	@Override
	public void onInitializeClient() {
		Lists.load();
		openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.pocket_list.open", InputConstants.KEY_K, CATEGORY));
		pinKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.pocket_list.pin", InputConstants.KEY_Z, CATEGORY));
		hudKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.pocket_list.toggle_hud", InputConstants.UNKNOWN.getValue(), CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
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
		});

		// The pin key works over items and recipes in inventories and in recipe viewers' screens,
		// unless you're typing (in a search box, say).
		ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
			if (!(screen instanceof PocketListScreen)) {
				ScreenKeyboardEvents.allowKeyPress(screen).register((current, key) ->
						!(pinKey.matches(key) && !Compat.typing(current) && !Pins.typingInViewer()
								&& Pins.pinHovered(mc, current, key.hasShiftDown() || mc.hasShiftDown())));
			}
		});

		// A one-time hello, so a new player knows where to start.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> mc.execute(() -> {
			if (!Lists.data().welcomed) {
				Lists.data().welcomed = true;
				Lists.save();
				SystemToast.add(mc.gui.toastManager(), WELCOME,
						Component.translatable("pocket_list.welcome.title"),
						Component.translatable("pocket_list.welcome.text", openKey.getTranslatedKeyMessage()));
			}
		}));

		Viewers.init();
		ChestMemory.register();
		PocketHud.register();
	}
}
