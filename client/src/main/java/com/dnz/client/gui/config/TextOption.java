package com.dnz.client.gui.config;

import com.dnz.client.gui.Anim;
import com.dnz.client.gui.Smooth;
import com.dnz.client.gui.Theme;
import com.dnz.client.gui.Ui;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/**
 * Text setting: a text box on the right. Click it to type; the edge lights up in the accent color while typing.
 * Enter or Escape stops typing; every change is passed on right away.
 */
public class TextOption extends Option {
	private static final float BOX_H = 15;
	private final Supplier<String> get;
	private final TextField field = new TextField();
	private final String hint;
	/** Typing glow, 0..1. */
	private float glow;
	private float boxW;
	private float boxX;
	private float boxY;
	private boolean selecting;

	public TextOption(String name, String hint, int maxLength, Supplier<String> get, Consumer<String> set) {
		super(name);
		this.get = get;
		this.hint = hint;
		this.field.maxLength(maxLength).value(get.get()).onChange(set);
	}

	@Override
	protected float controlWidth() {
		return this.boxW;
	}

	@Override
	protected boolean wantsKeyboard() {
		return true;
	}

	@Override
	protected void adopt(Option old) {
		super.adopt(old);
		if (old instanceof TextOption before) {
			this.glow = before.glow;
		}
	}

	@Override
	public void extract(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered) {
		this.boxW = Math.min(this.wide ? 180 : 120, this.width * 0.5F);
		this.boxX = this.x + this.width - 9 - this.boxW;
		this.boxY = this.y + (this.height - BOX_H) / 2.0F;
		if (!this.field.isFocused()) {
			this.field.value(this.get.get()); // shows changes made elsewhere
		}
		super.extract(g, mouseX, mouseY, hovered);
	}

	@Override
	protected void extractControl(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		boolean typing = this.field.isFocused();
		boolean over = inside(mouseX, mouseY, this.boxX, this.boxY, this.boxW, BOX_H);
		this.glow = Anim.approach(this.glow, typing ? 1.0F : 0.0F, 14.0F);
		int accent = Theme.menuAccent();
		if (this.glow > 0.01F) {
			Smooth.shadow(g, this.boxX, this.boxY, this.boxW, BOX_H, 4, 4 * this.glow, Theme.withAlpha(accent & 0xFFFFFF, 0.35F * this.glow));
		}
		int edge = Theme.lerp(Theme.lerp(0x12FFFFFF, 0x2AFFFFFF, over ? 1.0F : 0.0F), accent, this.glow);
		Smooth.rect(g, this.boxX, this.boxY, this.boxW, BOX_H, 4, edge);
		Smooth.rect(g, this.boxX + 0.75F, this.boxY + 0.75F, this.boxW - 1.5F, BOX_H - 1.5F, 3.25F, Ui.COMPONENT_BG);
		this.field.extract(g, this.boxX, this.boxY, this.boxW, BOX_H, this.hint, this.isActive());
	}

	@Override
	public boolean click(double mouseX, double mouseY, int button, boolean doubleClick) {
		if (button != 0) {
			return false;
		}
		boolean shift = net.minecraft.client.Minecraft.getInstance().hasShiftDown();
		this.field.click(Math.max(this.boxX, Math.min(mouseX, this.boxX + this.boxW)), shift, doubleClick);
		this.selecting = true;
		return true;
	}

	@Override
	public void drag(double mouseX, double mouseY) {
		if (this.selecting) {
			this.field.drag(mouseX);
		}
	}

	@Override
	public void release(double mouseX, double mouseY) {
		this.selecting = false;
	}

	@Override
	public boolean key(KeyEvent event) {
		if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
			this.blur();
			return true;
		}
		return this.field.key(event);
	}

	@Override
	public boolean typed(CharacterEvent event) {
		return this.field.typed(event);
	}

	@Override
	public void blur() {
		this.field.focus(false);
	}
}
