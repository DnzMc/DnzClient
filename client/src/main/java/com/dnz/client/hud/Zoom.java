package com.dnz.client.hud;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** Hold the zoom key (C by default) to zoom in; scroll while zooming to change the amount. */
public final class Zoom {
	private static final float DEFAULT_LEVEL = 4.0F;
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("dnzclient", "main"));
	public static final KeyMapping KEY = new KeyMapping("key.dnzclient.zoom", InputConstants.KEY_C, CATEGORY);

	private static float level = DEFAULT_LEVEL;
	private static float current = 1.0F;
	private static long lastFrame;

	private Zoom() {
	}

	public static boolean active() {
		Minecraft mc = Minecraft.getInstance();
		HudModule module = DnzHud.module("zoom");
		return mc.player != null && mc.gui.screen() == null && KEY.isDown() && (module == null || module.enabled());
	}

	/** FOV multiplier for this frame, smoothly animated. */
	public static float fovMultiplier() {
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 0.0F : Math.min(0.1F, (now - lastFrame) / 1.0E9F);
		lastFrame = now;
		boolean zooming = active();
		if (!zooming) {
			level = DEFAULT_LEVEL;
		}
		float target = zooming ? 1.0F / level : 1.0F;
		current += (target - current) * Math.min(1.0F, dt * 14.0F);
		if (Math.abs(current - target) < 0.001F) {
			current = target;
		}
		return current;
	}

	/** Mouse sensitivity multiplier so aiming stays controllable while zoomed. */
	public static double sensitivity() {
		return current;
	}

	public static void scroll(double amount) {
		level = Math.max(1.5F, Math.min(50.0F, level * (amount > 0 ? 1.25F : 0.8F)));
	}
}
