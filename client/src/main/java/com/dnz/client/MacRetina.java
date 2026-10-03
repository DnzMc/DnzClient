package com.dnz.client;

/**
 * macOS Retina screens have 2x the pixels in each direction, so the game draws 4x as many pixels as on a normal
 * screen. With the Retina setting off (default), the game window asks for a normal-resolution framebuffer:
 * the biggest single FPS gain on a Mac. Takes effect when the game starts.
 */
public final class MacRetina {
	/** GLFW_COCOA_RETINA_FRAMEBUFFER (an alias of GLFW_SCALE_FRAMEBUFFER in GLFW 3.4). */
	private static final int RETINA_HINT = 0x00023001;

	private MacRetina() {
	}

	public static boolean isMac() {
		return System.getProperty("os.name").toLowerCase().startsWith("mac");
	}

	/** Called right before the game window is created. GLFW is reached by name (it is not on the compile path). */
	public static void applyHint() {
		if (!isMac()) {
			return;
		}
		try {
			Class.forName("org.lwjgl.glfw.GLFW").getMethod("glfwWindowHint", int.class, int.class)
				.invoke(null, RETINA_HINT, DnzConfig.get().macRetina ? 1 : 0);
		} catch (ReflectiveOperationException ignored) {
			// Keep the default (Retina) if GLFW changed.
		}
	}
}
