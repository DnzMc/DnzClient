package com.dnz.client.gui.config;

import com.dnz.client.gui.Smooth;
import com.dnz.client.gui.Theme;
import com.dnz.client.gui.Ui;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.StringUtil;

/**
 * Single-line text editing for DNZ Config: blinking caret, selection (shift + arrows, mouse drag, Ctrl+A),
 * clipboard (Ctrl+C / X / V), word jumps with Ctrl and sideways scrolling for long text. Drawn with the smooth
 * menu font into a box given by its owner.
 */
public class TextField {
	private static final float SCALE = 0.75F;
	private String value = "";
	private int cursor;
	/** Other end of the selection (same as the cursor when nothing is selected). */
	private int anchor;
	/** Sideways scroll of the text, in font units. */
	private float offset;
	private int maxLength = 64;
	private boolean focused;
	private long caretShownAt;
	private Consumer<String> responder = text -> {
	};
	/** Box of the text area (set when drawn), for mouse clicks. */
	private float textX;

	public TextField value(String value) {
		this.value = value == null ? "" : value;
		this.cursor = Math.min(this.cursor, this.value.length());
		this.anchor = Math.min(this.anchor, this.value.length());
		return this;
	}

	public String value() {
		return this.value;
	}

	public TextField maxLength(int maxLength) {
		this.maxLength = maxLength;
		return this;
	}

	public TextField onChange(Consumer<String> responder) {
		this.responder = responder;
		return this;
	}

	public boolean isFocused() {
		return this.focused;
	}

	public void focus(boolean focused) {
		this.focused = focused;
		if (focused) {
			this.caretShownAt = System.currentTimeMillis();
		} else {
			this.anchor = this.cursor;
		}
	}

	private static Font font() {
		return Minecraft.getInstance().font;
	}

	/** Width of the first [chars] characters in font units. */
	private float widthOf(int chars) {
		return font().width(Ui.regular(this.value.substring(0, Math.max(0, Math.min(chars, this.value.length())))));
	}

	/** Draws the text (or the gray [hint] when empty) inside the box x, y, w, h. */
	public void extract(GuiGraphicsExtractor g, float x, float y, float w, float h, String hint, boolean active) {
		this.textX = x + 5;
		float room = (w - 10) / SCALE;
		float caret = this.widthOf(this.cursor);
		// Keep the caret in view: scroll the text sideways when it would leave the box.
		if (caret - this.offset > room - 1) {
			this.offset = caret - room + 1;
		} else if (caret - this.offset < 0) {
			this.offset = caret;
		}
		this.offset = Math.max(0, Math.min(this.offset, Math.max(0, this.widthOf(this.value.length()) - room + 1)));

		float ty = y + (h - 8 * SCALE) / 2.0F + 0.5F;
		g.enableScissor((int) Math.floor(x + 2), (int) Math.floor(y), (int) Math.ceil(x + w - 2), (int) Math.ceil(y + h));
		g.pose().pushMatrix();
		g.pose().translate(this.textX - this.offset * SCALE, ty);
		g.pose().scale(SCALE, SCALE);
		if (this.focused && this.cursor != this.anchor) {
			float from = this.widthOf(Math.min(this.cursor, this.anchor));
			float to = this.widthOf(Math.max(this.cursor, this.anchor));
			Smooth.rect(g, from, -1.5F, to - from, 11, 1.5F, Theme.withAlpha(Theme.menuAccent() & 0xFFFFFF, 0.45F));
		}
		if (this.value.isEmpty() && !this.focused) {
			g.text(font(), Ui.regular(hint), 0, 0, Ui.TEXT_2, false);
		} else {
			g.text(font(), Ui.regular(this.value), 0, 0, active ? 0xFFFFFFFF : Ui.TEXT_OFF, false);
		}
		// Caret: on for half a second, off for half a second; always on right after typing or moving.
		if (this.focused && (System.currentTimeMillis() - this.caretShownAt) % 1000 < 500) {
			Smooth.rect(g, caret - 0.1F, -1.5F, 1.1F, 11, 0.5F, 0xFFFFFFFF);
		}
		g.pose().popMatrix();
		g.disableScissor();
	}

	/** Character position under the mouse. */
	private int indexAt(double mouseX) {
		float target = (float) ((mouseX - this.textX) / SCALE + this.offset);
		int best = 0;
		float bestGap = Float.MAX_VALUE;
		for (int i = 0; i <= this.value.length(); i++) {
			float gap = Math.abs(this.widthOf(i) - target);
			if (gap < bestGap) {
				bestGap = gap;
				best = i;
			}
		}
		return best;
	}

