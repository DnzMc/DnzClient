package com.dnz.schematic.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** Flat rounded button of the schematic menu. Selected = accent color; danger = red text. */
public class UiButton extends AbstractButton {
	public static final int ACCENT = 0xFF4FA3FF;

	private final Runnable action;
	private boolean selected;
	private boolean danger;
	private boolean leftAligned;
	private float hover;

	public UiButton(int x, int y, int width, int height, String text, Runnable action) {
		super(x, y, width, height, Component.literal(text));
		this.action = action;
	}

	public UiButton selected(boolean selected) {
		this.selected = selected;
		return this;
	}

	public UiButton danger() {
		this.danger = true;
		return this;
	}

	public UiButton left() {
		this.leftAligned = true;
		return this;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		this.action.run();
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		float target = this.active && this.isHoveredOrFocused() ? 1.0F : 0.0F;
		this.hover += (target - this.hover) * 0.3F;
		int x = this.getX(), y = this.getY(), w = this.width, h = this.height;
		int bg;
		if (this.selected) {
			bg = ACCENT;
		} else {
			int alpha = 0x40 + (int) (this.hover * 0x30);
			bg = alpha << 24 | 0x2A2F3D;
		}
		Ui.roundedRect(g, x, y, w, h, bg);
		if (!this.selected) {
			Ui.roundedOutline(g, x, y, w, h, this.hover > 0.5F ? 0x60FFFFFF : 0x28FFFFFF);
		}
		int color = !this.active ? 0xFF6A7080 : this.danger ? 0xFFFF6B6B : 0xFFFFFFFF;
		var font = Minecraft.getInstance().font;
		int ty = y + (h - 8) / 2;
		if (this.leftAligned) {
			g.text(font, this.getMessage(), x + 6, ty, color, false);
		} else {
			g.centeredText(font, this.getMessage(), x + w / 2, ty, color);
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}
