package com.dnz.client.gui.config;

import com.dnz.client.gui.Anim;
import com.dnz.client.gui.Smooth;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Pick one color from a row of round swatches; the chosen one gets a white ring. */
public class ColorOption extends Option {
	private static final float DOT = 11;
	private static final float GAP = 5;
	private final int[] colors;
	private final IntSupplier get;
	private final IntConsumer set;
	/** Per swatch: ring (chosen) and hover animation. */
	private final float[] ring;
	private final float[] lift;

	public ColorOption(String name, int[] colors, IntSupplier get, IntConsumer set) {
		super(name);
		this.colors = colors.clone();
		this.get = get;
		this.set = set;
		this.ring = new float[colors.length];
		this.lift = new float[colors.length];
		int chosen = get.getAsInt();
		if (chosen >= 0 && chosen < colors.length) {
			this.ring[chosen] = 1;
		}
	}

	@Override
	protected float controlWidth() {
		return this.colors.length * DOT + (this.colors.length - 1) * GAP;
	}

	@Override
	protected void adopt(Option old) {
		super.adopt(old);
		if (old instanceof ColorOption before && before.colors.length == this.colors.length) {
			System.arraycopy(before.ring, 0, this.ring, 0, this.ring.length);
			System.arraycopy(before.lift, 0, this.lift, 0, this.lift.length);
		}
	}

	private float dotX(int i) {
		return this.x + this.width - 9 - this.controlWidth() + i * (DOT + GAP);
	}

	private float dotY() {
		return this.y + (this.height - DOT) / 2.0F;
	}

	private int dotAt(double mouseX, double mouseY) {
		for (int i = 0; i < this.colors.length; i++) {
			if (inside(mouseX, mouseY, this.dotX(i) - GAP / 2, this.dotY() - 3, DOT + GAP, DOT + 6)) {
				return i;
			}
		}
		return -1;
	}

	@Override
	protected void extractControl(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int chosen = this.get.getAsInt();
		int over = this.isActive() ? this.dotAt(mouseX, mouseY) : -1;
		float y = this.dotY();
		for (int i = 0; i < this.colors.length; i++) {
			this.ring[i] = Anim.approach(this.ring[i], i == chosen ? 1.0F : 0.0F, 14.0F);
			this.lift[i] = Anim.approach(this.lift[i], i == over ? 1.0F : 0.0F, 16.0F);
			float grow = this.lift[i] * 1.2F;
			float x = this.dotX(i);
			if (this.ring[i] > 0.01F) {
				float r = 2.2F * this.ring[i];
				Smooth.rect(g, x - r, y - r, DOT + 2 * r, DOT + 2 * r, DOT / 2 + r, (Math.round(0xFF * this.ring[i]) << 24) | 0xFFFFFF);
				Smooth.rect(g, x - r + 1.1F, y - r + 1.1F, DOT + 2 * r - 2.2F, DOT + 2 * r - 2.2F, DOT / 2 + r - 1.1F, 0xFF1E2830);
			}
			Smooth.shadow(g, x - grow, y - grow + 0.6F, DOT + 2 * grow, DOT + 2 * grow, DOT / 2 + grow, 2, 0x50000000);
			Smooth.rect(g, x - grow, y - grow, DOT + 2 * grow, DOT + 2 * grow, DOT / 2 + grow, 0xFF000000 | this.colors[i]);
		}
	}

	@Override
	public boolean click(double mouseX, double mouseY, int button, boolean doubleClick) {
		int i = this.dotAt(mouseX, mouseY);
		if (button != 0 || i < 0) {
			return false;
		}
		if (i != this.get.getAsInt()) {
			this.set.accept(i);
		}
		clickSound();
		return true;
	}
}
