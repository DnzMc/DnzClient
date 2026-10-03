package com.dnz.client.gui;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * Look of the DNZ menu ("DNZ Config"): dark glass colors, Poppins text in four weights and the shared
 * controls (switch, slider track, option rows). Everything is drawn with smooth shapes and fonts, no pixel art.
 */
public final class Ui {
	/** Page and sidebar glass, see-through over the blurred game. */
	public static final int PAGE_BG = 0xD811171C;
	public static final int SIDEBAR_BG = 0xCC151C22;
	/** Chips, cards and option rows. */
	public static final int CHIP_BG = 0xC2232D32;
	public static final int CARD_BG = 0x66232D32;
	public static final int CARD_HOVER = 0x8A2B363D;
	public static final int COMPONENT_BG = 0xFF1A2229;
	public static final int COMPONENT_HOVER = 0xFF222C35;
	public static final int BORDER = 0x1AFFFFFF;
	public static final int TEXT = 0xFFD5DBFF;
	public static final int TEXT_2 = 0xFF8A8E9A;
	public static final int TEXT_OFF = 0xFF6E7482;
	public static final int TRACK = 0xFF5B5F69;

	/** Screen pixels per menu unit while the menu draws (set by DnzMenuScreen). */
	public static float unitPx = 2;

	private Ui() {
	}

	/**
	 * The size text at [scale] really gets: the same in the Modern look; in the Minecraft look a whole number of
	 * screen pixels per font pixel (the pixel font is put on whole pixels when drawn, see TextSmoothMixin).
	 */
	public static float scaled(float scale) {
		if (!Theme.smoothAll || !Theme.minecraftLook()) {
			return scale;
		}
		return Math.max(1, Math.round(scale * unitPx)) / unitPx;
	}

	public static Component regular(String text) {
		return font(text, Theme.UI_REGULAR);
	}

	public static Component medium(String text) {
		return font(text, Theme.UI_MEDIUM);
	}

	public static Component semibold(String text) {
		return font(text, Theme.UI_SEMIBOLD);
	}

	private static Component font(String text, FontDescription font) {
		return Component.literal(text).withStyle(style -> style.withFont(font));
	}

	/** [text] in [font], cut with "…" so that it is at most [room] wide when drawn at [scale]. */
	public static Component fit(String text, FontDescription font, float room, float scale) {
		Component whole = font(text, font);
		Font measure = Minecraft.getInstance().font;
		int max = (int) (room / scaled(scale));
		if (measure.width(whole) <= max) {
			return whole;
		}
		Component dots = font("…", font);
		String head = measure.getSplitter().plainHeadByWidth(text, Math.max(0, max - measure.width(dots)), Style.EMPTY.withFont(font));
		return font(head.stripTrailing() + "…", font);
	}

	/** Text at [scale] with its top-left corner at x, y. */
	public static void text(GuiGraphicsExtractor g, Component text, float x, float y, float scale, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.text(Minecraft.getInstance().font, text, 0, 0, color, false);
		g.pose().popMatrix();
	}

	public static void centered(GuiGraphicsExtractor g, Component text, float x, float y, float scale, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.centeredText(Minecraft.getInstance().font, text, 0, 0, color);
		g.pose().popMatrix();
	}

	public static int width(Component text, float scale) {
		return Math.round(Minecraft.getInstance().font.width(text) * scaled(scale));
	}

	/** Glass box with a thin light edge (cards, option rows, inputs). */
	public static void glass(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int fill) {
		Smooth.rect(g, x, y, w, h, radius, BORDER);
		Smooth.rect(g, x + 0.75F, y + 0.75F, w - 1.5F, h - 1.5F, Math.max(0, radius - 0.75F), fill);
	}

	/** On/off switch: the track fills with the accent color, the white knob slides right ([on] and [hover] are 0..1). */
	public static void switchControl(GuiGraphicsExtractor g, float x, float y, float w, float h, float on, float hover) {
		switchControl(g, x, y, w, h, on, hover, true);
	}

	/** Switch that is grayed out while it can't be used ([active] false). */
	public static void switchControl(GuiGraphicsExtractor g, float x, float y, float w, float h, float on, float hover, boolean active) {
		int accent = active ? Theme.menuAccent() : 0xFF4A5060;
		Smooth.rect(g, x, y, w, h, h / 2, Theme.lerp(Theme.lerp(active ? TRACK : 0xFF3A3F49, 0xFF6C717C, hover), accent, on));
		float k = h - 4;
		float kx = x + 2 + (w - h) * on;
		Smooth.shadow(g, kx, y + 2.3F, k, k, k / 2, 2, 0x50000000);
		Smooth.rect(g, kx, y + 2, k, k, k / 2, active ? Palette.KEEP_WHITE : 0xFF9BA1AD);
	}

