package com.dnz.client.skia;

import java.io.InputStream;
import net.minecraft.client.Minecraft;
import org.jetbrains.skia.Canvas;
import org.jetbrains.skia.Data;
import org.jetbrains.skia.Font;
import org.jetbrains.skia.FontMgr;
import org.jetbrains.skia.ImageFilter;
import org.jetbrains.skia.Paint;
import org.jetbrains.skia.RRect;
import org.jetbrains.skia.Typeface;

/** Experiment: the DNZ menu layout drawn by Skia (smooth corners, real shadows, browser-quality text). */
public final class SkiaDemo {
	private static Typeface regular;
	private static Typeface semibold;

	private SkiaDemo() {
	}

	private static Typeface font(String file) {
		try (InputStream in = SkiaDemo.class.getResourceAsStream("/assets/dnzclient/font/" + file)) {
			byte[] bytes = in.readAllBytes();
			return FontMgr.Companion.getDefault().makeFromData(Data.Companion.makeFromBytes(bytes, 0, bytes.length), 0);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	public static void paint(Canvas c) {
		if (regular == null) {
			regular = font("poppins-regular.ttf");
			semibold = font("poppins-semibold.ttf");
		}
		var window = Minecraft.getInstance().getWindow();
		float w = window.getWidth(), h = window.getHeight(), s = h / 900f;
		Font title = new Font(semibold, 22 * s), body = new Font(regular, 13 * s), small = new Font(regular, 11 * s);
		Paint panel = new Paint();
		panel.setColor(0xF0151821);
		panel.setImageFilter(ImageFilter.Companion.makeDropShadow(0, 8 * s, 24 * s, 24 * s, 0x80000000, null, null));
		Paint side = new Paint();
		side.setColor(0xFF10131A);
		Paint card = new Paint();
		card.setColor(0xFF1E2230);
		card.setImageFilter(ImageFilter.Companion.makeDropShadow(0, 3 * s, 6 * s, 6 * s, 0x60000000, null, null));
		Paint bar = new Paint();
		bar.setColor(0xFF2B6BFF);
		Paint text = new Paint();
		text.setColor(0xFFFFFFFF);
		Paint muted = new Paint();
		muted.setColor(0xFF9AA3B5);
		String[] side_ = {"Edit HUD", "Global Search", "Mods", "Profiles", "Keybinds", "Themes", "Preferences", "Changelog"};
		String[] names = {"FPS", "Ping", "Keystrokes", "CPS", "Coordinates", "Armor", "Potions", "Zoom", "Clock", "Speed", "Memory", "Totems", "Pearls", "Combo", "Reach", "Open World"};
		float px = w * 0.17f, py = h * 0.17f, pw = w * 0.66f, ph = h * 0.66f;
		c.drawRRect(RRect.Companion.makeXYWH(px, py, pw, ph, 14 * s), panel);
		c.drawRRect(RRect.Companion.makeXYWH(px, py, pw * 0.22f, ph, 14 * s), side);
		c.drawString("DNZ CLIENT", px + 20 * s, py + 40 * s, title, text);
		for (int i = 0; i < side_.length; i++) {
			c.drawString(side_[i], px + 22 * s, py + (80 + i * 30) * s, body, muted);
		}
		float gx = px + pw * 0.25f, gw = pw * 0.72f, cw = (gw - 3 * 12 * s) / 4, ch = cw * 0.53f;
		for (int i = 0; i < names.length; i++) {
			float x = gx + (i % 4) * (cw + 12 * s), y = py + 60 * s + (i / 4) * (ch + 12 * s);
			c.drawRRect(RRect.Companion.makeXYWH(x, y, cw, ch, 8 * s), card);
			c.drawRRect(RRect.Companion.makeXYWH(x, y + ch - ch * 0.27f, cw, ch * 0.27f, 0), bar);
			c.drawString(names[i], x + 10 * s, y + ch - 7 * s, small, text);
		}
		for (Object o : new Object[] {panel, side, card, bar, text, muted, title, body, small}) {
			((org.jetbrains.skia.impl.Managed) o).close();
		}
	}
}
