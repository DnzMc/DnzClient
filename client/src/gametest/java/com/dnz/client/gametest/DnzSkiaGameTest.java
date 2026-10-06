package com.dnz.client.gametest;

import com.dnz.client.gui.DnzMenuScreen;
import com.dnz.client.skia.SkiaDemoScreen;
import com.dnz.client.skia.SkiaOverlay;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

/**
 * Skia menus experiment (only with -Pskia): FPS of the same world with no menu, the DNZ menu, and the Skia-drawn
 * menu; saves a picture of the Skia menu (dnz-skia.png). Results: dnz-skia.txt.
 */
public class DnzSkiaGameTest implements FabricClientGameTest {
	private static volatile boolean recording;
	private static long last;
	private static final long[] FRAMES = new long[200_000];
	private static int count;
	private static long skiaNanos;
	private static int skiaFrames;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (System.getProperty("dnz.skia", "").isEmpty()) {
			return;
		}
		LevelRenderEvents.START_MAIN.register(ctx -> {
			if (!recording) {
				return;
			}
			long now = System.nanoTime();
			if (last != 0 && count < FRAMES.length) {
				FRAMES[count++] = now - last;
				skiaNanos += SkiaOverlay.lastNanos;
				skiaFrames++;
			}
			last = now;
		});
		context.getInput().resizeWindow(1600, 900);
		context.runOnClient(mc -> {
			mc.options.renderDistance().set(8);
			mc.options.framerateLimit().set(260);
			mc.options.enableVsync().set(false);
			mc.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
		});
		List<String> report = new ArrayList<>();
		report.add("DNZ Skia menu test (1600x900, render distance 8)");
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false)
			.adjustSettings(ui -> ui.setSeed("20261001")).create()) {
			world.getServer().runCommand("time set 6000");
			context.waitTicks(400);
			report.add(measure(context, "No menu"));
			// New HUD modules: picture for checking (hud-new.png in the screenshots folder).
			context.runOnClient(mc -> {
				for (String id : new String[] {"minimap", "lookat", "crosshair", "direction", "tps", "entities", "chunks", "xp", "dimension", "frametime"}) {
					com.dnz.client.hud.DnzHud.module(id).setEnabled(true);
				}
				com.dnz.client.DnzConfig.get().crosshair.style = 1;
				com.dnz.client.DnzConfig.get().crosshair.color = 0xFF55FF55;
			});
			context.waitTicks(40);
			System.out.println("[DNZ SKIA] hud picture " + context.takeScreenshot("hud-new").toAbsolutePath());
			context.setScreen(() -> new net.minecraft.client.gui.screens.PauseScreen(true));
			context.waitTicks(5);
			System.out.println("[DNZ SKIA] pause picture " + context.takeScreenshot("pause-menu").toAbsolutePath());
			String opened = context.computeOnClient(mc -> {
				for (var w : net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(mc.gui.screen())) {
					if (w.getMessage().getString().equals("DNZ Settings") && w instanceof net.minecraft.client.gui.components.Button b) {
						b.onPress(new net.minecraft.client.input.KeyEvent(257, 0, 0));
						return mc.gui.screen().getClass().getSimpleName();
					}
				}
				return "no button";
			});
			context.waitTicks(10);
			System.out.println("[DNZ SKIA] DNZ Settings opens: " + opened + " then " + context.computeOnClient(mc -> String.valueOf(mc.gui.screen())));
			context.runOnClient(mc -> mc.gui.setScreen(new com.dnz.client.gui.DnzShiftScreen()));
			context.waitTicks(20);
			System.out.println("[DNZ SKIA] shift picture " + context.takeScreenshot("shift-screen").toAbsolutePath());
			context.runOnClient(mc -> mc.gui.setScreen(null));
			context.runOnClient(mc -> com.dnz.client.DnzConfig.get().nanoVg = false);
			compare(context, report, "menu-own", () -> DnzMenuScreen.open(null));
			context.runOnClient(mc -> com.dnz.client.DnzConfig.get().nanoVg = true);
			compare(context, report, "menu-nanovg", () -> DnzMenuScreen.open(null));
			context.runOnClient(mc -> mc.gui.setScreen(null));
			context.waitTicks(40);
			report.add(measure(context, "No menu again (game state still fine)"));
		}
		report.forEach(line -> System.out.println("[DNZ SKIA] " + line));
		try {
			Files.write(Path.of("dnz-skia.txt"), report);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/** Opens the demo screen, measures FPS and saves a picture (dnz-<name>.png). */
	private static void compare(ClientGameTestContext context, List<String> report, String name, java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> screen) {
		context.runOnClient(mc -> mc.gui.setScreen(screen.get()));
		context.waitTicks(60);
		skiaNanos = 0;
		skiaFrames = 0;
		SkiaOverlay.lastNanos = 0;
		report.add(measure(context, name + " menu"));
		report.add(String.format("   overlay time on the game's thread: %.2f ms per frame", skiaNanos / 1e6 / Math.max(1, skiaFrames)));
		context.runOnClient(mc -> {
			var window = mc.getWindow();
			int w = window.getWidth(), h = window.getHeight();
			ByteBuffer px = SkiaOverlay.readFrame(w, h);
			if (px != null) {
				BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
				for (int y = 0; y < h; y++) {
					for (int x = 0; x < w; x++) {
						int i = ((h - 1 - y) * w + x) * 4;
						img.setRGB(x, y, (px.get(i) & 255) << 16 | (px.get(i + 1) & 255) << 8 | (px.get(i + 2) & 255));
					}
				}
				try {
					javax.imageio.ImageIO.write(img, "png", Path.of("dnz-" + name.replace(' ', '-') + ".png").toFile());
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		});
	}

	private static String measure(ClientGameTestContext context, String label) {
		count = 0;
		last = 0;
		recording = true;
		context.waitTicks(400);
		recording = false;
		long[] f = Arrays.copyOf(FRAMES, count);
		Arrays.sort(f);
		double total = 0;
		for (long x : f) {
			total += x;
		}
		double avg = count / (total / 1e9);
		int n = Math.max(1, count / 100);
		double low = 0;
		for (int i = count - n; i < count; i++) {
			low += f[i];
		}
		low = n / (low / 1e9);
		return String.format("%-36s avg %6.0f FPS   1%% low %5.0f FPS   (%d frames)", label, avg, low, count);
	}
}
