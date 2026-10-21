package com.dnz.client189.engine;

import com.dnz.client189.Module;
import com.dnz.client189.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

/**
 * FPS test of DNZ Engine (only when started with DNZ_BENCH=on or DNZ_BENCH=off; never in normal play): makes
 * a normal world, times how long the chunks take to appear, then fills the view with dropped items, signs,
 * armor stands and particles and averages the FPS for 20 seconds.
 */
public final class Bench {
	private static int ticks;
	private static int settle;
	private static int stage;
	private static long joined;
	private static int quietSeconds;
	private static long lastSecond;
	private static int lastBusySecond;
	private static long fpsSum;
	private static int fpsCount;

	private Bench() {
	}

	public static String mode() {
		return System.getenv("DNZ_BENCH");
	}

	/** Before the settings are read: the "off" run uses plain Minecraft behaviour everywhere. */
	public static boolean vanilla() {
		return "off".equals(mode());
	}

	/** "noregions": DNZ Engine on, but chunks drawn the old way (to measure the region drawing alone). */
	public static boolean vanillaRenderer() {
		return "noregions".equals(mode());
	}

	public static void tick(Minecraft mc) {
		if (mode() == null) {
			return;
		}
		ticks++;
		Module engine = Modules.get("engine");
		if (engine != null && engine.enabled() == vanilla()) {
			engine.setEnabled(!vanilla());
		}
		if (stage == 0 && ticks == 60) {
			fixedWindow(mc);
			mc.gameSettings.enableVsync = false;
			org.lwjgl.opengl.Display.setVSyncEnabled(false);
			mc.gameSettings.limitFramerate = 260;
			mc.gameSettings.renderDistanceChunks = 12;
			mc.gameSettings.useVbo = true;
			WorldSettings settings = new WorldSettings(20261017L, WorldSettings.GameType.CREATIVE, true, false, WorldType.DEFAULT).enableCommands();
			mc.launchIntegratedServer("dnz-bench", "dnz-bench", settings);
			stage = 1;
		} else if (stage == 1 && mc.theWorld != null && mc.thePlayer != null && ++settle > 300) {
			// 15 seconds in the world (spawn chunks generated), then every chunk is built again and timed
			mc.renderGlobal.loadRenderers();
			joined = System.currentTimeMillis();
			lastSecond = joined;
			stage = 2;
		} else if (stage == 2) {
			// Chunk loading: done when no chunk was built for 3 seconds in a row
			if (System.currentTimeMillis() - lastSecond >= 1000) {
				lastSecond += 1000;
				int updates = chunkUpdates(mc);
				int second = (int) ((lastSecond - joined) / 1000);
				if (updates > 0) {
					lastBusySecond = second;
					quietSeconds = 0;
				} else if (++quietSeconds >= 3 || second > 90) {
					System.out.println("DNZ BENCH chunks-loaded-seconds=" + lastBusySecond + " engine=" + !vanilla());
					stage = 3;
					ticks = 0;
				}
			}
		} else if (stage == 3) {
			// the scene: items, signs and armor stands in front of the player, particles every second
			if (ticks == 1) {
				mc.thePlayer.sendChatMessage("/gamerule doDaylightCycle false");
				mc.thePlayer.sendChatMessage("/tp @p ~ 130 ~ 0 35");
			}
			if (ticks >= 5 && ticks < 45) {
				int row = ticks - 5;
				for (int col = -10; col <= 10; col++) {
					mc.thePlayer.sendChatMessage("/summon Item ~" + col + " ~-4 ~" + (8 + row) + " {Item:{id:minecraft:stone,Count:64},Age:-32768,PickupDelay:32767}");
				}
				if (row % 2 == 0) {
					for (int col = -10; col <= 10; col += 2) {
						mc.thePlayer.sendChatMessage("/setblock ~" + col + " ~-5 ~" + (8 + row) + " standing_sign");
						mc.thePlayer.sendChatMessage("/summon ArmorStand ~" + col + " ~-5 ~" + (9 + row) + " {CustomName:\"DNZ\",CustomNameVisible:1,NoGravity:1}");
					}
				}
			}
			// Buried armor stands: hidden by the entity culling (F3 shows how many)
			if (ticks >= 45 && ticks < 55) {
				for (int col = -5; col < 5; col++) {
					mc.thePlayer.sendChatMessage("/summon ArmorStand ~" + col * 2 + " ~-25 ~" + (ticks - 45) * 2 + " {NoGravity:1}");
				}
			}
			if (ticks == 380) {
				mc.gameSettings.showDebugInfo = true;
			}
			if (ticks == 400) {
				net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.mcDataDir, "dnz-bench-f3.png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
				System.out.println("DNZ BENCH culled-entities=" + Occlusion.lastHiddenEntities + " culled-tiles=" + Occlusion.lastHiddenTiles);
			}
			if (ticks >= 60 && ticks % 20 == 0) {
				mc.thePlayer.sendChatMessage("/particle explode ~ ~-3 ~20 8 3 8 0.02 3000");
			}
			if (ticks == 100) {
				fpsSum = 0;
				fpsCount = 0;
			}
			if (ticks > 100 && ticks % 20 == 0) {
				fpsSum += Minecraft.getDebugFPS();
				fpsCount++;
			}
			if (ticks == 100 + 20 * 20) {
				System.out.println("DNZ BENCH avg-fps=" + (fpsCount == 0 ? 0 : fpsSum / fpsCount) + " engine=" + !vanilla());
				mc.shutdown();
			}
		}
	}

	private static void fixedWindow(Minecraft mc) {
		try {
			org.lwjgl.opengl.Display.setDisplayMode(new org.lwjgl.opengl.DisplayMode(1280, 720));
			for (java.lang.reflect.Method m : Minecraft.class.getDeclaredMethods()) {
				if ((m.getName().equals("resize") || m.getName().equals("func_71370_a")) && m.getParameterTypes().length == 2) {
					m.setAccessible(true);
					m.invoke(mc, 1280, 720);
				}
			}
		} catch (Exception e) {
			System.out.println("DNZ BENCH window size not set: " + e);
		}
	}

	private static int chunkUpdates(Minecraft mc) {
		String debug = mc.debug;
		int open = debug.indexOf('(');
		int end = debug.indexOf(" chunk update");
		if (open < 0 || end < open) {
			return 0;
		}
		try {
			return Integer.parseInt(debug.substring(open + 1, end).trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
