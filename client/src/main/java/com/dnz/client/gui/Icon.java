package com.dnz.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/** One-color line icons from the bundled Material Icons font (assets/dnzclient/font/icons.json). */
public final class Icon {
	public static final FontDescription FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("dnzclient", "icons"));

	public static final char EDIT_HUD = '\ue871';
	public static final char SEARCH = '\uef7a';
	public static final char MODS = '\ue429';
	public static final char PROFILES = '\uea21';
	public static final char KEYBINDS = '\ue312';
	public static final char THEMES = '\ue3ae';
	public static final char PREFERENCES = '\ue8b8';
	public static final char CHANGELOG = '\ue627';
	public static final char FEEDBACK = '\ue87f';
	public static final char BELL = '\ue7f5';
	public static final char BACK = '\ue5c4';
	public static final char FORWARD = '\ue5c8';
	public static final char CLOSE = '\ue5cd';
	public static final char COMBAT = '\uea28';
	public static final char QUALITY = '\uf8cd';
	public static final char SERVER = '\ue80b';

	private Icon() {
	}

	public static Component of(char icon) {
		return Component.literal(String.valueOf(icon)).withStyle(style -> style.withFont(FONT));
	}

	/** Draws an icon [size] pixels tall with its top-left corner at x, y. */
	public static void draw(GuiGraphicsExtractor g, char icon, float x, float y, float size, int color) {
		float s = size / 9.0F;
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(s, s);
		g.text(Minecraft.getInstance().font, of(icon), 0, 0, color, false);
		g.pose().popMatrix();
	}
}
