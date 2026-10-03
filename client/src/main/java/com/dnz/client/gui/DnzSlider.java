package com.dnz.client.gui;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Whole-number slider (min..max in steps). Styled by AbstractSliderButtonMixin like every DNZ slider. */
public class DnzSlider extends AbstractSliderButton {
	private final int min;
	private final int max;
	private final int step;
	private final IntFunction<Component> format;
	private final IntConsumer onChange;
	private final Runnable onRelease;
	private int current;

	public DnzSlider(int x, int y, int width, int height, int min, int max, int step, int value,
		IntFunction<Component> format, IntConsumer onChange, Runnable onRelease) {
		super(x, y, width, height, Component.empty(), max > min ? (double) (value - min) / (max - min) : 0.0);
		this.min = min;
		this.max = max;
		this.step = Math.max(1, step);
		this.format = format;
		this.onChange = onChange;
		this.onRelease = onRelease;
		this.current = value;
		this.updateMessage();
	}

	private int snapped() {
		int raw = this.min + (int) Math.round(this.value * (this.max - this.min));
		int steps = Math.round((raw - this.min) / (float) this.step);
		return Math.max(this.min, Math.min(this.max, this.min + steps * this.step));
	}

	@Override
	protected void updateMessage() {
		this.setMessage(this.format.apply(this.snapped()));
	}

	@Override
	protected void applyValue() {
		int value = this.snapped();
		if (value != this.current) {
			this.current = value;
			this.onChange.accept(value);
		}
	}

	@Override
	public void onRelease(MouseButtonEvent event) {
		super.onRelease(event);
		this.onRelease.run();
	}
}
