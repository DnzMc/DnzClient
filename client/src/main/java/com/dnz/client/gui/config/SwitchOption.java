package com.dnz.client.gui.config;

import com.dnz.client.gui.Anim;
import com.dnz.client.gui.Ui;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** On/off setting: a sliding switch on the right. A click anywhere on the row flips it. */
public class SwitchOption extends Option {
	private static final float W = 22;
	private static final float H = 12;
	private final BooleanSupplier get;
	private final Consumer<Boolean> set;
	/** Knob position 0 (off) .. 1 (on), slides after a click. */
	private float knob = -1;

	public SwitchOption(String name, BooleanSupplier get, Consumer<Boolean> set) {
		super(name);
		this.get = get;
		this.set = set;
	}

	@Override
	protected float controlWidth() {
		return W;
	}

	@Override
	protected void adopt(Option old) {
		super.adopt(old);
		if (old instanceof SwitchOption before) {
			this.knob = before.knob;
		}
	}

	@Override
	protected void extractControl(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		boolean on = this.get.getAsBoolean();
		if (this.knob < 0) {
			this.knob = on ? 1.0F : 0.0F;
		}
		this.knob = Anim.approach(this.knob, on ? 1.0F : 0.0F, 16.0F);
		float sx = this.x + this.width - W - 9;
		float sy = this.y + (this.height - H) / 2.0F;
		Ui.switchControl(g, sx, sy, W, H, Anim.easeOut(this.knob), this.hover, this.isActive());
	}

	@Override
	public boolean click(double mouseX, double mouseY, int button, boolean doubleClick) {
		if (button != 0) {
			return false;
		}
		this.set.accept(!this.get.getAsBoolean());
		clickSound();
		return true;
	}
}
