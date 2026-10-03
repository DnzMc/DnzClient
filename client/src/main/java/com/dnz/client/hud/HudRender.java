package com.dnz.client.hud;

import com.dnz.client.gui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * What a module draws with. In "measure" mode nothing is drawn (only the size is worked out);
 * without a background, text gets a shadow so it stays readable.
 */
public final class HudRender {
	public final GuiGraphicsExtractor g;
	public final Minecraft mc;
	public final Font font;
	/** Only the size is needed, draw nothing. */
	public final boolean measure;
	/** Drawn in the HUD editor: show example content when there is nothing real (no target, no effects...). */
	public final boolean editor;
	public final boolean bg;

	HudRender(GuiGraphicsExtractor g, Minecraft mc, boolean measure, boolean editor, boolean bg) {
		this.g = g;
		this.mc = mc;
		this.font = mc.font;
		this.measure = measure;
		this.editor = editor;
		this.bg = bg;
	}

	public void panel(int w, int h) {
		if (!this.measure && this.bg) {
			Theme.roundedRect(this.g, 0, 0, w, h, Theme.withAlpha(Theme.style().panel(), 0.62F));
		}
	}

	/** The thin accent bar on the left of text panels. */
	public void accentBar(int h) {
		if (!this.measure && this.bg) {
			this.g.fill(0, 3, 2, h - 3, Theme.accent());
		}
	}

	public void text(Component text, int x, int y, int color) {
		if (!this.measure) {
			this.g.text(this.font, text, x, y, color, !this.bg);
		}
	}

	public void text(String text, int x, int y, int color) {
		if (!this.measure) {
			this.g.text(this.font, text, x, y, color, !this.bg);
		}
	}

	public void centered(String text, int centerX, int y, int color) {
		this.text(text, centerX - this.font.width(text) / 2, y, color);
	}

	public void fill(int x0, int y0, int x1, int y1, int color) {
		if (!this.measure) {
			this.g.fill(x0, y0, x1, y1, color);
		}
	}

	public void rounded(int x, int y, int w, int h, int color) {
		if (!this.measure) {
			Theme.roundedRect(this.g, x, y, w, h, color);
		}
	}

	public void item(ItemStack stack, int x, int y) {
		if (!this.measure && !stack.isEmpty()) {
			this.g.item(stack, x, y);
		}
	}

	public void itemWithCount(ItemStack stack, int x, int y) {
		if (!this.measure && !stack.isEmpty()) {
			this.g.item(stack, x, y);
			this.g.itemDecorations(this.font, stack, x, y);
		}
	}

	public int width(String text) {
		return this.font.width(text);
	}

	public int width(Component text) {
		return this.font.width(text);
	}

	public static int accent() {
		return Theme.accent();
	}

	public static int accentRgb() {
		return Theme.accentInfo().rgb();
	}
}
