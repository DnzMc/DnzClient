package com.dnz.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** "DNZ Client" in the bold DNZ font (DNZ white, Client blue) at the bottom right of the inventory and ESC menu. */
public final class Watermark {
	/** Blue of the DNZ logo. */
	private static final int BLUE = 0x4DA0FF;
	private static final int MARGIN = 6;

	private Watermark() {
	}

	public static void draw(Screen screen, GuiGraphicsExtractor g) {
		Font font = Minecraft.getInstance().font;
		Component text = Component.literal("DNZ ").withColor(0xFFFFFF)
			.append(Component.literal("Client").withColor(BLUE))
			.withStyle(style -> style.withFont(Theme.BOLD_FONT));
		int x = screen.width - font.width(text) - MARGIN;
		int y = screen.height - font.lineHeight - MARGIN + 2;
		g.text(font, text, x, y, 0xFFFFFFFF, false);
	}
}
