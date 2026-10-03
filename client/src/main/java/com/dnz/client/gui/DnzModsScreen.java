package com.dnz.client.gui;

import com.dnz.client.L;
import com.dnz.client.mods.ModConfigScreens;
import com.dnz.client.mods.ModManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** DNZ menu "Mods" tab: toggle installed mods and install new ones from Modrinth. */
public class DnzModsScreen extends DnzMenuScreen {
	private static final int ROW_H = 22;

	private boolean browseTab;
	private int page;
	private int rows;

	private List<ModManager.LocalMod> local = new ArrayList<>();
	private List<ModManager.RemoteMod> results = new ArrayList<>();
	private final Set<String> installing = new HashSet<>();
	private String query = "";
	private boolean searching;
	private boolean changed;
	private EditBox searchBox;

	private Component status = Component.literal(L.t("mods.restart_hint"));
	private int statusColor = Theme.MUTED;

	public DnzModsScreen(Screen parent) {
		super(Tab.MODS, parent);
	}

	private int listTop() {
		return this.cy + 22 + (this.browseTab ? 24 : 0);
	}

	private int visibleRows() {
		return this.browseTab ? this.rows - 1 : this.rows;
	}

	@Override
	protected void initContent() {
		this.rows = Math.max(3, (this.ch - 62) / ROW_H);
		int x = this.cx;
		int w = this.cw;

		// Sub tabs
		int tabW = (w - 4) / 2;
		this.addRenderableWidget(new DnzButton(x, this.cy, tabW, 18, Component.literal(L.t("mods.installed")), () -> this.switchTab(false))
			.selected(!this.browseTab));
		this.addRenderableWidget(new DnzButton(x + tabW + 4, this.cy, w - tabW - 4, 18, Component.literal(L.t("mods.browse")), () -> this.switchTab(true))
			.selected(this.browseTab));

		if (this.browseTab) {
			int searchY = this.cy + 22;
			this.searchBox = new EditBox(this.font, x + 1, searchY + 1, w - 70, 18, Component.literal(L.t("mods.search")));
			this.searchBox.setMaxLength(64);
			this.searchBox.setValue(this.query);
			this.searchBox.setHint(Component.literal(L.t("mods.search_hint")).withColor(0x7A7F8C));
			this.searchBox.setResponder(value -> this.query = value);
			this.addRenderableWidget(this.searchBox);
			this.addRenderableWidget(new DnzButton(x + w - 64, searchY, 64, 20, Component.literal(this.searching ? "..." : L.t("mods.search")), this::runSearch));
			this.buildRemoteRows(x, this.listTop(), w, this.visibleRows());
		} else {
			this.local = ModManager.listLocal();
			this.buildLocalRows(x, this.listTop(), w, this.visibleRows());
		}

		// Bottom bar
		int by = this.cy + this.ch - 20;
		this.addRenderableWidget(new DnzButton(x, by, 90, 20, Component.literal(L.t("open_folder")),
			() -> com.dnz.client.compat.Compat.openFolder(ModManager.modsDir())));
		if (this.changed) {
			this.addRenderableWidget(new DnzButton(x + 94, by, 130, 20, Component.literal(L.t("mods.quit")), this.minecraft::stop));
		}
	}

	private void switchTab(boolean browse) {
		this.browseTab = browse;
		this.page = 0;
		this.rebuildWidgets();
		if (browse && this.results.isEmpty() && !this.searching) {
			this.runSearch();
		}
	}

	private int pageCount(int size, int perPage) {
		return Math.max(1, (size + perPage - 1) / perPage);
	}

	private void addPager(int x, int y, int w, int size, int perPage) {
		int pages = this.pageCount(size, perPage);
		if (pages <= 1) {
			return;
		}
		this.page = Math.min(this.page, pages - 1);
		DnzButton prev = new DnzButton(x + w / 2 - 70, y, 30, 14, Component.literal("<"), () -> {
			this.page--;
			this.rebuildWidgets();
		});
		prev.active = this.page > 0;
		DnzButton next = new DnzButton(x + w / 2 + 40, y, 30, 14, Component.literal(">"), () -> {
			this.page++;
			this.rebuildWidgets();
		});
		next.active = this.page < pages - 1;
		this.addRenderableWidget(prev);
		this.addRenderableWidget(next);
	}

	private void buildLocalRows(int x, int top, int w, int rows) {
		this.page = Math.min(this.page, this.pageCount(this.local.size(), rows) - 1);
		int from = this.page * rows;
		for (int i = from; i < Math.min(this.local.size(), from + rows); i++) {
			ModManager.LocalMod mod = this.local.get(i);
			int y = top + (i - from) * ROW_H;
			String label = mod.locked() ? L.t("mods.required") : mod.enabled() ? L.t("on") : L.t("off");
			DnzButton toggle = new DnzButton(x + w - 70, y + 1, 70, 18, Component.literal(label), () -> {
				String error = ModManager.toggle(mod);
				if (error != null) {
					this.setStatus(error, 0xFFFF6B6B);
				} else {
					this.changed = true;
					this.setStatus(L.t(mod.enabled() ? "mods.disabled" : "mods.enabled", mod.name()), 0xFF7CE38B);
				}
				this.rebuildWidgets();
			}).selected(mod.enabled() && !mod.locked());
			toggle.active = !mod.locked();
			this.addRenderableWidget(toggle);

			// Settings (menu icon) button
			Optional<Function<Screen, Screen>> config = mod.enabled() ? ModConfigScreens.find(mod.id()) : Optional.empty();
			DnzButton settings = new DnzButton(x + w - 92, y + 1, 18, 18, Component.literal("≡"), () -> {
				Screen screen = config.map(f -> f.apply(this)).orElse(null);
				if (screen != null) {
					this.minecraft.gui.setScreen(screen);
				} else {
					this.setStatus(L.t("mods.no_settings_named", mod.name()), Theme.MUTED);
				}
			});
			settings.active = config.isPresent();
			this.tip(settings, Component.literal(config.isPresent()
				? L.t("mods.open_settings", mod.name())
				: !mod.enabled() ? L.t("mods.enable_first")
				: ModConfigScreens.modMenuLoaded() ? L.t("mods.no_settings") : L.t("mods.need_modmenu")));
			this.addRenderableWidget(settings);
		}
		this.addPager(x, top + rows * ROW_H + 2, w, this.local.size(), rows);
	}

