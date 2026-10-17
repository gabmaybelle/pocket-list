package com.gabbymaybelle.pocketlist;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * A small step-by-step in-game test for the Forge jar, since Forge has no client game test
 * framework like Fabric's. It only runs in the `clientTest` run (`./gradlew :forge:runClientTest`),
 * which sets the system property below; this source set is never in the jar players get.
 *
 * <p>Each step runs on the game's own thread, one or more ticks at a time. A failed check throws,
 * which crashes the game with a crash report and a non-zero exit code, so Gradle (and CI) fail.
 * When every step has passed, the game closes normally. A watchdog ends a run that hangs.
 */
@Mod.EventBusSubscriber(modid = PocketList.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
final class ClientTest {
	static final boolean ENABLED = Boolean.getBoolean(PocketList.MOD_ID + ".clienttest");
	/** A run that takes longer than this has hung (the game thread may be stuck, so a separate thread checks). */
	private static final long WATCHDOG_MILLIS = 10 * 60 * 1000;

	/** One step: returns true once it's done, and is called again next tick until then. */
	interface Step {
		boolean tick(Minecraft mc);
	}

	private static final Deque<Step> STEPS = new ArrayDeque<>();
	private static boolean started;
	private static boolean finished;

	private ClientTest() {
	}

	/** Runs once. */
	static void run(Consumer<Minecraft> action) {
		STEPS.add(mc -> {
			action.accept(mc);
			return true;
		});
	}

	/** Waits this many ticks. */
	static void waitTicks(int ticks) {
		int[] left = { ticks };
		STEPS.add(mc -> --left[0] <= 0);
	}

	/** Waits until the condition holds, failing after this many ticks. */
	static void waitUntil(String what, int timeoutTicks, Predicate<Minecraft> condition) {
		int[] waited = { 0 };
		STEPS.add(mc -> {
			if (condition.test(mc)) {
				return true;
			}
			if (++waited[0] > timeoutTicks) {
				throw new AssertionError("Timed out after " + timeoutTicks + " ticks waiting for: " + what);
			}
			return false;
		});
	}

	/** Runs a command on the singleplayer server as an operator, then gives it a few ticks to reach the client. */
	static void command(String command) {
		run(mc -> {
			IntegratedServer server = mc.getSingleplayerServer();
			if (server == null) {
				throw new AssertionError("No singleplayer server for the command: " + command);
			}
			server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
		});
		waitTicks(4);
	}

	/** Saves a screenshot in the run's screenshots folder (named by the time it was taken). */
	static void screenshot() {
		run(mc -> Screenshot.grab(mc.gameDirectory, mc.getMainRenderTarget(), message -> {}));
		waitTicks(2);
	}

	/**
	 * Presses and lets go of a key through the game's own keyboard handler, the way a player's key
	 * press arrives, so Forge's screen key events fire as they do in play.
	 */
	static void pressKey(String keyName, boolean shift) {
		run(mc -> {
			int key = InputConstants.getKey(keyName).getValue();
			int modifiers = shift ? GLFW.GLFW_MOD_SHIFT : 0;
			long window = mc.getWindow().getWindow();
			mc.keyboardHandler.keyPress(window, key, 0, GLFW.GLFW_PRESS, modifiers);
			mc.keyboardHandler.keyPress(window, key, 0, GLFW.GLFW_RELEASE, modifiers);
		});
		waitTicks(2);
	}

	/**
	 * Puts the mouse over a point in GUI coordinates and gives the screen a few frames to notice.
	 * There's no real mouse in the test, so the handler's position is set directly; screens and
	 * recipe viewers read it from there.
	 */
	static void moveMouse(int guiX, int guiY) {
		run(mc -> placeMouse(mc, guiX, guiY));
		waitTicks(2);
	}

	/** Moves the mouse there and clicks the left button through the game's own mouse handler. */
	static void click(int guiX, int guiY) {
		moveMouse(guiX, guiY);
		run(ClientTest::clickNow);
		waitTicks(2);
	}

	/** Adds a step of the test's own, for steps that need to look before deciding what to do. */
	static void add(Step step) {
		STEPS.add(step);
	}

	/** {@link #moveMouse} at once, for use inside a step. */
	static void placeMouse(Minecraft mc, int guiX, int guiY) {
		Window window = mc.getWindow();
		double perGuiX = (double) window.getScreenWidth() / window.getGuiScaledWidth();
		double perGuiY = (double) window.getScreenHeight() / window.getGuiScaledHeight();
		setField(mc.mouseHandler, MouseHandler.class, "xpos", (guiX + 0.5) * perGuiX);
		setField(mc.mouseHandler, MouseHandler.class, "ypos", (guiY + 0.5) * perGuiY);
	}

	/** A left click where the mouse is, at once, for use inside a step. */
	static void clickNow(Minecraft mc) {
		mouseButton(mc, true);
		mouseButton(mc, false);
	}

	/** Presses (or lets go of) the left button where the mouse is, for a click spread over frames. */
	static void mouseButton(Minecraft mc, boolean down) {
		Class<?>[] types = { long.class, int.class, int.class, int.class };
		invoke(mc.mouseHandler, MouseHandler.class, "onPress", types, mc.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT,
				down ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE, 0);
	}

	private static void invoke(Object owner, Class<?> declaring, String name, Class<?>[] types, Object... args) {
		try {
			Method method = declaring.getDeclaredMethod(name, types);
			method.setAccessible(true);
			method.invoke(owner, args);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Couldn't call " + declaring.getSimpleName() + "." + name, e);
		}
	}

	private static void setField(Object owner, Class<?> declaring, String name, Object value) {
		try {
			Field field = declaring.getDeclaredField(name);
			field.setAccessible(true);
			field.set(owner, value);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("No field " + name + " on " + declaring.getSimpleName(), e);
		}
	}

	/** Reads a field the test can't see otherwise, such as a container screen's position. */
	static Object field(Object owner, Class<?> declaring, String name) {
		try {
			Field field = declaring.getDeclaredField(name);
			field.setAccessible(true);
			return field.get(owner);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("No field " + name + " on " + declaring.getSimpleName(), e);
		}
	}

	/** Fails the run with this message unless the condition holds. */
	static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	/**
	 * Creates a fresh flat creative world with commands on, and waits until the player is in it. The
	 * folder gets the time in its name, so a run never reopens a world an earlier run left behind
	 * (in the same game folder, perhaps on another Minecraft version).
	 */
	static void createWorld(String baseName) {
		String name = baseName + "-" + System.currentTimeMillis();
		waitUntil("the title screen", 2400, mc -> mc.getOverlay() == null && mc.screen != null);
		run(mc -> mc.createWorldOpenFlows().createFreshLevel(name,
				new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
				new WorldOptions(1L, false, false),
				registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions()));
		waitUntil("the player in the new world", 2400, mc -> mc.player != null && mc.level != null && mc.screen == null);
		// Time for the chunks around the player to arrive and draw.
		waitTicks(60);
	}

	@SubscribeEvent
	static void onTick(TickEvent.ClientTickEvent event) {
		if (!ENABLED || finished || event.phase != TickEvent.Phase.END) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (!started) {
			started = true;
			// The window is rarely in front while the test runs; a pause would stop the world.
			mc.options.pauseOnLostFocus = false;
			PocketList.LOGGER.info("Pocket List: client test starting");
			Thread watchdog = new Thread(() -> {
				try {
					Thread.sleep(WATCHDOG_MILLIS);
				} catch (InterruptedException e) {
					return;
				}
				PocketList.LOGGER.error("Pocket List: client test hung for {} minutes; stopping", WATCHDOG_MILLIS / 60000);
				Runtime.getRuntime().halt(3);
			}, "Pocket List client test watchdog");
			watchdog.setDaemon(true);
			watchdog.start();
			PocketListClientTest.steps();
		}
		Step step = STEPS.peek();
		if (step == null) {
			finished = true;
			PocketList.LOGGER.info("Pocket List: client test passed, every check");
			mc.stop();
			return;
		}
		if (step.tick(mc)) {
			STEPS.poll();
		}
	}
}
