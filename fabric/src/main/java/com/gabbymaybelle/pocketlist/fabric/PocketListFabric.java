package com.gabbymaybelle.pocketlist.fabric;

import com.gabbymaybelle.pocketlist.ChestMemory;
import com.gabbymaybelle.pocketlist.Platform;
import com.gabbymaybelle.pocketlist.PocketHud;
import com.gabbymaybelle.pocketlist.PocketList;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Fabric's way in: Fabric API's events, passed on to the shared code. */
public class PocketListFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Platform.set(new Platform.Hooks() {
			@Override
			public Path configDir() {
				return FabricLoader.getInstance().getConfigDir();
			}

			@Override
			public boolean isModLoaded(String id) {
				return FabricLoader.getInstance().isModLoaded(id);
			}

			@Override
			public List<ItemStack> blockItems(Level level, BlockPos pos) {
				Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, pos, null);
				if (storage == null) {
					return null;
				}
				List<ItemStack> stacks = new ArrayList<>();
				for (StorageView<ItemVariant> view : storage) {
					if (!view.isResourceBlank() && view.getAmount() > 0) {
						stacks.add(view.getResource().toStack((int) Math.min(view.getAmount(), Integer.MAX_VALUE)));
					}
				}
				return stacks;
			}
		});
		PocketList.init();

		KeyMapping.Category.register(PocketList.CATEGORY.id());
		KeyMappingHelper.registerKeyMapping(PocketList.OPEN_KEY);
		KeyMappingHelper.registerKeyMapping(PocketList.PIN_KEY);
		KeyMappingHelper.registerKeyMapping(PocketList.HUD_KEY);

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			PocketList.onTick(mc);
			ChestMemory.onTick(mc);
		});
		ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
			ChestMemory.onScreenInit(mc, screen);
			ScreenEvents.remove(screen).register(ChestMemory::onScreenRemoved);
			ScreenKeyboardEvents.allowKeyPress(screen).register((current, key) -> !PocketList.onScreenKey(mc, current, key));
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			ChestMemory.onUseBlock(level, hit.getBlockPos());
			return InteractionResult.PASS;
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
			PocketList.onJoin(mc);
			ChestMemory.onJoin(mc);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> ChestMemory.onDisconnect());
		HudElementRegistry.addLast(PocketHud.ID, PocketHud::extract);
	}
}
