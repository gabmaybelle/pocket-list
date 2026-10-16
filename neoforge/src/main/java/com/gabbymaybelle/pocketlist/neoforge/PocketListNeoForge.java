package com.gabbymaybelle.pocketlist.neoforge;

import com.gabbymaybelle.pocketlist.ChestMemory;
import com.gabbymaybelle.pocketlist.Platform;
import com.gabbymaybelle.pocketlist.PocketHud;
import com.gabbymaybelle.pocketlist.PocketList;
import com.gabbymaybelle.pocketlist.PocketListSettingsScreen;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * NeoForge's way in: NeoForge's events, passed on to the shared code, and the settings button on
 * the Mods screen. Client only (dist = CLIENT), so a dedicated server never loads this class.
 */
@Mod(value = PocketList.MOD_ID, dist = Dist.CLIENT)
public class PocketListNeoForge {
	public PocketListNeoForge(IEventBus modBus, ModContainer container) {
		Platform.set(new Platform.Hooks() {
			@Override
			public Path configDir() {
				return FMLPaths.CONFIGDIR.get();
			}

			@Override
			public boolean isModLoaded(String id) {
				return ModList.get().isLoaded(id);
			}

			@Override
			public List<ItemStack> blockItems(Level level, BlockPos pos) {
				ResourceHandler<ItemResource> handler = level.getCapability(Capabilities.Item.BLOCK, pos, null);
				if (handler == null) {
					return null;
				}
				List<ItemStack> stacks = new ArrayList<>();
				for (int slot = 0; slot < handler.size(); slot++) {
					ItemResource resource = handler.getResource(slot);
					int amount = handler.getAmountAsInt(slot);
					if (!resource.isEmpty() && amount > 0) {
						stacks.add(resource.toStack(amount));
					}
				}
				return stacks;
			}
		});
		PocketList.init();

		modBus.addListener(PocketListNeoForge::keys);
		modBus.addListener(PocketListNeoForge::layers);
		NeoForge.EVENT_BUS.addListener(PocketListNeoForge::tick);
		NeoForge.EVENT_BUS.addListener(PocketListNeoForge::screenInit);
		NeoForge.EVENT_BUS.addListener(PocketListNeoForge::screenClosing);
		NeoForge.EVENT_BUS.addListener(PocketListNeoForge::screenKey);
		NeoForge.EVENT_BUS.addListener(PocketListNeoForge::useBlock);
		NeoForge.EVENT_BUS.addListener(PocketListNeoForge::loggingIn);
		NeoForge.EVENT_BUS.addListener(PocketListNeoForge::loggingOut);
		container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new PocketListSettingsScreen(parent));
	}

	private static void keys(RegisterKeyMappingsEvent event) {
		event.registerCategory(PocketList.CATEGORY);
		event.register(PocketList.OPEN_KEY);
		event.register(PocketList.PIN_KEY);
		event.register(PocketList.HUD_KEY);
	}

	private static void layers(RegisterGuiLayersEvent event) {
		event.registerAboveAll(PocketHud.ID, PocketHud::extract);
	}

	private static void tick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		PocketList.onTick(mc);
		ChestMemory.onTick(mc);
	}

	private static void screenInit(ScreenEvent.Init.Post event) {
		ChestMemory.onScreenInit(Minecraft.getInstance(), event.getScreen());
	}

	private static void screenClosing(ScreenEvent.Closing event) {
		ChestMemory.onScreenRemoved(event.getScreen());
	}

	private static void screenKey(ScreenEvent.KeyPressed.Pre event) {
		if (PocketList.onScreenKey(Minecraft.getInstance(), event.getScreen(), event.getKeyEvent())) {
			event.setCanceled(true);
		}
	}

	private static void useBlock(PlayerInteractEvent.RightClickBlock event) {
		ChestMemory.onUseBlock(event.getLevel(), event.getPos());
	}

	private static void loggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
		Minecraft mc = Minecraft.getInstance();
		PocketList.onJoin(mc);
		ChestMemory.onJoin(mc);
	}

	private static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
		ChestMemory.onDisconnect();
	}
}
