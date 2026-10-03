package com.dnz.client.gui.config;

import com.dnz.client.gui.Smooth;
import com.dnz.client.gui.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Section title over a group of rows ("General", "Menu"...), across the whole width. */
public class HeaderOption extends Option {
	private static final float TEXT = 0.85F;

	public HeaderOption(String title) {
		super(title);
		this.wide = true;
	}

	@Override
	public int rowHeight() {
		return 15;
	}

	@Override
	public void extract(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered) {
		Component title = Ui.semibold(this.name);
		float textW = Ui.width(title, TEXT);
		Ui.text(g, title, this.x + 1, this.y + 4, TEXT, Ui.TEXT);
		// Faint line after the title, to the right edge.
		float lineX = this.x + textW + 8;
		if (lineX < this.x + this.width) {
			Smooth.rect(g, lineX, this.y + 8, this.x + this.width - lineX, 0.75F, 0, 0x16FFFFFF);
		}
	}

	@Override
	protected void extractControl(GuiGraphicsExtractor g, int mouseX, int mouseY) {
	}
}
