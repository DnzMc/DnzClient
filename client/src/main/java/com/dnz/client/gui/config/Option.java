package com.dnz.client.gui.config;

import com.dnz.client.gui.Anim;
import com.dnz.client.gui.Smooth;
import com.dnz.client.gui.Theme;
import com.dnz.client.gui.Ui;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * One setting of a DNZ Config page: a glass row with the name (and a short gray note) on the left and its control
 * on the right. Rows are laid out, scrolled and clicked by {@link OptionList}; everything is in menu units.
 * Values are read through suppliers every frame, so a row always shows the current setting.
 */
public abstract class Option {
	protected final String name;
	protected Supplier<String> note = () -> "";
	protected Component tooltip;
	protected boolean wide;
	/** Small accent bar on the left edge (e.g. a changed setting that is not applied yet). */
	protected boolean marked;
	private BooleanSupplier active = () -> true;

	/** Place inside the list (relative to its content), set by the list's layout. */
	int relX;
	int relY;
	/** Where the row is on screen this frame (moves with scrolling). */
	protected float x;
	protected float y;
	protected int width;
	protected int height;
	/** Mouse-over animation, 0..1. */
	protected float hover;

	protected Option(String name) {
		this.name = name;
	}

	/** Short gray line under the name. */
	public Option note(String note) {
		String text = note == null ? "" : note;
		this.note = () -> text;
		return this;
	}

	/** Note that can change (e.g. the name of the chosen color). */
	public Option note(Supplier<String> note) {
		this.note = note;
		return this;
	}

	/** Longer help shown after the mouse rests on the row. */
	public Option tooltip(Component tooltip) {
		this.tooltip = tooltip;
		return this;
	}

	/** The row takes the whole width instead of one of the two columns. */
	public Option wide() {
		this.wide = true;
		return this;
	}

	/** Shows the accent bar on the left edge. */
	public Option marked(boolean marked) {
		this.marked = marked;
		return this;
	}

	/** The row is grayed out and ignores clicks while [active] says no. */
	public Option activeIf(BooleanSupplier active) {
		this.active = active;
		return this;
	}

	public boolean isActive() {
		return this.active.getAsBoolean();
	}

	public boolean isWide() {
		return this.wide;
	}

	public Component tooltip() {
		return this.tooltip;
	}

	/** Height of the row in menu units. */
	public int rowHeight() {
		return this.note.get().isEmpty() ? 24 : 30;
	}

	/** Width the control takes on the right; the name and the note are cut before it. */
	protected float controlWidth() {
		return 0;
	}

	/** Identity used to keep animations running when a page rebuilds its rows. */
	protected String key() {
		return this.getClass().getSimpleName() + ":" + this.name;
	}

	/** Takes over the animation state of the row this one replaces. */
	protected void adopt(Option old) {
		this.hover = old.hover;
	}

	/** Keyboard input goes to this row after a click (text boxes). */
	protected boolean wantsKeyboard() {
		return false;
	}

	void place(float x, float y) {
		this.x = x;
		this.y = y;
	}

	public boolean contains(double mouseX, double mouseY) {
		return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
	}

	protected static boolean inside(double mouseX, double mouseY, float x, float y, float w, float h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	/** Draws the row: the card, the name and note, then the control. */
	public void extract(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered) {
		this.hover = Anim.approach(this.hover, hovered && this.isActive() ? 1.0F : 0.0F, 14.0F);
		Ui.glass(g, this.x, this.y, this.width, this.height, 5, Theme.lerp(Ui.CARD_BG, Ui.CARD_HOVER, this.hover));
		if (this.marked) {
			Smooth.rect(g, this.x + 2.5F, this.y + 6, 2, this.height - 12, 1, Theme.menuAccent());
		}
		this.extractLabel(g);
		this.extractControl(g, mouseX, mouseY);
	}

	/** Name (and note) on the left, cut with "…" so they never run under the control. */
	protected void extractLabel(GuiGraphicsExtractor g) {
		float room = this.width - this.controlWidth() - 24;
		String note = this.note.get();
		boolean on = this.isActive();
		float nameY = note.isEmpty() ? this.y + (this.height - 8 * 0.8F) / 2.0F + 0.5F : this.y + 6;
		Ui.text(g, Ui.fit(this.name, Theme.UI_MEDIUM, room, 0.8F), this.x + 9, nameY, 0.8F, on ? 0xFFFFFFFF : Ui.TEXT_OFF);
		if (!note.isEmpty()) {
			Ui.text(g, Ui.fit(note, Theme.UI_REGULAR, room, 0.62F), this.x + 9, this.y + 17.5F, 0.62F, on ? Ui.TEXT_2 : 0xFF5C616D);
		}
	}

	protected abstract void extractControl(GuiGraphicsExtractor g, int mouseX, int mouseY);

	/** Mouse pressed on the row; true when the row used it. */
	public boolean click(double mouseX, double mouseY, int button, boolean doubleClick) {
		return false;
	}

	/** Mouse moved with the button held, after this row took the press. */
	public void drag(double mouseX, double mouseY) {
	}

	public void release(double mouseX, double mouseY) {
	}

	public boolean key(KeyEvent event) {
		return false;
	}

	public boolean typed(CharacterEvent event) {
		return false;
	}

	/** Lost the keyboard (clicked somewhere else). */
	public void blur() {
	}

	protected static void clickSound() {
		AbstractWidget.playButtonClickSound(Minecraft.getInstance().getSoundManager());
	}
}
