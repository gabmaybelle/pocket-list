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
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * A small step-by-step in-game test for the NeoForge jar, since NeoForge has no client game test
 * framework like Fabric's. It only runs in the `clientTest` run (`./gradlew :neoforge:runClientTest`),
 * which sets the system property below; this source set is never in the jar players get.
 *
 * <p>Each step runs on the game's own thread, one or more ticks at a time. A failed check throws,
 * which crashes the game with a crash report and a non-zero exit code, so Gradle (and CI) fail.
 * When every step has passed, the game closes normally. A watchdog ends a run that hangs.
 */
@EventBusSubscriber(modid = PocketList.MOD_ID, value = Dist.CLIENT)
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
		run(mc -> Screenshot.grab(mc, false));
		waitTicks(2);
	}

	/**
	 * Presses and lets go of a key through the game's own keyboard handler, the way a player's key
	 * press arrives, so NeoForge's screen key events fire as they do in play. The handler method is
	 * private on 26.2 and public on 26.3, with the same arguments, so it's called by reflection.
	 */
	static void pressKey(String keyName, boolean shift) {
		run(mc -> {
			// Looked up while the game runs: 26.3 renumbered keys and modifiers (Z is 90 on 26.2 and 29
			// on 26.3), and a constant written here would be compiled in with 26.3's number.
			int key = InputConstants.getKey(keyName).getValue();
			KeyEvent event = new KeyEvent(key, 0, shift ? modShift() : 0);
			long window = mc.getWindow().handle();
			invoke(mc.keyboardHandler, KeyboardHandler.class, "keyPress", new Class<?>[] { long.class, int.class, KeyEvent.class }, window, GLFW_PRESS, event);
			invoke(mc.keyboardHandler, KeyboardHandler.class, "keyPress", new Class<?>[] { long.class, int.class, KeyEvent.class }, window, GLFW_RELEASE, event);
		});
		waitTicks(2);
	}

	/**
	 * Puts the mouse over a point in GUI coordinates and gives the screen a few frames to notice.
	 * There's no real mouse in the test, so the handler's position is set directly (the same field
	 * on 26.2 and 26.3); screens and recipe viewers read it from there.
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
		MouseButtonInfo left = new MouseButtonInfo(inputConstant("MOUSE_BUTTON_LEFT"), 0);
		Class<?>[] types = { long.class, MouseButtonInfo.class, int.class };
		invoke(mc.mouseHandler, MouseHandler.class, "onButton", types, mc.getWindow().handle(), left, down ? GLFW_PRESS : GLFW_RELEASE);
	}

	/** Key and button actions, as the game's handlers take them (the same on 26.2 and 26.3). */
	private static final int GLFW_RELEASE = 0, GLFW_PRESS = 1;

	/** The Shift modifier bit of the running version (1 on 26.2, 3 on 26.3). */
	private static int modShift() {
		return inputConstant("MOD_SHIFT");
	}

	/**
	 * An input number of the running version, read while the game runs. 26.3 renumbered keys, buttons
	 * and modifiers (the left button is 0 on 26.2 and 1 on 26.3), and a constant written in the code
	 * would be compiled in with 26.3's number.
	 */
	private static int inputConstant(String name) {
		try {
			return InputConstants.class.getField(name).getInt(null);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("No InputConstants." + name, e);
		}
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
		waitUntil("the title screen", 2400, mc -> mc.gui.overlay() == null && mc.gui.screen() != null);
		run(mc -> mc.createWorldOpenFlows().createFreshLevel(name,
				new LevelSettings(name, GameType.CREATIVE, new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true,
						WorldDataConfiguration.DEFAULT),
				new WorldOptions(1L, false, false),
				registries -> registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
				mc.gui.screen()));
		waitUntil("the player in the new world", 2400, mc -> mc.player != null && mc.level != null && mc.gui.screen() == null);
		// Time for the chunks around the player to arrive and draw.
		waitTicks(60);
	}

	@SubscribeEvent
	static void onTick(ClientTickEvent.Post event) {
		if (!ENABLED || finished) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (!started) {
			started = true;
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
