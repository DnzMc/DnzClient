package com.dnz.client.skia;

import com.dnz.client.gui.Smooth;
import com.dnz.client.gui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;

/** Comparison: the same DNZ menu layout drawn by DNZ's own smooth shapes and fonts (Minecraft's renderer). */
public class OwnDemoScreen extends Screen {
	public OwnDemoScreen() {
		super(Component.literal("DNZ own"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void text(GuiGraphicsExtractor g, FontDescription font, float px, float x, float y, int color, String s) {
		// px = wanted text height in window pixels (like the Skia/NanoVG font size); Minecraft's font is 9 units tall.
		float scale = px / (float) this.minecraft.getWindow().getGuiScale() / 7.0F;
		g.pose().pushMatrix();
		g.pose().translate(x, y - 7.0F * scale);
		g.pose().scale(scale, scale);
		g.text(this.font, Component.literal(s).withStyle(st -> st.withFont(font)), 0, 0, color, false);
		g.pose().popMatrix();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		boolean was = Theme.smoothAll;
		Theme.smoothAll = false;
		float w = this.width, h = this.height, gs = (float) this.minecraft.getWindow().getGuiScale();
		float s = this.minecraft.getWindow().getHeight() / 900f / gs; // window pixels of the reference -> GUI units
		String[] side = {"Edit HUD", "Global Search", "Mods", "Profiles", "Keybinds", "Themes", "Preferences", "Changelog"};
		String[] names = {"FPS", "Ping", "Keystrokes", "CPS", "Coordinates", "Armor", "Potions", "Zoom", "Clock", "Speed", "Memory", "Totems", "Pearls", "Combo", "Reach", "Open World"};
		float px = w * 0.17f, py = h * 0.17f, pw = w * 0.66f, ph = h * 0.66f;
		Smooth.shadow(g, px, py + 8 * s, pw, ph, 14 * s, 24 * s, 0x80000000);
		Smooth.rect(g, px, py, pw, ph, 14 * s, 0xF0151821);
		Smooth.rect(g, px, py, pw * 0.22f, ph, 14 * s, 0xFF10131A);
		text(g, Theme.UI_SEMIBOLD, 22 * s * gs, px + 20 * s, py + 40 * s, 0xFFFFFFFF, "DNZ CLIENT");
		for (int i = 0; i < side.length; i++) {
			text(g, Theme.UI_REGULAR, 13 * s * gs, px + 22 * s, py + (80 + i * 30) * s, 0xFF9AA3B5, side[i]);
		}
		float gx = px + pw * 0.25f, gw = pw * 0.72f, cw = (gw - 3 * 12 * s) / 4, ch = cw * 0.53f;
		for (int i = 0; i < names.length; i++) {
			float x = gx + (i % 4) * (cw + 12 * s), y = py + 60 * s + (i / 4) * (ch + 12 * s);
			Smooth.shadow(g, x, y + 3 * s, cw, ch, 8 * s, 6 * s, 0x60000000);
			Smooth.rect(g, x, y, cw, ch, 8 * s, 0xFF1E2230);
			Smooth.rect(g, x, y + ch - ch * 0.27f, cw, ch * 0.27f, 0, 0xFF2B6BFF);
			text(g, Theme.UI_REGULAR, 11 * s * gs, x + 10 * s, y + ch - 7 * s, 0xFFFFFFFF, names[i]);
		}
		Theme.smoothAll = was;
	}
}
