package com.dnz.client.gui.config;

import com.dnz.client.gui.Anim;
import com.dnz.client.gui.Theme;
import com.dnz.client.gui.Ui;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Whole-number setting (min..max in steps): the name and the value on top, a thin track with a round knob below.
 * Drag the knob or click the track; the knob glides to a clicked spot.
 */
public class SliderOption extends Option {
	private final int min;
	private final int max;
	private final int step;
	private final IntSupplier get;
	private final IntConsumer set;
	private final IntFunction<String> format;
	private Runnable done = () -> {
	};
	/** Knob position 0..1 as drawn (follows the value smoothly). */
	private float shown = -1;
	private boolean dragging;
	/** The mouse moved since the press: the knob sticks to it (a plain click lets it glide there). */
	private boolean moved;

	public SliderOption(String name, int min, int max, int step, IntSupplier get, IntConsumer set, IntFunction<String> format) {
		super(name);
		this.min = min;
		this.max = Math.max(min + 1, max);
		this.step = Math.max(1, step);
		this.get = get;
		this.set = set;
		this.format = format;
	}

	/** Runs when the knob is let go (e.g. to save). */
	public SliderOption onRelease(Runnable done) {
		this.done = done;
		return this;
	}

	@Override
	public int rowHeight() {
		return 32;
	}

	@Override
	protected void adopt(Option old) {
		super.adopt(old);
		if (old instanceof SliderOption before) {
			this.shown = before.shown;
		}
	}

	private float trackX() {
		return this.x + 11;
	}

	private float trackW() {
		return this.width - 22;
	}

	private float value() {
		return Math.max(0.0F, Math.min(1.0F, (this.get.getAsInt() - this.min) / (float) (this.max - this.min)));
	}

	@Override
	protected void extractLabel(GuiGraphicsExtractor g) {
		boolean on = this.isActive();
		Component value = Ui.medium(this.format.apply(this.get.getAsInt()));
		float valueW = Ui.width(value, 0.72F);
		Ui.text(g, value, this.x + this.width - 10 - valueW, this.y + 7, 0.72F,
			!on ? Ui.TEXT_OFF : Theme.lerp(Ui.TEXT_2, 0xFFFFFFFF, this.dragging ? 1.0F : this.hover));
		Ui.text(g, Ui.fit(this.name, Theme.UI_MEDIUM, this.width - valueW - 28, 0.8F), this.x + 9, this.y + 6.5F, 0.8F,
			on ? 0xFFFFFFFF : Ui.TEXT_OFF);
	}

	@Override
	protected void extractControl(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		float value = this.value();
		if (this.shown < 0 || this.dragging && this.moved) {
			this.shown = value;
		} else {
			this.shown = Anim.approach(this.shown, value, 18.0F);
		}
		Ui.slider(g, this.trackX(), this.y + this.height - 14, this.trackW(), 10, this.shown, this.isActive(),
			this.dragging ? 1.0F : this.hover);
	}

	@Override
	public boolean click(double mouseX, double mouseY, int button, boolean doubleClick) {
		// Only the lower half (the track) moves the value, so a click on the name can't change it by accident.
		if (button != 0 || mouseY < this.y + this.height - 18) {
			return false;
		}
		this.dragging = true;
		this.moved = false;
		this.follow(mouseX);
		return true;
	}

	@Override
	public void drag(double mouseX, double mouseY) {
		if (this.dragging) {
			this.moved = true;
			this.follow(mouseX);
		}
	}

	@Override
	public void release(double mouseX, double mouseY) {
		if (this.dragging) {
			this.dragging = false;
			this.done.run();
		}
	}

	private void follow(double mouseX) {
		float t = (float) Math.max(0.0, Math.min(1.0, (mouseX - this.trackX()) / this.trackW()));
		int raw = Math.round(this.min + t * (this.max - this.min));
		int snapped = this.min + Math.round((raw - this.min) / (float) this.step) * this.step;
		snapped = Math.max(this.min, Math.min(this.max, snapped));
		if (snapped != this.get.getAsInt()) {
			this.set.accept(snapped);
		}
	}
}
