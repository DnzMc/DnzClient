package com.dnz.client.gui;

import com.dnz.client.DnzConfig;
import com.mojang.blaze3d.platform.Window;

/**
 * Size of the DNZ menu on the screen. The menu is laid out in its own units (the panel is always
 * {@link #PANEL_W} x {@link #PANEL_H}) and drawn scaled, so it keeps the same proportions at every window size,
 * resolution and Minecraft GUI scale: about two thirds of the window, like a desktop app. Small windows get a
 * bigger share so the text stays readable.
 */
public final class MenuScale {
	public static final int PANEL_W = 560;
	public static final int PANEL_H = 340;
	/** Room the menu needs on the screen: the panel, side margins and the credit line below it. */
	private static final int ROOM_W = PANEL_W + 16;
	private static final int ROOM_H = PANEL_H + 40;
	/** Below this many screen pixels per unit the small text gets hard to read. */
	private static final float READABLE = 1.25F;

	private MenuScale() {
	}

	public static int sizePercent() {
		return Math.max(70, Math.min(130, DnzConfig.get().menuSize));
	}

	/** Screen pixels per menu unit for the window as it is now. */
	public static float pixels(Window window) {
		float w = Math.max(1, window.getWidth());
		float h = Math.max(1, window.getHeight());
		float ideal = Math.min(w * 0.68F / PANEL_W, h * 0.72F / PANEL_H) * sizePercent() / 100.0F;
		float fit = Math.min(w / ROOM_W, h / ROOM_H);
		return Math.max(0.25F, Math.min(fit, Math.max(READABLE, ideal)));
	}

	/** Minecraft GUI units per menu unit: the scale the menu is drawn with. */
	public static float gui(Window window) {
		return pixels(window) / Math.max(1, window.getGuiScale());
	}
}