	private void buildRemoteRows(int x, int top, int w, int rows) {
		this.page = Math.min(this.page, this.pageCount(this.results.size(), rows) - 1);
		int from = this.page * rows;
		for (int i = from; i < Math.min(this.results.size(), from + rows); i++) {
			ModManager.RemoteMod mod = this.results.get(i);
			int y = top + (i - from) * ROW_H;
			boolean installed = ModManager.isInstalled(mod);
			boolean busy = this.installing.contains(mod.projectId());
			String label = installed ? L.t("mods.installed_label") : busy ? L.t("mods.installing_label") : L.t("mods.install");
			DnzButton button = new DnzButton(x + w - 70, y + 1, 70, 18, Component.literal(label), () -> this.install(mod)).selected(!installed && !busy);
			button.active = !installed && !busy;
			this.addRenderableWidget(button);
		}
		this.addPager(x, top + rows * ROW_H + 2, w, this.results.size(), rows);
	}

	private void runSearch() {
		if (this.searching) {
			return;
		}
		this.searching = true;
		this.setStatus(L.t("mods.searching"), Theme.accent());
		this.rebuildWidgets();
		ModManager.search(this.query.trim()).whenComplete((list, error) -> this.minecraft.execute(() -> {
			this.searching = false;
			if (error != null) {
				this.setStatus(L.t("mods.search_failed"), 0xFFFF6B6B);
			} else {
				this.results = list;
				this.page = 0;
				this.setStatus(list.isEmpty() ? L.t("mods.no_results") : L.t("mods.found", list.size(), ModManager.minecraftVersion()), Theme.MUTED);
			}
			this.rebuildWidgets();
		}));
	}

	private void install(ModManager.RemoteMod mod) {
		this.installing.add(mod.projectId());
		this.setStatus(L.t("mods.installing", mod.title()), Theme.accent());
		this.rebuildWidgets();
		ModManager.install(mod).thenAccept(error -> this.minecraft.execute(() -> {
			this.installing.remove(mod.projectId());
			if (error == null) {
				this.changed = true;
				this.setStatus(L.t("mods.install_done", mod.title()), 0xFF7CE38B);
			} else {
				this.setStatus(mod.title() + ": " + error, 0xFFFF6B6B);
			}
			this.rebuildWidgets();
		}));
	}

	private void setStatus(String text, int color) {
		this.status = Component.literal(text);
		this.statusColor = color;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.browseTab && this.searchBox != null && this.searchBox.isFocused()
			&& event.isConfirmation()) {
			this.runSearch();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int x = this.cx;
		int w = this.cw;
		int top = this.listTop();
		int rows = this.visibleRows();
		int from = this.page * rows;
		if (this.browseTab) {
			for (int i = from; i < Math.min(this.results.size(), from + rows); i++) {
				ModManager.RemoteMod mod = this.results.get(i);
				int y = top + (i - from) * ROW_H;
				g.fill(x, y, x + w - 74, y + 20, 0x30000000);
				g.text(this.font, this.font.plainSubstrByWidth(mod.title(), w - 90), x + 6, y + 2, 0xFFFFFFFF);
				g.text(this.font, this.font.plainSubstrByWidth(mod.author() + "  •  " + ModManager.formatDownloads(mod.downloads()) + " " + L.t("mods.downloads"), w - 90),
					x + 6, y + 11, Theme.MUTED);
			}
			if (this.results.isEmpty() && !this.searching) {
				g.centeredText(this.font, Component.literal(L.t("mods.type_to_search")), x + w / 2, top + 50, Theme.MUTED);
			}
		} else {
			for (int i = from; i < Math.min(this.local.size(), from + rows); i++) {
				ModManager.LocalMod mod = this.local.get(i);
				int y = top + (i - from) * ROW_H;
				g.fill(x, y, x + w - 96, y + 20, 0x30000000);
				g.fill(x, y, x + 2, y + 20, mod.enabled() ? 0xFF7CE38B : 0xFFFF6B6B);
				int color = mod.enabled() ? 0xFFFFFFFF : 0xFF8A8F9C;
				g.text(this.font, this.font.plainSubstrByWidth(mod.name(), w - 110), x + 8, y + 6, color);
			}
			if (this.local.isEmpty()) {
				g.centeredText(this.font, Component.literal(L.t("mods.empty")), x + w / 2, top + 50, Theme.MUTED);
			}
		}

		if (ModConfigScreens.modMenuLoaded()) {
			Component credit = Component.literal("Powered by ").withColor(0xB0B8C8).append(Component.literal("Mod Menu").withColor(0x7CE38B));
			g.text(this.font, credit, x + w - this.font.width(credit), this.cy + this.ch - 14, 0xFFFFFFFF);
		}
	}

	@Override
	protected void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.centeredText(this.font, this.status, this.width / 2, this.py + this.panelH + 6, this.statusColor);
	}
}
