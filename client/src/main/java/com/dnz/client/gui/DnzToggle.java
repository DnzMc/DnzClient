package com.dnz.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** A card with a name on the left and a sliding on/off switch on the right. Changes right away, no rebuild. */
public class DnzToggle extends AbstractButton {
	private final Consumer<Boolean> onChange;
	private boolean on;
	private float hover;
	private float knob;

	public DnzToggle(int x, int y, int width, int height, Component label, boolean on, Consumer<Boolean> onChange) {
		super(x, y, width, height, label);
		this.on = on;
		this.knob = on ? 1.0F : 0.0F;
		this.onChange = onChange;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		this.on = !this.on;
		this.onChange.accept(this.on);
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		Font font = Minecraft.getInstance().font;
		this.hover = Anim.approach(this.hover, this.isHoveredOrFocused() ? 1.0F : 0.0F, 14.0F);
		this.knob = Anim.approach(this.knob, this.on ? 1.0F : 0.0F, 16.0F);
		int x = this.getX();
		int y = this.getY();
		int w = this.width;
		int h = this.height;
		int accent = 0xFF000000 | Theme.accentInfo().rgb();

		// Card
		Smooth.rect(g, x, y, w, h, 7, Theme.lerp(0xFF1A1D23, 0xFF22262E, this.hover));
		// Name, cut with "…" when it does not fit next to the switch.
		Component name = Theme.smooth(this.getMessage());
		int maxText = w - 44;
		if (font.width(name) > maxText) {
			name = Theme.smooth(Component.literal(font.plainSubstrByWidth(this.getMessage().getString(), maxText - 6) + "…"));
		}
		g.text(font, name, x + 9, y + (h - 8) / 2, Theme.lerp(0xFFC9CFDA, 0xFFFFFFFF, Math.max(this.hover, this.knob)), false);

		// Switch: track fills with the accent color, the knob slides right.
		float sw = 22;
		float sh = 12;
		float sx = x + w - sw - 8;
		float sy = y + (h - sh) / 2.0F;
		Smooth.rect(g, sx, sy, sw, sh, sh / 2, Theme.lerp(0xFF3A3F49, accent, this.knob));
		float k = sh - 4;
		float kx = sx + 2 + (sw - sh) * this.knob;
		Smooth.rect(g, kx, sy + 2, k, k, k / 2, 0xFFFFFFFF);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}
