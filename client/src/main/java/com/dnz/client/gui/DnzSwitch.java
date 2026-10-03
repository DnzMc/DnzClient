package com.dnz.client.gui;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** Just the sliding on/off switch of an option row. Changes right away, no rebuild. */
public class DnzSwitch extends AbstractButton {
	private final Consumer<Boolean> onChange;
	private boolean on;
	private float knob;
	private float hover;

	public DnzSwitch(int x, int y, boolean on, Consumer<Boolean> onChange) {
		super(x, y, 22, 12, Component.empty());
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
		this.hover = Anim.approach(this.hover, this.isHoveredOrFocused() ? 1.0F : 0.0F, 14.0F);
		this.knob = Anim.approach(this.knob, this.on ? 1.0F : 0.0F, 16.0F);
		Ui.switchControl(g, this.getX(), this.getY(), this.width, this.height, Anim.easeOut(this.knob), this.hover);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}
