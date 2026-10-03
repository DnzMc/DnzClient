package com.dnz.client.gui.config;

import com.dnz.client.gui.Anim;
import com.dnz.client.gui.Smooth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The scrolling page of a DNZ Config screen: setting rows in two columns (section titles and wide rows take the
 * whole width), smooth scrolling with the mouse wheel or the thin scrollbar, rows clipped to the area. Open
 * dropdown lists are drawn over everything by the menu through {@link #extractOverlay}.
 * Everything is in menu units.
 */
public class OptionList implements GuiEventListener, Renderable, NarratableEntry {
	private static final int GAP = 6;
	private static final float BAR_W = 3;
	/** Mouse position given to rows that must not react (the mouse is elsewhere or a list is open). */
	private static final int NOWHERE = -100_000;
	private final int left;
	private final int top;
	private final int width;
	private final int height;
	private final List<Option> options = new ArrayList<>();
	private int contentHeight;
	/** Scroll in units: where it is drawn now and where the wheel sent it. */
	private float scroll;
	private float scrollTarget;
	/** Row that took the last mouse press (gets drags and the release). */
	private Option pressed;
	/** Row that gets the keyboard (a text box being typed in). */
	private Option keyboard;
	/** Dropdown whose list is open. */
	private ChoiceOption popup;
	private boolean barDrag;
	private double barGrab;
	private Option hovered;
	private boolean focused;
	/** Counts {@link #set} calls: a click can make the page show new rows, then the old row is not kept. */
	private int generation;

	public OptionList(int left, int top, int width, int height) {
		this.left = left;
		this.top = top;
		this.width = width;
		this.height = height;
	}

	/** Shows [rows]. Rows that were already there (same kind and name) keep their animations, and the scroll stays. */
	public OptionList set(List<? extends Option> rows) {
		Map<String, Option> before = new HashMap<>();
		for (Option o : this.options) {
			before.put(o.key(), o);
		}
		this.generation++;
		this.blurKeyboard();
		this.closePopup();
		this.pressed = null;
		this.options.clear();
		for (Option o : rows) {
			Option old = before.get(o.key());
			if (old != null) {
				o.adopt(old);
			}
			this.options.add(o);
		}
		this.layout();
		float max = this.maxScroll();
		this.scroll = Math.min(this.scroll, max);
		this.scrollTarget = Math.min(this.scrollTarget, max);
		return this;
	}

	public void scrollToTop() {
		this.scroll = 0;
		this.scrollTarget = 0;
	}

	private void layout() {
		int colW = (this.width - GAP) / 2;
		int y = 0;
		int col = 0;
		int rowH = 0;
		List<Option> line = new ArrayList<>();
		for (Option o : this.options) {
			boolean full = o.isWide();
			if (full || col == 2) {
				y = this.closeLine(line, y, rowH);
				rowH = 0;
				col = 0;
			}
			if (o instanceof HeaderOption && y > 0) {
				y += 4; // a bit more air above a new section
			}
			o.relX = full ? 0 : col * (colW + GAP);
			o.relY = y;
			o.width = full ? this.width : colW;
			line.add(o);
			rowH = Math.max(rowH, o.rowHeight());
			col = full ? 2 : col + 1;
		}
		y = this.closeLine(line, y, rowH);
		this.contentHeight = Math.max(0, y - GAP);
	}

	/** Gives the rows of one line the same height (so the cards line up); returns where the next line starts. */
	private int closeLine(List<Option> line, int y, int rowH) {
		if (line.isEmpty()) {
			return y;
		}
		for (Option o : line) {
			o.height = rowH;
		}
		line.clear();
		return y + rowH + GAP;
	}

	private float maxScroll() {
		return Math.max(0, this.contentHeight - this.height);
	}

	private void position() {
		for (Option o : this.options) {
			o.place(this.left + o.relX, this.top + o.relY - this.scroll);
		}
	}

	private boolean inArea(double mouseX, double mouseY) {
		return mouseX >= this.left && mouseX < this.left + this.width && mouseY >= this.top && mouseY < this.top + this.height;
	}

	private boolean onBar(double mouseX, double mouseY) {
		return this.maxScroll() > 0 && mouseX >= this.left + this.width + 1 && mouseX < this.left + this.width + 10
			&& mouseY >= this.top && mouseY < this.top + this.height;
	}

	private float barHeight() {
		return Math.max(16, this.height * (float) this.height / Math.max(1, this.contentHeight));
	}

	private float barY() {
		float max = this.maxScroll();
		return this.top + (this.height - this.barHeight()) * (max <= 0 ? 0 : this.scroll / max);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		float max = this.maxScroll();
		this.scrollTarget = Math.max(0, Math.min(max, this.scrollTarget));
		if (!this.barDrag) {
			this.scroll = Anim.approach(this.scroll, this.scrollTarget, 16.0F);
		}
		this.position();
		boolean free = this.inArea(mouseX, mouseY) && !this.barDrag && this.popup == null;
		int mx = free || this.pressed != null ? mouseX : NOWHERE;
		int my = free || this.pressed != null ? mouseY : NOWHERE;
		this.hovered = null;
		g.enableScissor(this.left, this.top, this.left + this.width, this.top + this.height);
		for (Option o : this.options) {
			if (o.y + o.height < this.top || o.y > this.top + this.height) {
				continue;
			}
			boolean over = free && o.contains(mouseX, mouseY);
			if (over) {
				this.hovered = o;
			}
			o.extract(g, mx, my, over || o == this.pressed);
		}
		g.disableScissor();
		if (max > 0) {
			boolean barOver = this.barDrag || this.onBar(mouseX, mouseY);
			Smooth.rect(g, this.left + this.width + 4, this.barY(), BAR_W, this.barHeight(), BAR_W / 2, barOver ? 0x70FFFFFF : 0x40FFFFFF);
		}
	}

	/** Open dropdown lists, over every other part of the menu. */
	public void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		for (Option o : this.options) {
			if (o instanceof ChoiceOption choice && choice.popupVisible()) {
				choice.extractPopup(g, mouseX, mouseY, this.top, this.top + this.height);
			}
		}
	}

	/** Help text of the row under the mouse (none while a list is open or something is dragged). */
	public Component tooltip() {
		return this.popup == null && this.pressed == null && this.hovered != null ? this.hovered.tooltip() : null;
	}

	public Object hoveredRow() {
		return this.hovered;
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return this.inArea(mouseX, mouseY) || this.onBar(mouseX, mouseY) || this.popup != null && this.popup.popupContains(mouseX, mouseY);
	}

	/**
	 * A click somewhere else on the screen: closes an open dropdown (true = the click is used up by that) and stops
	 * typing in a text box.
	 */
	public boolean clickedElsewhere(double mouseX, double mouseY) {
		if (this.isMouseOver(mouseX, mouseY)) {
			return false;
		}
		this.blurKeyboard();
		if (this.popup != null) {
			this.closePopup();
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		this.position();
		if (this.popup != null) {
			ChoiceOption open = this.popup;
			this.popup = null;
			if (!open.choose(mx, my)) {
				open.close(); // a click beside the open list only closes it
			}
			return true;
		}
		if (this.onBar(mx, my)) {
			this.barDrag = true;
			float by = this.barY();
			float bh = this.barHeight();
			this.barGrab = my >= by && my < by + bh ? my - by : bh / 2;
			this.dragBar(my);
			return true;
		}
		if (!this.inArea(mx, my)) {
			return false;
		}
		Option hit = null;
		for (Option o : this.options) {
			if (o.contains(mx, my)) {
				hit = o;
				break;
			}
		}
		if (this.keyboard != null && this.keyboard != hit) {
			this.blurKeyboard();
		}
		int before = this.generation;
		if (hit != null && hit.isActive() && hit.click(mx, my, event.button(), doubleClick) && this.generation == before) {
			this.pressed = hit;
			if (hit.wantsKeyboard()) {
				this.keyboard = hit;
			}
			if (hit instanceof ChoiceOption choice && choice.isOpen()) {
				this.popup = choice;
			}
		}
		return true;
	}

	private void dragBar(double mouseY) {
		float room = this.height - this.barHeight();
		if (room <= 0) {
			return;
		}
		float t = (float) ((mouseY - this.barGrab - this.top) / room);
		this.scrollTarget = Math.max(0, Math.min(1, t)) * this.maxScroll();
		this.scroll = this.scrollTarget;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (this.barDrag) {
			this.dragBar(event.y());
			return true;
		}
		if (this.pressed != null) {
			this.position();
			this.pressed.drag(event.x(), event.y());
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		this.barDrag = false;
		if (this.pressed != null) {
			this.pressed.release(event.x(), event.y());
			this.pressed = null;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY == 0 || !this.inArea(mouseX, mouseY) && !this.onBar(mouseX, mouseY)) {
			return false;
		}
		this.closePopup();
		this.scrollTarget = Math.max(0, Math.min(this.maxScroll(), this.scrollTarget - (float) scrollY * 32));
		return true;
	}

	/** Opens the dropdown list of the row called [name], as if it was clicked; false when there is none. */
	public boolean openChoice(String name) {
		for (Option o : this.options) {
			if (o instanceof ChoiceOption choice && o.name.equals(name)) {
				this.closePopup();
				choice.open();
				this.popup = choice;
				return true;
			}
		}
		return false;
	}

	/** A dropdown list is open and the mouse is on it (nothing under it should react). */
	public boolean popupUnder(double mouseX, double mouseY) {
		return this.popup != null && this.popup.popupContains(mouseX, mouseY);
	}

	/** Escape closes an open list or stops typing first, instead of closing the menu. */
	public boolean holdsEscape() {
		return this.popup != null || this.keyboard != null;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.popup != null && event.isEscape()) {
			this.closePopup();
			return true;
		}
		if (this.keyboard != null) {
			if (event.isEscape()) {
				this.blurKeyboard();
				return true;
			}
			return this.keyboard.key(event);
		}
		return false;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return this.keyboard != null && this.keyboard.typed(event);
	}

	private void closePopup() {
		if (this.popup != null) {
			this.popup.close();
			this.popup = null;
		}
	}

	private void blurKeyboard() {
		if (this.keyboard != null) {
			this.keyboard.blur();
			this.keyboard = null;
		}
	}

	@Override
	public void setFocused(boolean focused) {
		this.focused = focused;
		if (!focused) {
			this.blurKeyboard();
			this.closePopup();
		}
	}

	@Override
	public boolean isFocused() {
		return this.focused;
	}

	@Override
	public ScreenRectangle getRectangle() {
		return new ScreenRectangle(this.left, this.top, this.width, this.height);
	}

	@Override
	public NarrationPriority narrationPriority() {
		return NarrationPriority.NONE;
	}

	@Override
	public void updateNarration(NarrationElementOutput output) {
	}
}
