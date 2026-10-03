package com.dnz.client;

import com.dnz.client.gui.DnzButton;
import com.dnz.client.gui.DnzMenuScreen;
import com.dnz.client.gui.DnzVisualScreen;
import com.dnz.client.gui.SharpFonts;
import com.dnz.client.hud.CombatTracker;
import com.dnz.client.hud.DnzHud;
import com.dnz.client.hud.FoodOverlay;
import com.dnz.client.hud.HudModule;
import net.minecraft.client.Minecraft;
import com.dnz.client.hud.Zoom;
import com.dnz.client.script.ScriptManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class DnzClient implements ClientModInitializer {
	/** Opens the DNZ menu (Right Shift by default). */
	public static final KeyMapping MENU_KEY = new KeyMapping("key.dnzclient.menu", InputConstants.KEY_RSHIFT, Zoom.CATEGORY);

	@Override
	public void onInitializeClient() {
		DnzConfig.get();
		ScriptManager.init();
		KeyMappingHelper.registerKeyMapping(Zoom.KEY);
		KeyMappingHelper.registerKeyMapping(MENU_KEY);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("dnzclient", "hud"), DnzHud::render);
		CombatTracker.register();
		FoodOverlay.register();
		com.dnz.client.hud.ShulkerPreview.register();
		com.dnz.client.hud.CleanHotbar.register();
		AutoJoin.register();

		// Once per install: VSync off and no FPS cap (they hold FPS back and add input delay in PvP).
		// After that the player's own choice is kept.
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			DnzConfig config = DnzConfig.get();
			if (config.fpsDefaults < 1) {
				client.options.enableVsync().set(false);
				// On a Mac the FPS cap from the launcher's AUTO (the screen's refresh rate) stays: unlimited FPS only
				// heats a MacBook until it slows itself down.
				if (!MacRetina.isMac()) {
					client.options.framerateLimit().set(260); // 260 = "Unlimited"
				}
				client.options.save();
				config.fpsDefaults = 1;
				config.save();
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (MENU_KEY.consumeClick()) {
				if (client.gui.screen() == null) {
					client.gui.setScreen(DnzMenuScreen.open(null));
				}
			}
			CombatTracker.tick(client);
			toggleSprint(client);
			// Our fonts follow the window's pixel size (resized window, new GUI scale, menu closed).
			SharpFonts.update(client, client.gui.screen());
		});

		// Add a "Visual" tab button to the vanilla Options screen.
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof DnzMenuScreen)) {
				SharpFonts.update(client, screen); // the DNZ menu does this itself, at its own scale
			}
			if (screen instanceof OptionsScreen) {
				Screens.getWidgets(screen).add(new DnzButton(6, 6, 64, 20, Component.literal("Visual"),
					() -> client.gui.setScreen(new DnzVisualScreen(screen))));
			}
		});
	}

	private static boolean sprintHeld;

	/** Auto sprint module: sprinting stays on while moving forward (same as holding the sprint key). */
	private static void toggleSprint(Minecraft client) {
		HudModule module = DnzHud.module("togglesprint");
		boolean on = module != null && module.enabled() && client.player != null;
		if (on) {
			client.options.keySprint.setDown(true);
			sprintHeld = true;
		} else if (sprintHeld) {
			// Let go once when the module is turned off.
			client.options.keySprint.setDown(false);
			sprintHeld = false;
		}
	}
}