	public void click(double mouseX, boolean shift, boolean doubleClick) {
		this.focus(true);
		if (doubleClick) {
			this.anchor = 0;
			this.cursor = this.value.length();
			return;
		}
		this.cursor = this.indexAt(mouseX);
		if (!shift) {
			this.anchor = this.cursor;
		}
	}

	/** Mouse dragged after a click: selects from where the click was. */
	public void drag(double mouseX) {
		this.cursor = this.indexAt(mouseX);
		this.caretShownAt = System.currentTimeMillis();
	}

	private String selected() {
		return this.value.substring(Math.min(this.cursor, this.anchor), Math.max(this.cursor, this.anchor));
	}

	/** Puts [text] where the caret is (over the selection), as far as it fits. */
	private void insert(String text) {
		int from = Math.min(this.cursor, this.anchor);
		int to = Math.max(this.cursor, this.anchor);
		String clean = StringUtil.filterText(text);
		int room = Math.max(0, this.maxLength - (this.value.length() - (to - from)));
		if (clean.length() > room) {
			clean = clean.substring(0, room);
		}
		this.set(this.value.substring(0, from) + clean + this.value.substring(to), from + clean.length());
	}

	private void set(String text, int caret) {
		boolean changed = !text.equals(this.value);
		this.value = text;
		this.cursor = Math.max(0, Math.min(caret, text.length()));
		this.anchor = this.cursor;
		this.caretShownAt = System.currentTimeMillis();
		if (changed) {
			this.responder.accept(text);
		}
	}

	/** Start of the word left of [from] (Ctrl + arrow / backspace). */
	private int wordLeft(int from) {
		int i = from;
		while (i > 0 && this.value.charAt(i - 1) == ' ') {
			i--;
		}
		while (i > 0 && this.value.charAt(i - 1) != ' ') {
			i--;
		}
		return i;
	}

	private int wordRight(int from) {
		int i = from;
		int n = this.value.length();
		while (i < n && this.value.charAt(i) != ' ') {
			i++;
		}
		while (i < n && this.value.charAt(i) == ' ') {
			i++;
		}
		return i;
	}

	private void moveTo(int position, boolean select) {
		this.cursor = Math.max(0, Math.min(position, this.value.length()));
		if (!select) {
			this.anchor = this.cursor;
		}
		this.caretShownAt = System.currentTimeMillis();
	}

	/** Editing keys; true when the key was used. */
	public boolean key(KeyEvent event) {
		if (!this.focused) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		boolean shift = event.hasShiftDown();
		boolean word = event.hasControlDown();
		if (event.isSelectAll()) {
			this.anchor = 0;
			this.moveTo(this.value.length(), true);
			return true;
		}
		if (event.isCopy()) {
			mc.keyboardHandler.setClipboard(this.selected());
			return true;
		}
		if (event.isCut()) {
			mc.keyboardHandler.setClipboard(this.selected());
			this.insert("");
			return true;
		}
		if (event.isPaste()) {
			this.insert(mc.keyboardHandler.getClipboard());
			return true;
		}
		if (event.isLeft()) {
			if (this.cursor != this.anchor && !shift) {
				this.moveTo(Math.min(this.cursor, this.anchor), false);
			} else {
				this.moveTo(word ? this.wordLeft(this.cursor) : this.cursor - 1, shift);
			}
			return true;
		}
		if (event.isRight()) {
			if (this.cursor != this.anchor && !shift) {
				this.moveTo(Math.max(this.cursor, this.anchor), false);
			} else {
				this.moveTo(word ? this.wordRight(this.cursor) : this.cursor + 1, shift);
			}
			return true;
		}
		int key = event.key();
		if (key == InputConstants.KEY_HOME) {
			this.moveTo(0, shift);
			return true;
		}
		if (key == InputConstants.KEY_END) {
			this.moveTo(this.value.length(), shift);
			return true;
		}
		if (key == InputConstants.KEY_BACKSPACE) {
			if (this.cursor == this.anchor) {
				this.anchor = word ? this.wordLeft(this.cursor) : Math.max(0, this.cursor - 1);
			}
			this.insert("");
			return true;
		}
		if (key == InputConstants.KEY_DELETE) {
			if (this.cursor == this.anchor) {
				this.anchor = word ? this.wordRight(this.cursor) : Math.min(this.value.length(), this.cursor + 1);
			}
			this.insert("");
			return true;
		}
		return false;
	}

	public boolean typed(CharacterEvent event) {
		if (!this.focused || !event.isAllowedChatCharacter()) {
			return false;
		}
		this.insert(event.codepointAsString());
		return true;
	}
}
