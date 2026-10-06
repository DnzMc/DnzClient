package com.dnz.client.gametest;

import com.dnz.client.DnzConfig;
import com.dnz.client.compat.Compat;
import com.dnz.client.gui.DnzModsScreen;
import com.dnz.client.gui.DnzVisualScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Screenshots of the menus in the Simple (clean, glass) style, with blur, and the DNZ style for comparison. */
public class DnzMenuGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (!System.getProperty("dnz.bench", "").isEmpty() || !System.getProperty("dnz.skia", "").isEmpty()) {
			return; // only the FPS benchmark runs
		}
		// One-time FPS defaults (DnzClient): VSync off, unlimited FPS, saved to options.txt.
		// (The test framework resets the live options before each test, so the saved file is checked.)
		String saved;
		try {
			saved = java.nio.file.Files.readString(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("options.txt"));
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
		boolean ok = DnzConfig.get().fpsDefaults == 1 && saved.contains("enableVsync:false") && saved.contains("maxFps:260");
		System.out.println("[DNZ TEST] fps defaults saved: " + ok);
		if (!ok) {
			throw new AssertionError("FPS defaults were not saved to options.txt");
		}
		String perfMods = String.join(", ", java.util.stream.Stream.of("lithium", "entityculling", "immediatelyfast", "ferritecore", "dynamic_fps", "badoptimizations")
			.filter(id -> net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(id)).toList());
		System.out.println("[DNZ TEST] performance mods loaded: " + perfMods);

		// Default (Modern) look: sunset picture, sharp logo, smooth button labels.
		style(context, false);
		shot(context, "dnz-0-title", net.minecraft.client.gui.screens.TitleScreen::new);
		shot(context, "dnz-0-singleplayer", () -> new net.minecraft.client.gui.screens.worldselection.SelectWorldScreen(null));

		style(context, true);
		context.runOnClient(mc -> mc.options.menuBackgroundBlurriness().set(6));
		// Title screen and server list: smooth labels, sharp "DNZ CLIENT" logo.
		shot(context, "simple-0-title", net.minecraft.client.gui.screens.TitleScreen::new);
		shot(context, "simple-0-multiplayer", () -> new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(null));

		// "Join" from the launcher: a note makes the game connect (to a closed port here, so it fails right away).
		boolean connecting = context.computeOnClient(mc -> {
			try {
				java.nio.file.Files.writeString(com.dnz.client.AutoJoin.noteFile(),
					"{\"name\":\"Test\",\"address\":\"127.0.0.1:1\",\"time\":" + System.currentTimeMillis() + "}");
			} catch (java.io.IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
			mc.gui.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
			return com.dnz.client.AutoJoin.tryJoin(mc, mc.gui.screen());
		});
		context.waitTicks(40);
		String after = context.computeOnClient(mc -> mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getSimpleName());
		boolean noteUsed = !java.nio.file.Files.exists(com.dnz.client.AutoJoin.noteFile());
		System.out.println("[DNZ TEST] auto join started=" + connecting + " note used=" + noteUsed + " screen after=" + after);
		if (!connecting || !noteUsed || after.equals("TitleScreen")) {
			throw new AssertionError("Auto join from the launcher did not start connecting");
		}
		context.setScreen(() -> null);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			shot(context, "simple-1-pause", () -> new net.minecraft.client.gui.screens.PauseScreen(true));
			shot(context, "simple-1-inventory", () -> new net.minecraft.client.gui.screens.inventory.InventoryScreen(net.minecraft.client.Minecraft.getInstance().player));
			shot(context, "simple-2-visual", () -> new DnzVisualScreen(null));
			shot(context, "simple-3-mods", () -> new DnzModsScreen(null));
			shot(context, "simple-4-options", () -> Compat.optionsScreen(null));
			style(context, false);
			shot(context, "dnz-pause", () -> new net.minecraft.client.gui.screens.PauseScreen(true));
			shot(context, "dnz-inventory", () -> new net.minecraft.client.gui.screens.inventory.InventoryScreen(net.minecraft.client.Minecraft.getInstance().player));
			// "DNZ Settings" in the ESC menu opens the DNZ menu.
			context.setScreen(() -> new net.minecraft.client.gui.screens.PauseScreen(true));
			context.waitTicks(5);
			String opened = context.computeOnClient(mc -> {
				for (var w : net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(mc.gui.screen())) {
					if (w.getMessage().getString().equals("DNZ Settings") && w instanceof net.minecraft.client.gui.components.Button b) {
						b.onPress(new net.minecraft.client.input.KeyEvent(257, 0, 0));
						return mc.gui.screen().getClass().getSimpleName();
					}
				}
				return "no button";
			});
			System.out.println("[DNZ TEST] DNZ Settings opens: " + opened);
			if (opened.equals("no button") || opened.equals("PauseScreen")) {
				throw new AssertionError("DNZ Settings button missing or did nothing: " + opened);
			}
			shot(context, "dnz-visual", () -> new DnzVisualScreen(null));
			shot(context, "dnz-performance", () -> new com.dnz.client.gui.DnzPerformanceTab(null));
			shot(context, "dnz-mods", () -> new DnzModsScreen(null));
			shot(context, "dnz-skin", () -> new com.dnz.client.gui.DnzSkinScreen(null));
			shot(context, "dnz-changelog", () -> new com.dnz.client.gui.DnzChangelogTab(null));
			shot(context, "dnz-feedback", () -> new com.dnz.client.gui.DnzFeedbackTab(null));
			shot(context, "dnz-module-coords", () -> new com.dnz.client.gui.DnzModuleScreen(null, com.dnz.client.hud.DnzHud.modules().stream().filter(m -> m.id.equals("coords")).findFirst().orElseThrow()));

			// DNZ Config: the menu keeps its proportions at every window size and GUI scale.
			context.getInput().resizeWindow(1920, 1080);
			context.waitTicks(5);
			shot(context, "dnz-1080p-mods", () -> new com.dnz.client.gui.DnzHudTab(null));
			guiScale(context, 2);
			shot(context, "dnz-1080p-gui2-mods", () -> new com.dnz.client.gui.DnzHudTab(null));
			guiScale(context, 0);
			shot(context, "dnz-1080p-visual", () -> new DnzVisualScreen(null));
			// A dropdown list unfolded over the rows.
			context.runOnClient(mc -> mc.gui.screen().children().forEach(child -> {
				if (child instanceof com.dnz.client.gui.config.OptionList list) {
					list.openChoice("Style");
				}
			}));
			context.waitTicks(10);
			System.out.println("[DNZ TEST] screenshot " + context.takeScreenshot("dnz-1080p-dropdown").toAbsolutePath());
			shot(context, "dnz-1080p-performance", () -> new com.dnz.client.gui.DnzPerformanceTab(null));
			shot(context, "dnz-1080p-module", () -> new com.dnz.client.gui.DnzModuleScreen(null, com.dnz.client.hud.DnzHud.modules().stream().filter(m -> m.id.equals("fps")).findFirst().orElseThrow()));
			// Themes: Modern white, Minecraft black and white.
			look(context, 0, true);
			shot(context, "dnz-1080p-white-visual", () -> new DnzVisualScreen(null));
			shot(context, "dnz-1080p-white-mods", () -> new com.dnz.client.gui.DnzHudTab(null));
			shot(context, "dnz-1080p-white-performance", () -> new com.dnz.client.gui.DnzPerformanceTab(null));
			look(context, 1, false);
			shot(context, "dnz-1080p-minecraft-visual", () -> new DnzVisualScreen(null));
			shot(context, "dnz-1080p-minecraft-mods", () -> new com.dnz.client.gui.DnzHudTab(null));
			shot(context, "dnz-1080p-minecraft-performance", () -> new com.dnz.client.gui.DnzPerformanceTab(null));
			look(context, 1, true);
			shot(context, "dnz-1080p-minecraft-white-visual", () -> new DnzVisualScreen(null));
			shot(context, "dnz-1080p-minecraft-white-module", () -> new com.dnz.client.gui.DnzModuleScreen(null, com.dnz.client.hud.DnzHud.modules().stream().filter(m -> m.id.equals("fps")).findFirst().orElseThrow()));
			look(context, 0, false);
			context.getInput().resizeWindow(2560, 1440);
			context.waitTicks(5);
			shot(context, "dnz-1440p-mods", () -> new com.dnz.client.gui.DnzHudTab(null));
			guiScale(context, 2);
			shot(context, "dnz-1440p-gui2-mods", () -> new com.dnz.client.gui.DnzHudTab(null));
			guiScale(context, 0);
			context.getInput().resizeWindow(854, 480);
			context.waitTicks(5);
			context.setScreen(() -> null);
		}
	}

	private static void guiScale(ClientGameTestContext context, int scale) {
		context.runOnClient(mc -> {
			mc.options.guiScale().set(scale);
			mc.resizeGui();
		});
		context.waitTicks(2);
	}

	private static void look(ClientGameTestContext context, int look, boolean light) {
		context.runOnClient(mc -> {
			DnzConfig.get().menuLook = look;
			DnzConfig.get().menuLight = light;
		});
	}

	private static void style(ClientGameTestContext context, boolean simple) {
		context.runOnClient(mc -> {
			DnzConfig config = DnzConfig.get();
			config.simpleStyle = simple;
			config.javaStyle = false;
		});
	}

	private static void shot(ClientGameTestContext context, String name, java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> screen) {
		context.setScreen(screen);
		context.waitTicks(10);
		System.out.println("[DNZ TEST] screenshot " + context.takeScreenshot(name).toAbsolutePath());
	}
}
