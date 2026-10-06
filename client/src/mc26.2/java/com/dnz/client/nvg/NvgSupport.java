package com.dnz.client.nvg;

import com.mojang.blaze3d.systems.RenderSystem;

/** NanoVG menus on Minecraft 26.2: available when the game draws with OpenGL (not in Vulkan mode). */
public final class NvgSupport {
	private static Boolean openGl;

	private NvgSupport() {
	}

	public static boolean supported() {
		if (openGl == null) {
			try {
				openGl = RenderSystem.getDevice().getDeviceInfo().backendName().toLowerCase(java.util.Locale.ROOT).contains("opengl");
				Class.forName("org.lwjgl.nanovg.NanoVGGL3");
			} catch (Throwable e) {
				openGl = false; // NanoVG not bundled or no OpenGL
			}
		}
		return openGl;
	}
}
