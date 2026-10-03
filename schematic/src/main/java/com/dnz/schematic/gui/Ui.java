package com.dnz.schematic.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Small drawing helpers for the schematic menu. */
public final class Ui {
	private Ui() {
	}

	/** Rectangle with 2 px rounded corners. */
	public static void roundedRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		g.fill(x + 2, y, x + w - 2, y + h, color);
		g.fill(x, y + 2, x + 2, y + h - 2, color);
		g.fill(x + w - 2, y + 2, x + w, y + h - 2, color);
		g.fill(x + 1, y + 1, x + 2, y + 2, color);
		g.fill(x + w - 2, y + 1, x + w - 1, y + 2, color);
		g.fill(x + 1, y + h - 2, x + 2, y + h - 1, color);
		g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, color);
	}

	public static void roundedOutline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		g.fill(x + 2, y, x + w - 2, y + 1, color);
		g.fill(x + 2, y + h - 1, x + w - 2, y + h, color);
		g.fill(x, y + 2, x + 1, y + h - 2, color);
		g.fill(x + w - 1, y + 2, x + w, y + h - 2, color);
		g.fill(x + 1, y + 1, x + 2, y + 2, color);
		g.fill(x + w - 2, y + 1, x + w - 1, y + 2, color);
		g.fill(x + 1, y + h - 2, x + 2, y + h - 1, color);
		g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, color);
	}
}
