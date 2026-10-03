package com.dnz.client.gui.config;

import com.dnz.client.gui.Anim;
import com.dnz.client.gui.Smooth;
import com.dnz.client.gui.Theme;
import com.dnz.client.gui.Ui;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * One of a few named values: a pill with the chosen value and an arrow; clicking it unfolds a list to pick from
 * (below the pill, or above it near the bottom of the page). The list is drawn over the other rows by OptionList.
 */
public class ChoiceOption extends Option {
	private static final float PILL_H = 15;
	private static final float ITEM_H = 14;
	private static final float TEXT = 0.72F;
	private final List<String> labels;
	private final IntSupplier get;
	private final IntConsumer set;
	private boolean open;
	/** Unfold animation 0..1 (also runs backwards after closing). */
	private float unfold;
	private float pillHover;
	private float pillW;
	private float pillX;
	private float pillY;
	/** Where the open list is (set when drawn). */
	private float listX;
	private float listY;
	private float listW;
	private float listH;
	private boolean upward;

	public ChoiceOption(String name, List<String> labels, IntSupplier get, IntConsumer set) {
		super(name);
		this.labels = List.copyOf(labels);
		this.get = get;
		this.set = set;
	}

	@Override
	protected float controlWidth() {
		return this.pillW;
	}

	@Override
	protected void adopt(Option old) {
		super.adopt(old);
		if (old instanceof ChoiceOption before) {
			this.pillHover = before.pillHover;
		}
	}

	private int current() {
		int i = this.get.getAsInt();
		return i >= 0 && i < this.labels.size() ? i : 0;
	}

	private float widestLabel() {
		float widest = 0;
		for (String label : this.labels) {
			widest = Math.max(widest, Ui.width(Ui.medium(label), TEXT));
		}
		return widest;
	}

	@Override
	public void extract(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered) {
		this.pillW = Math.max(56, Math.min(this.widestLabel() + 26, this.width * 0.55F));
		this.pillX = this.x + this.width - 9 - this.pillW;
		this.pillY = this.y + (this.height - PILL_H) / 2.0F;
		super.extract(g, mouseX, mouseY, hovered);
	}

	@Override
	protected void extractControl(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		boolean on = this.isActive();
		boolean over = on && inside(mouseX, mouseY, this.pillX, this.pillY, this.pillW, PILL_H);
		this.pillHover = Anim.approach(this.pillHover, over || this.open ? 1.0F : 0.0F, 14.0F);
		int edge = this.open ? Theme.withAlpha(Theme.menuAccent() & 0xFFFFFF, 0.85F) : Theme.lerp(0x12FFFFFF, 0x2AFFFFFF, this.pillHover);
		Smooth.rect(g, this.pillX, this.pillY, this.pillW, PILL_H, 4, edge);
		Smooth.rect(g, this.pillX + 0.75F, this.pillY + 0.75F, this.pillW - 1.5F, PILL_H - 1.5F, 3.25F,
			Theme.lerp(Ui.COMPONENT_BG, Ui.COMPONENT_HOVER, this.pillHover));
		int color = on ? 0xFFFFFFFF : Ui.TEXT_OFF;
		Ui.text(g, Ui.fit(this.labels.get(this.current()), Theme.UI_MEDIUM, this.pillW - 22, TEXT),
			this.pillX + 7, this.pillY + (PILL_H - 8 * TEXT) / 2.0F + 0.5F, TEXT, color);
		Ui.chevron(g, this.pillX + this.pillW - 9, this.pillY + PILL_H / 2.0F, 6, this.open, on ? 0xFFC9CFDA : Ui.TEXT_OFF);
	}

	@Override
	public boolean click(double mouseX, double mouseY, int button, boolean doubleClick) {
		if (button != 0) {
			return false;
		}
		this.open = !this.open;
		clickSound();
		return true;
	}

	public boolean isOpen() {
		return this.open;
	}

	public void open() {
		this.open = true;
	}

	public void close() {
		this.open = false;
	}

	/** Still on screen (open, or folding away). */
	public boolean popupVisible() {
		return this.open || this.unfold > 0.01F;
	}

	public boolean popupContains(double mouseX, double mouseY) {
		return this.open && inside(mouseX, mouseY, this.listX, this.listY, this.listW, this.listH);
	}

	/** The click picked a value from the open list (true) or missed it. */
	public boolean choose(double mouseX, double mouseY) {
		if (!this.popupContains(mouseX, mouseY)) {
			return false;
		}
		int index = (int) ((mouseY - this.listY - 2) / ITEM_H);
		if (index >= 0 && index < this.labels.size()) {
			if (index != this.current()) {
				this.set.accept(index);
			}
			clickSound();
		}
		this.open = false;
		return true;
	}

	/** Draws the unfolded list, kept between [top] and [bottom] (the page's visible area). */
	public void extractPopup(GuiGraphicsExtractor g, int mouseX, int mouseY, float top, float bottom) {
		this.unfold = Anim.approach(this.unfold, this.open ? 1.0F : 0.0F, 20.0F);
		this.listW = Math.max(this.pillW, this.widestLabel() + 30);
		this.listH = this.labels.size() * ITEM_H + 4;
		this.listX = this.pillX + this.pillW - this.listW;
		this.upward = this.pillY + PILL_H + 3 + this.listH > bottom && this.pillY - 3 - this.listH >= top;
		this.listY = this.upward ? this.pillY - 3 - this.listH : this.pillY + PILL_H + 3;
		float shown = Anim.easeOut(this.unfold);
		if (shown <= 0.01F) {
			return;
		}
		// Unfolds from the pill: only the part that is out so far is drawn.
		float visible = this.listH * shown + 6;
		int clipTop = (int) Math.floor(this.upward ? this.listY + this.listH - visible : this.listY - 6);
		int clipBottom = (int) Math.ceil(this.upward ? this.listY + this.listH + 6 : this.listY + visible);
		g.enableScissor((int) Math.floor(this.listX - 8), clipTop, (int) Math.ceil(this.listX + this.listW + 8), clipBottom);
		Smooth.shadow(g, this.listX, this.listY + 2, this.listW, this.listH, 5, 8, 0x80000000);
		Smooth.rect(g, this.listX, this.listY, this.listW, this.listH, 5, 0x30FFFFFF);
		Smooth.rect(g, this.listX + 0.75F, this.listY + 0.75F, this.listW - 1.5F, this.listH - 1.5F, 4.25F, 0xFA171D24);
		int chosen = this.current();
		int accent = Theme.menuAccent();
		for (int i = 0; i < this.labels.size(); i++) {
			float iy = this.listY + 2 + i * ITEM_H;
			boolean over = this.open && inside(mouseX, mouseY, this.listX + 2, iy, this.listW - 4, ITEM_H);
			if (over) {
				Smooth.rect(g, this.listX + 2, iy, this.listW - 4, ITEM_H, 3, 0x16FFFFFF);
			}
			int color = i == chosen ? Theme.lerp(accent, 0xFFFFFFFF, 0.25F) : over ? 0xFFFFFFFF : 0xFFC4CAD4;
			Ui.text(g, Ui.fit(this.labels.get(i), Theme.UI_MEDIUM, this.listW - 26, TEXT), this.listX + 8,
				iy + (ITEM_H - 8 * TEXT) / 2.0F + 0.5F, TEXT, color);
			if (i == chosen) {
				Ui.check(g, this.listX + this.listW - 10, iy + ITEM_H / 2.0F, 7, color);
			}
		}
		g.disableScissor();
	}
}
