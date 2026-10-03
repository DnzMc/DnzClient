package com.dnz.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** DNZ menu sidebar entry: a line icon and the name; the open page is a filled accent bar. */
public class DnzSideItem extends AbstractButton {
	private final char icon;
	private final boolean selected;
	private final boolean beta;
	private final Runnable action;
	private float hover;

	public DnzSideItem(int x, int y, int width, int height, String label, char icon, boolean selected, boolean beta, Runnable action) {
		super(x, y, width, height, Component.literal(label));
		this.icon = icon;
		this.selected = selected;
		this.beta = beta;
		this.action = action;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		this.action.run();
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		Font font = Minecraft.getInstance().font;
		this.hover = Anim.approach(this.hover, this.isHoveredOrFocused() ? 1.0F : 0.0F, 14.0F);
		int x = this.getX();
		int y = this.getY();
		int accent = Theme.menuAccent();
		if (this.selected) {
			Smooth.rect(g, x, y, this.width, this.height, 3.5F, accent);
		} else if (this.hover > 0.01F) {
			Smooth.rect(g, x, y, this.width, this.height, 3.5F, Theme.withAlpha(0xFFFFFF, 0.06F * this.hover));
		}
		int color = this.selected ? Palette.ON_ACCENT : Theme.lerp(0xFFE1E5EC, 0xFFFFFFFF, this.hover);
		Icon.draw(g, this.icon, x + 7, y + (this.height - 8) / 2.0F, 8, color);
		// Name a little smaller than normal text, like a web page.
		Component text = Ui.medium(this.getMessage().getString());
		float s = 0.8F;
		g.pose().pushMatrix();
		g.pose().translate(x + 21, y + (this.height - 8 * s) / 2.0F);
		g.pose().scale(s, s);
		g.text(font, text, 0, 0, color, false);
		g.pose().popMatrix();
		if (this.beta) {
			// Small "BETA" pill after the name.
			float bx = x + 25 + font.width(text) * s;
			Smooth.rect(g, bx, y + (this.height - 8) / 2.0F, 20, 8, 4, accent);
			g.pose().pushMatrix();
			g.pose().translate(bx + 10, y + (this.height - 8) / 2.0F + 2);
			g.pose().scale(0.55F, 0.55F);
			g.centeredText(font, DnzMenuScreen.bold("BETA"), 0, 0, Palette.ON_ACCENT);
			g.pose().popMatrix();
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}
