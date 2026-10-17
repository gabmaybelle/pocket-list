package com.gabbymaybelle.pocketlist.forge;

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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.items.IItemHandler;

/**
 * Forge's events, passed on to the shared code, and the settings button on the Mods screen. Client
 * only: {@link PocketListForge} loads this class on a client and never on a dedicated server.
 */
final class PocketListForgeClient {
	private PocketListForgeClient() {
	}

	static void init() {
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
				BlockEntity blockEntity = level.getBlockEntity(pos);
				if (blockEntity == null) {
					return null;
				}
				IItemHandler handler = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
				if (handler == null) {
					return null;
				}
				List<ItemStack> stacks = new ArrayList<>();
				for (int slot = 0; slot < handler.getSlots(); slot++) {
					ItemStack stack = handler.getStackInSlot(slot);
					if (!stack.isEmpty()) {
						stacks.add(stack.copy());
					}
				}
				return stacks;
			}
		});
		PocketList.init();

		IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
		modBus.addListener(PocketListForgeClient::keys);
		modBus.addListener(PocketListForgeClient::overlays);
		MinecraftForge.EVENT_BUS.addListener(PocketListForgeClient::tick);
		MinecraftForge.EVENT_BUS.addListener(PocketListForgeClient::screenInit);
		MinecraftForge.EVENT_BUS.addListener(PocketListForgeClient::screenClosing);
		MinecraftForge.EVENT_BUS.addListener(PocketListForgeClient::screenKey);
		MinecraftForge.EVENT_BUS.addListener(PocketListForgeClient::useBlock);
		MinecraftForge.EVENT_BUS.addListener(PocketListForgeClient::loggingIn);
		MinecraftForge.EVENT_BUS.addListener(PocketListForgeClient::loggingOut);
		// The Config button on Forge's Mods screen.
		ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
				() -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new PocketListSettingsScreen(parent)));
	}

	private static void keys(RegisterKeyMappingsEvent event) {
		event.register(PocketList.OPEN_KEY);
		event.register(PocketList.PIN_KEY);
		event.register(PocketList.HUD_KEY);
	}

	private static void overlays(RegisterGuiOverlaysEvent event) {
		event.registerAboveAll(PocketHud.ID.getPath(), (gui, graphics, partialTick, width, height) -> PocketHud.render(graphics, partialTick));
	}

	private static void tick(TickEvent.ClientTickEvent event) {
		if (event.phase == TickEvent.Phase.END) {
			Minecraft mc = Minecraft.getInstance();
			PocketList.onTick(mc);
			ChestMemory.onTick(mc);
		}
	}

	private static void screenInit(ScreenEvent.Init.Post event) {
		ChestMemory.onScreenInit(Minecraft.getInstance(), event.getScreen());
	}

	private static void screenClosing(ScreenEvent.Closing event) {
		ChestMemory.onScreenRemoved(event.getScreen());
	}

	private static void screenKey(ScreenEvent.KeyPressed.Pre event) {
		if (PocketList.onScreenKey(Minecraft.getInstance(), event.getScreen(), event.getKeyCode(), event.getScanCode(), event.getModifiers())) {
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
