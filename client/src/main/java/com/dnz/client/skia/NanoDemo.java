package com.dnz.client.skia;

import static org.lwjgl.nanovg.NanoVG.*;

import java.io.InputStream;
import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;

/** Comparison: the same DNZ menu layout as SkiaDemo, drawn by NanoVG. */
public final class NanoDemo {
	private static ByteBuffer regular;
	private static ByteBuffer semibold;
	private static boolean fontsMade;
	private static final NVGColor COLOR = NVGColor.create();
	private static final NVGColor COLOR2 = NVGColor.create();
	private static final NVGPaint PAINT = NVGPaint.create();

	private NanoDemo() {
	}

	private static ByteBuffer file(String name) {
		try (InputStream in = NanoDemo.class.getResourceAsStream("/assets/dnzclient/font/" + name)) {
			byte[] b = in.readAllBytes();
			ByteBuffer buf = BufferUtils.createByteBuffer(b.length);
			buf.put(b).flip();
			return buf;
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static NVGColor rgba(NVGColor c, int argb) {
		return nvgRGBA((byte) (argb >> 16), (byte) (argb >> 8), (byte) argb, (byte) (argb >>> 24), c);
	}

	private static void rrect(long vg, float x, float y, float w, float h, float r, int argb) {
		nvgBeginPath(vg);
		nvgRoundedRect(vg, x, y, w, h, r);
		nvgFillColor(vg, rgba(COLOR, argb));
		nvgFill(vg);
	}

	/** Soft shadow under a rounded box (NanoVG's box gradient). */
	private static void shadow(long vg, float x, float y, float w, float h, float r, float blur, float dy, int argb) {
		nvgBoxGradient(vg, x, y + dy, w, h, r, blur, rgba(COLOR, argb), rgba(COLOR2, 0), PAINT);
		nvgBeginPath(vg);
		nvgRect(vg, x - blur * 2, y - blur * 2 + dy, w + blur * 4, h + blur * 4);
		nvgFillPaint(vg, PAINT);
		nvgFill(vg);
	}

	private static void text(long vg, String font, float size, float x, float y, int argb, String s) {
		nvgFontFace(vg, font);
		nvgFontSize(vg, size);
		nvgFillColor(vg, rgba(COLOR, argb));
		nvgText(vg, x, y, s);
	}

	public static void paint(long vg) {
		if (!fontsMade) {
			regular = file("poppins-regular.ttf");
			semibold = file("poppins-semibold.ttf");
			nvgCreateFontMem(vg, "regular", regular, false);
			nvgCreateFontMem(vg, "semibold", semibold, false);
			fontsMade = true;
		}
		var window = Minecraft.getInstance().getWindow();
		float w = window.getWidth(), h = window.getHeight(), s = h / 900f;
		String[] side = {"Edit HUD", "Global Search", "Mods", "Profiles", "Keybinds", "Themes", "Preferences", "Changelog"};
		String[] names = {"FPS", "Ping", "Keystrokes", "CPS", "Coordinates", "Armor", "Potions", "Zoom", "Clock", "Speed", "Memory", "Totems", "Pearls", "Combo", "Reach", "Open World"};
		float px = w * 0.17f, py = h * 0.17f, pw = w * 0.66f, ph = h * 0.66f;
		shadow(vg, px, py, pw, ph, 14 * s, 24 * s, 8 * s, 0x80000000);
		rrect(vg, px, py, pw, ph, 14 * s, 0xF0151821);
		rrect(vg, px, py, pw * 0.22f, ph, 14 * s, 0xFF10131A);
		text(vg, "semibold", 22 * s, px + 20 * s, py + 40 * s, 0xFFFFFFFF, "DNZ CLIENT");
		for (int i = 0; i < side.length; i++) {
			text(vg, "regular", 13 * s, px + 22 * s, py + (80 + i * 30) * s, 0xFF9AA3B5, side[i]);
		}
		float gx = px + pw * 0.25f, gw = pw * 0.72f, cw = (gw - 3 * 12 * s) / 4, ch = cw * 0.53f;
		for (int i = 0; i < names.length; i++) {
			float x = gx + (i % 4) * (cw + 12 * s), y = py + 60 * s + (i / 4) * (ch + 12 * s);
			shadow(vg, x, y, cw, ch, 8 * s, 6 * s, 3 * s, 0x60000000);
			rrect(vg, x, y, cw, ch, 8 * s, 0xFF1E2230);
			rrect(vg, x, y + ch - ch * 0.27f, cw, ch * 0.27f, 0, 0xFF2B6BFF);
			text(vg, "regular", 11 * s, x + 10 * s, y + ch - 7 * s, 0xFFFFFFFF, names[i]);
		}
	}
}
