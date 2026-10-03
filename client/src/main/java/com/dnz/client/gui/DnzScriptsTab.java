package com.dnz.client.gui;

import com.dnz.client.L;
import com.dnz.client.compat.Compat;
import com.dnz.client.script.ScriptManager;
import com.dnz.client.script.ScriptMod;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** DNZ menu "Script Mods" tab: DNZ Script mods with on/off, errors in red (with the line number) and reload. */
public class DnzScriptsTab extends DnzMenuScreen {
	private static final int ROW_H = 30;
	private static final int TOGGLE_W = 64;

	private int page;
	private int rows;
	private Component status;

	public DnzScriptsTab(Screen parent) {
		super(Tab.SCRIPTS, parent);
	}

	private int listTop() {
		return this.cy + 26;
	}

	@Override
	protected void initContent() {
		List<ScriptMod> mods = ScriptManager.mods();
		this.rows = Math.max(2, (this.ch - 26 - 22) / ROW_H);
		int pages = Math.max(1, (mods.size() + this.rows - 1) / this.rows);
		this.page = Math.min(this.page, pages - 1);

		this.addRenderableWidget(new DnzButton(this.cx, this.cy, 100, 20, Component.literal(L.t("scripts.reload")), () -> {
			ScriptManager.reloadAll(false);
			long running = ScriptManager.mods().stream().filter(m -> m.status() == ScriptMod.Status.RUNNING).count();
			this.status = Component.literal(L.t("scripts.reloaded_status", running)).withColor(0x7CE38B);
			this.rebuildWidgets();
		}).selected(true));
		this.addRenderableWidget(new DnzButton(this.cx + 104, this.cy, 90, 20, Component.literal(L.t("open_folder")),
			() -> Compat.openFolder(ScriptManager.folder())));

		int from = this.page * this.rows;
		for (int i = from; i < Math.min(mods.size(), from + this.rows); i++) {
			ScriptMod mod = mods.get(i);
			int y = this.listTop() + (i - from) * ROW_H;
			String label = switch (mod.status()) {
				case RUNNING -> L.t("on");
				case OFF -> L.t("off");
				case ERROR -> L.t("scripts.retry");
			};
			DnzButton toggle = new DnzButton(this.cx + this.cw - TOGGLE_W, y + 5, TOGGLE_W, 18, Component.literal(label), () -> {
				ScriptManager.setEnabled(mod, mod.status() != ScriptMod.Status.RUNNING);
				this.rebuildWidgets();
			}).selected(mod.status() == ScriptMod.Status.RUNNING);
			if (mod.error() != null) {
				this.tip(toggle, Component.literal(errorText(mod)));
			}
			this.addRenderableWidget(toggle);
		}

		if (pages > 1) {
			int y = this.cy + this.ch - 16;
			DnzButton prev = new DnzButton(this.cx + this.cw / 2 - 50, y, 30, 14, Component.literal("<"), () -> {
				this.page--;
				this.rebuildWidgets();
			});
			prev.active = this.page > 0;
			DnzButton next = new DnzButton(this.cx + this.cw / 2 + 20, y, 30, 14, Component.literal(">"), () -> {
				this.page++;
				this.rebuildWidgets();
			});
			next.active = this.page < pages - 1;
			this.addRenderableWidget(prev);
			this.addRenderableWidget(next);
		}
	}

	private static String errorText(ScriptMod mod) {
		return (mod.errorLine() > 0 ? L.t("script.line", mod.errorLine()) + ": " : "") + mod.error();
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		List<ScriptMod> mods = ScriptManager.mods();
		long running = mods.stream().filter(m -> m.status() == ScriptMod.Status.RUNNING).count();
		String count = L.t("scripts.count", mods.size(), running);
		g.text(this.font, count, this.cx + this.cw - this.font.width(count), this.cy + 6, Theme.MUTED);

		if (mods.isEmpty()) {
			g.textWithWordWrap(this.font, Component.literal(L.t("scripts.empty")), this.cx, this.listTop() + 6, this.cw, Theme.MUTED);
			return;
		}
		int textW = this.cw - TOGGLE_W - 16;
		int from = this.page * this.rows;
		for (int i = from; i < Math.min(mods.size(), from + this.rows); i++) {
			ScriptMod mod = mods.get(i);
			int y = this.listTop() + (i - from) * ROW_H;
			int bar = switch (mod.status()) {
				case RUNNING -> 0xFF7CE38B;
				case OFF -> 0xFF6A6F7A;
				case ERROR -> 0xFFFF6B6B;
			};
			g.fill(this.cx, y, this.cx + this.cw - TOGGLE_W - 4, y + ROW_H - 2, 0x30000000);
			g.fill(this.cx, y, this.cx + 2, y + ROW_H - 2, bar);

			String title = mod.name + "  v" + mod.version;
			g.text(this.font, this.font.plainSubstrByWidth(title, textW), this.cx + 8, y + 4,
				mod.status() == ScriptMod.Status.OFF ? 0xFF8A8F9C : 0xFFFFFFFF);
			if (mod.error() != null) {
				g.text(this.font, this.font.plainSubstrByWidth(errorText(mod), textW), this.cx + 8, y + 16, 0xFFFF6B6B);
			} else {
				String info = mod.author.isEmpty() ? mod.description : L.t("scripts.by", mod.author) + (mod.description.isEmpty() ? "" : "  •  " + mod.description);
				g.text(this.font, this.font.plainSubstrByWidth(info, textW), this.cx + 8, y + 16, Theme.MUTED);
			}
		}
		boolean paged = mods.size() > this.rows; // the page arrows use the bottom line then
		if (this.status == null && !paged) {
			g.text(this.font, this.font.plainSubstrByWidth(L.t("scripts.hint"), this.cw), this.cx, this.cy + this.ch - 10, 0xFF6A7080);
		}
	}

	@Override
	protected void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		if (this.status != null) {
			g.centeredText(this.font, this.status, this.width / 2, this.py + this.panelH + 6, 0xFFFFFFFF);
		}
	}
}
