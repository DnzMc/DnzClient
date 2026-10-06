package com.dnz.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** Smooth, rounded, animated button used across DNZ Client screens. */
public class DnzButton extends AbstractButton {
	/** BUTTON: normal; TAB: sidebar entry (left text, see-through); CHIP: small pill (category filters). */
	public enum Look { BUTTON, TAB, CHIP }

	private final Runnable action;
	private float hover;
	private float select;
	private boolean selected;
	private Look look = Look.BUTTON;

	public DnzButton(int x, int y, int width, int height, Component message, Runnable action) {
		super(x, y, width, height, message);
		this.action = action;
	}

	public DnzButton selected(boolean selected) {
		this.selected = selected;
		this.select = selected ? 1.0F : 0.0F;
		return this;
	}

	public DnzButton look(Look look) {
		this.look = look;
		return this;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		this.action.run();
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		Font font = Minecraft.getInstance().font;
		if (Theme.vanillaWidgets()) {
			// Vanilla Minecraft button; selected options get yellow text.
			this.extractDefaultSprite(g);
			int color = !this.active ? 0xFFA0A0A0 : this.selected ? 0xFFFFFF55 : 0xFFFFFFFF;
			g.centeredText(font, this.getMessage(), this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, color);
			return;
		}

		this.hover = Anim.approach(this.hover, this.active && this.isHoveredOrFocused() ? 1.0F : 0.0F, 14.0F);
		int x = this.getX();
		int y = this.getY();
		int w = this.width;
		int h = this.height;

		if (Theme.simpleStyle()) {
			// Simple: rounded glass button, smooth centered text, no side bar.
			Theme.roundedRect(g, x, y, w, h, 3, Theme.simpleButtonBg(this.active, this.selected, this.hover));
			Theme.centeredText(g, font, this.getMessage(), x + w / 2, y + (h - 8) / 2, this.active ? 0xFFFFFFFF : 0xFF8A8F9C);
			return;
		}

		Component text = Theme.smooth(this.getMessage());
		// Text wider than the button is cut with "…" instead of spilling over the edges.
		int room = this.look == Look.TAB ? this.width - 16 : this.width - 8;
		if (font.width(text) > room) {
			text = Theme.smooth(Component.literal(font.plainSubstrByWidth(this.getMessage().getString(), room - 6) + "…"));
		}
		int accent = Theme.accentInfo().rgb();
		int textY = y + (h - 8) / 2;
		switch (this.look) {
			case TAB -> {
				// See-through until hovered; the open tab glows softly in the accent color.
				if (this.selected) {
					Smooth.rect(g, x, y, w, h, 7, Theme.withAlpha(accent, 0.20F));
					Smooth.rect(g, x + 3, y + h / 2.0F - 5, 3, 10, 1.5F, 0xFF000000 | accent);
				} else if (this.hover > 0.01F) {
					Smooth.rect(g, x, y, w, h, 7, Theme.withAlpha(0xFFFFFF, 0.07F * this.hover));
				}
				int color = this.selected ? 0xFFFFFFFF : Theme.lerp(0xFF9AA3B5, 0xFFFFFFFF, this.hover);
				g.text(font, text, x + 12, textY, color, false);
			}
			case CHIP -> {
				int bg = this.selected ? 0xFF000000 | accent : Theme.lerp(0xFF1E2128, 0xFF2A2E37, this.hover);
				Smooth.rect(g, x, y, w, h, h / 2.0F, bg);
				int color = this.selected ? 0xFFFFFFFF : Theme.lerp(0xFFB4BBC8, 0xFFFFFFFF, this.hover);
				g.centeredText(font, text, x + w / 2, textY, color);
			}
			default -> {
				if (this.selected && this.active) {
					Smooth.rect(g, x, y, w, h, 4, Theme.lerp(Theme.menuAccent(), 0xFF000000 | Theme.lighter(Theme.menuAccent() & 0xFFFFFF), this.hover));
				} else {
					Smooth.rect(g, x, y, w, h, 4, !this.active ? 0xFF1A2027 : Theme.lerp(0xFF283140, 0xFF323C4B, this.hover));
				}
				int color = !this.active ? 0xFF6B7180 : this.selected ? Palette.ON_ACCENT : 0xFFFFFFFF;
				Ui.centered(g, Ui.medium(text.getString()), x + w / 2.0F, y + (h - 8 * 0.85F) / 2.0F + 0.5F, 0.85F, color);
			}
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}
