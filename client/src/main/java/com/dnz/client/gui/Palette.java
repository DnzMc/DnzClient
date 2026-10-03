package com.dnz.client.gui;

/**
 * Colors of the DNZ menu in the chosen theme. The menu is designed dark; while it draws ({@link Theme#smoothAll}),
 * every color goes through {@link #map}: the White theme turns the grays around (dark becomes light, light text
 * becomes dark) and the Minecraft look takes the color out (black and white). Colored parts keep their color.
 */
public final class Palette {
	/** Always white (switch and slider knobs). */
	public static final int KEEP_WHITE = 0xFFFFFFFE;
	/** Text and icons drawn on the accent color: white, or black where the accent is light (Minecraft look, dark). */
	public static final int ON_ACCENT = 0xFFFFFFFD;

	private Palette() {
	}

	public static int map(int c) {
		boolean menu = Theme.smoothAll;
		boolean mono = menu && Theme.minecraftLook();
		boolean light = menu && Theme.lightMenu();
		if (c == KEEP_WHITE) {
			return 0xFFFFFFFF;
		}
		if (c == ON_ACCENT) {
			return mono && !light ? 0xFF141414 : 0xFFFFFFFF;
		}
		if (!mono && !light) {
			return c;
		}
		int a = c >>> 24;
		int r = c >> 16 & 0xFF;
		int g = c >> 8 & 0xFF;
		int b = c & 0xFF;
		int lum = (r * 77 + g * 150 + b * 29) >> 8;
		if (lum < 24 && a < 0xC0) {
			// Shadows and dimming stay dark (softer on the White theme).
			return light ? (a / 2) << 24 | (c & 0xFFFFFF) : c;
		}
		boolean colored = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) > 60;
		if (colored) {
			if (mono) {
				// Black and white: the accent becomes a light gray on black, a dark gray on white.
				int v = light ? 0x3A : 0xC8;
				return a << 24 | v * 0x010101;
			}
			// Light colored text would be hard to read on white.
			return lum > 140 ? a << 24 | scale(r, 0.7F) << 16 | scale(g, 0.7F) << 8 | scale(b, 0.7F) : c;
		}
		if (mono) {
			r = g = b = lum;
		}
		if (light) {
			int shift = 255 - 2 * lum;
			r = clamp(r + shift);
			g = clamp(g + shift);
			b = clamp(b + shift);
		}
		return a << 24 | r << 16 | g << 8 | b;
	}

	private static int scale(int channel, float by) {
		return Math.round(channel * by);
	}

	private static int clamp(int channel) {
		return Math.max(18, Math.min(255, channel));
	}
}
