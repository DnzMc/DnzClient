package com.dnz.client.gui;

import com.dnz.client.Friends;
import com.dnz.client.L;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** DNZ menu "Friends" page: the friend list kept on this computer, and who of them is on the current server. */
public class DnzFriendsTab extends DnzMenuScreen {
	private static final int ROW = 22;
	private EditBox name;
	private String typed = "";
	private String message;
	private int scroll;

	public DnzFriendsTab(Screen parent) {
		super(Tab.FRIENDS, parent);
	}

	@Override
	protected void initContent() {
		int addW = 60;
		this.name = new EditBox(this.font, this.cx, this.cy, this.cw - addW - 4, 18, Component.literal(L.t("friends.name")));
		this.name.setMaxLength(16);
		this.name.setHint(Component.literal(L.t("friends.name")));
		this.name.setValue(this.typed);
		this.name.setResponder(text -> this.typed = text);
		this.addRenderableWidget(this.name);
		this.addRenderableWidget(new DnzButton(this.cx + this.cw - addW, this.cy, addW, 18, Component.literal(L.t("friends.add")), this::add).selected(true));
		int y = this.cy + 40;
		List<String> list = Friends.list();
		for (int i = this.scroll; i < list.size() && y + ROW <= this.cy + this.ch; i++) {
			String n = list.get(i);
			this.addRenderableWidget(new DnzButton(this.cx + this.cw - 62, y, 62, 18, Component.literal(L.t("friends.remove")), () -> {
				Friends.remove(n);
				this.rebuildWidgets();
			}));
			y += ROW;
		}
	}

	private void add() {
		if (Friends.add(this.typed)) {
			this.message = null;
			this.typed = "";
		} else {
			this.message = L.t("friends.bad_name");
		}
		this.rebuildWidgets();
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (this.name != null && this.name.isFocused() && (event.key() == 257 || event.key() == 335)) { // Enter
			this.add();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		if (this.message != null) {
			g.text(this.font, Theme.smooth(this.message), this.cx, this.cy + 24, 0xFFFF7A7A, false);
		}
		List<String> list = Friends.list();
		int y = this.cy + 40;
		g.text(this.font, Theme.smooth(L.t("friends.list") + " (" + list.size() + ")"), this.cx, y - 11, 0xFF9AA3B5, false);
		if (list.isEmpty()) {
			g.text(this.font, Theme.smooth(L.t("friends.none")), this.cx, y + 4, 0xFF6F7787, false);
			g.text(this.font, Theme.smooth(L.t("friends.how")), this.cx, y + 18, 0xFF6F7787, false);
		}
		for (int i = this.scroll; i < list.size() && y + ROW <= this.cy + this.ch; i++) {
			String n = list.get(i);
			boolean here = Friends.here(n);
			Smooth.rect(g, this.cx, y, this.cw, 18, 4, 0xFF1E2230);
			Smooth.rect(g, this.cx + 7, y + 6.5F, 5, 5, 2.5F, here ? 0xFF55E36B : 0xFF5A6070);
			g.text(this.font, Theme.smooth(n), this.cx + 18, y + 5, 0xFFFFFFFF, false);
			String where = here ? L.t("friends.on_server") : L.t("friends.not_here");
			g.text(this.font, Theme.smooth(where), this.cx + 18 + this.font.width(n) + 8, y + 5, here ? 0xFF55E36B : 0xFF9AA3B5, false);
			y += ROW;
		}
	}

	@Override
	protected boolean scrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int max = Math.max(0, Friends.list().size() - (this.ch - 40) / ROW);
		int next = Math.max(0, Math.min(max, this.scroll - (int) Math.signum(scrollY)));
		if (next != this.scroll) {
			this.scroll = next;
			this.rebuildWidgets();
			return true;
		}
		return super.scrolled(mouseX, mouseY, scrollX, scrollY);
	}
}
