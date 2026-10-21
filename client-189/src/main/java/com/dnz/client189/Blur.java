package com.dnz.client189;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.ResourceLocation;

/** The light blur behind DNZ screens (Minecraft 1.8.9's own blur shader). */
public final class Blur {
	private static boolean active;

	private Blur() {
	}

	public static void on() {
		Minecraft mc = Minecraft.getMinecraft();
		if (active || mc.theWorld == null || !OpenGlHelper.shadersSupported || mc.entityRenderer.isShaderActive()) {
			return;
		}
		try {
			mc.entityRenderer.loadShader(new ResourceLocation("shaders/post/blur.json"));
			active = true;
		} catch (Exception ignored) {
		}
	}

	/** Off when the screen closes, unless another DNZ screen opens right away (it turns it on again). */
	public static void off(Minecraft mc) {
		if (active) {
			mc.entityRenderer.stopUseShader();
			active = false;
		}
	}
}