	/** Slider: thin track, accent part up to the value, white round knob ([value] 0..1). */
	public static void slider(GuiGraphicsExtractor g, float x, float y, float w, float h, float value, boolean active, float hover) {
		int accent = active ? Theme.menuAccent() : 0xFF5B5F69;
		float ty = y + h / 2 - 1.5F;
		Smooth.rect(g, x, ty, w, 3, 1.5F, active ? TRACK : 0xFF41454E);
		Smooth.rect(g, x, ty, Math.max(3, w * value), 3, 1.5F, accent);
		float k = 8 + hover * 1.5F;
		float kx = x + w * value - k / 2;
		if (hover > 0.01F && active) {
			// Soft accent halo around the knob while the mouse is on the slider.
			Smooth.shadow(g, kx, y + (h - k) / 2, k, k, k / 2, 3 * hover, Theme.withAlpha(accent & 0xFFFFFF, 0.45F * hover));
		}
		Smooth.shadow(g, kx, y + (h - k) / 2 + 0.5F, k, k, k / 2, 2, 0x60000000);
		Smooth.rect(g, kx, y + (h - k) / 2, k, k, k / 2, active ? Palette.KEEP_WHITE : 0xFF9BA1AD);
	}

	/** One rounded stroke centered at (cx, cy), turned by [degrees]: the building block of vector marks. */
	private static void stroke(GuiGraphicsExtractor g, float cx, float cy, float length, float thick, float degrees, int color) {
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().rotate((float) Math.toRadians(degrees));
		Smooth.rect(g, -length / 2, -thick / 2, length, thick, thick / 2, color);
		g.pose().popMatrix();
	}

	/** A "v" arrow (or "^" when [up]) of [size] units centered at (cx, cy), drawn as smooth shapes. */
	public static void chevron(GuiGraphicsExtractor g, float cx, float cy, float size, boolean up, int color) {
		float half = size * 0.3F;
		float thick = Math.max(0.9F, size * 0.17F);
		float length = half * 1.4142F + thick * 0.5F;
		float turn = up ? -45 : 45;
		stroke(g, cx - half / 2, cy, length, thick, turn, color);
		stroke(g, cx + half / 2, cy, length, thick, -turn, color);
	}

	/** A check mark of [size] units centered at (cx, cy). */
	public static void check(GuiGraphicsExtractor g, float cx, float cy, float size, int color) {
		float thick = Math.max(0.9F, size * 0.16F);
		float c = 0.7071F;
		float tipX = cx - size * 0.12F;
		float tipY = cy + size * 0.22F;
		float shortLeg = size * 0.3F;
		float longLeg = size * 0.62F;
		stroke(g, tipX - shortLeg / 2 * c, tipY - shortLeg / 2 * c, shortLeg + thick * 0.5F, thick, 45, color);
		stroke(g, tipX + longLeg / 2 * c, tipY - longLeg / 2 * c, longLeg + thick * 0.5F, thick, -45, color);
	}

	/** A small help box next to the mouse (smooth text on dark glass), kept inside the screen. */
	public static void tooltip(GuiGraphicsExtractor g, Component text, int mouseX, int mouseY, int screenW, int screenH) {
		Font font = Minecraft.getInstance().font;
		float s = 0.72F;
		List<FormattedCharSequence> lines = font.split(text, (int) (190 / s));
		if (lines.isEmpty()) {
			return;
		}
		int textW = 0;
		for (FormattedCharSequence line : lines) {
			textW = Math.max(textW, font.width(line));
		}
		float w = textW * s + 12;
		float h = (lines.size() * 10 - 2) * s + 10;
		float x = mouseX + 10;
		float y = mouseY + 12;
		if (x + w > screenW - 4) {
			x = mouseX - 8 - w;
		}
		if (y + h > screenH - 4) {
			y = mouseY - 6 - h;
		}
		x = Math.max(4, x);
		y = Math.max(4, y);
		Smooth.shadow(g, x, y + 2, w, h, 4, 8, 0x70000000);
		Smooth.rect(g, x - 0.75F, y - 0.75F, w + 1.5F, h + 1.5F, 4.75F, 0x2EFFFFFF);
		Smooth.rect(g, x, y, w, h, 4, 0xF5161C23);
		g.pose().pushMatrix();
		g.pose().translate(x + 6, y + 5.5F);
		g.pose().scale(s, s);
		int ly = 0;
		for (FormattedCharSequence line : lines) {
			g.text(font, line, 0, ly, 0xFFD9DEE7, false);
			ly += 10;
		}
		g.pose().popMatrix();
	}
}
