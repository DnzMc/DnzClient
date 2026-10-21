package com.dnz.client;

/**
 * macOS Retina screens have 2x the pixels in each direction, so the game draws 4x as many pixels as on a
 * normal screen.
 */
public final class MacRetina {
	private static final int RETINA_HINT = 0x00023001;

	private MacRetina() {
	}

	public static boolean isMac() {
		return System.getProperty("os.name").toLowerCase().startsWith("mac");
	}

	/** Called right before the game window is created. */
	public static void applyHint() {
		if (!isMac()) {
			return;
		}
		try {
			Class.forName("org.lwjgl.glfw.GLFW").getMethod("glfwWindowHint", int.class, int.class)
				.invoke(null, RETINA_HINT, DnzConfig.get().macRetina ? 1 : 0);
		} catch (ReflectiveOperationException ignored) {
			// keep the default (Retina) if GLFW changed
		}
	}
}
