package com.dnz.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * Glass menu button (Simple style ESC menu): translucent gray glass with a thin light border,
 * round corners and spaced-out bold capitals. [accent] buttons are translucent in the DNZ accent color.
 */
public class GlassButton extends AbstractButton {
	/** GLASS: gray glass. ACCENT: accent-colored glass. GHOST: invisible until hovered (menu tabs). */
	public enum Look { GLASS, ACCENT, GHOST }

	private final Runnable action;
	private final Look look;
	private final boolean accent;
	private final float textScale;
	private float hover;

	public GlassButton(int x, int y, int width, int height, Component message, boolean accent, Runnable action) {
		this(x, y, width, height, message, accent ? Look.ACCENT : Look.GLASS, 0.95F, action);
	}

	public GlassButton(int x, int y, int width, int height, Component message, Look look, float textScale, Runnable action) {
		super(x, y, width, height, message);
		this.look = look;
		this.accent = look == Look.ACCENT;
		this.textScale = textScale;
		this.action = action;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		this.action.run();
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		float target = this.isHoveredOrFocused() ? 1.0F : 0.0F;
		this.hover += (target - this.hover) * 0.3F;
		int x = this.getX();
		int y = this.getY();
		int w = this.width;
		int h = this.height;

		int rgb = Theme.accentInfo().rgb();
		int border = this.accent
			? Theme.withAlpha(Theme.lerp(0xFF000000 | rgb, 0xFFFFFFFF, 0.35F) & 0xFFFFFF, 0.75F + 0.2F * this.hover)
			: Theme.lerp(0x5CFFFFFF, 0x8CFFFFFF, this.hover);
		int fill = this.accent
			? Theme.withAlpha(rgb, 0.42F + 0.18F * this.hover)
			: Theme.lerp(0x55646C78, 0x7A7A8492, this.hover);
		if (this.look == Look.GHOST) {
			border = Theme.withAlpha(0xFFFFFF, 0.35F * this.hover);
			fill = Theme.withAlpha(0xFFFFFF, 0.10F * this.hover);
		}
		if (this.look != Look.GHOST || this.hover > 0.02F) {
			Theme.roundedRect(g, x, y, w, h, 4, border);
			Theme.roundedRect(g, x + 1, y + 1, w - 2, h - 2, 3, fill);
		}

		int textColor = this.look == Look.GHOST ? Theme.lerp(0xFFB4BAC6, 0xFFFFFFFF, this.hover) : 0xFFFFFFFF;
		Theme.spacedText(g, Minecraft.getInstance().font, this.getMessage().getString().toUpperCase(java.util.Locale.ROOT),
			x + w / 2, y + Math.round((h - 8 * this.textScale) / 2), this.textScale, this.look == Look.GHOST ? 1 : 2, textColor, 0.0F);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}
