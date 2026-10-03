package com.dnz.client.gui.config;

import com.dnz.client.gui.Anim;
import com.dnz.client.gui.Palette;
import com.dnz.client.gui.Smooth;
import com.dnz.client.gui.Theme;
import com.dnz.client.gui.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** A setting that does something: a button on the right (accent colored, or glass with {@link #plain()}). */
public class ButtonOption extends Option {
	private static final float BTN_H = 15;
	private static final float TEXT = 0.75F;
	private final String label;
	private final Runnable action;
	private boolean accent = true;
	private float press;
	private float btnW;
	private float btnX;
	private float btnY;

	public ButtonOption(String name, String label, Runnable action) {
		super(name);
		this.label = label;
		this.action = action;
	}

	/** Glass button instead of the accent color. */
	public ButtonOption plain() {
		this.accent = false;
		return this;
	}

	@Override
	protected float controlWidth() {
		return this.btnW;
	}

	@Override
	public void extract(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered) {
		this.btnW = Math.max(46, Ui.width(Ui.medium(this.label), TEXT) + 20);
		this.btnX = this.x + this.width - 9 - this.btnW;
		this.btnY = this.y + (this.height - BTN_H) / 2.0F;
		super.extract(g, mouseX, mouseY, hovered);
	}

	@Override
	protected void extractControl(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		boolean on = this.isActive();
		this.press = Anim.approach(this.press, 0.0F, 10.0F);
		// Pressed: the button dips in for a moment.
		float shrink = this.press * 1.2F;
		float bx = this.btnX + shrink;
		float by = this.btnY + shrink * 0.5F;
		float bw = this.btnW - shrink * 2;
		float bh = BTN_H - shrink;
		if (this.accent && on) {
			int accent = Theme.menuAccent();
			Smooth.rect(g, bx, by, bw, bh, 4, Theme.lerp(accent, 0xFF000000 | Theme.lighter(accent & 0xFFFFFF), this.hover));
		} else {
			Smooth.rect(g, bx, by, bw, bh, 4, !on ? 0xFF1A2027 : Theme.lerp(0xFF283140, 0xFF323C4B, this.hover));
		}
		Component text = Ui.medium(this.label);
		Ui.centered(g, text, bx + bw / 2.0F, by + (bh - 8 * TEXT) / 2.0F + 0.5F, TEXT, !on ? Ui.TEXT_OFF : this.accent ? Palette.ON_ACCENT : 0xFFFFFFFF);
	}

	@Override
	public boolean click(double mouseX, double mouseY, int button, boolean doubleClick) {
		if (button != 0) {
			return false;
		}
		this.press = 1.0F;
		clickSound();
		this.action.run();
		return true;
	}
}
