package com.dnz.client.gui;

import com.dnz.client.L;
import com.dnz.client.gui.config.ButtonOption;
import com.dnz.client.gui.config.ChoiceOption;
import com.dnz.client.gui.config.HeaderOption;
import com.dnz.client.gui.config.Option;
import com.dnz.client.gui.config.OptionList;
import com.dnz.client.gui.config.SliderOption;
import com.dnz.client.gui.config.SwitchOption;
import com.dnz.client.mods.ModConfigScreens;
import com.dnz.client.sodium.SodiumSettings;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * DNZ menu "Performance" tab: Sodium's (and Sodium Extra's) video settings drawn with DNZ widgets.
 * Without Sodium, or if Sodium's settings can't be read, it falls back to a button that opens the video settings screen.
 */
public class DnzPerformanceTab extends DnzMenuScreen {
	private static final int ROW_H = 22;
	private static int pageIndex;

	private boolean sodium;
	private List<SodiumSettings.PageInfo> pages = List.of();
	private List<SodiumSettings.Row> rows = List.of();
	private int scroll;
	private Component status;
	/** DNZ style: the settings page and its Apply / Undo buttons. */
	private OptionList list;
	private DnzButton applyButton;
	private DnzButton undoButton;

	public DnzPerformanceTab(Screen parent) {
		super(Tab.PERFORMANCE, parent);
		this.sodium = FabricLoader.getInstance().isModLoaded("sodium");
		if (this.sodium) {
			try {
				SodiumSettings.undo(); // start from the saved values
			} catch (Throwable t) {
				this.sodium = false;
			}
		}
	}

	private int rowsTop() {
		return this.cy + 26;
	}

	private int visibleRows() {
		return Math.max(1, (this.cy + this.ch - 24 - this.rowsTop()) / ROW_H);
	}

	private int controlW() {
		return Math.min(140, this.cw / 2);
	}

	@Override
	protected void initContent() {
		this.list = null;
		if (this.sodium) {
			try {
				if (Theme.dnzStyle()) {
					this.initSodiumOptions();
				} else {
					this.initSodium();
				}
				return;
			} catch (Throwable t) {
				// Sodium changed how its settings work; use its own screen instead.
				this.sodium = false;
				this.rebuildWidgets();
				return;
			}
		}
		DnzButton video = new DnzButton(this.cx, this.cy + 70, this.cw, 22, Component.literal(L.t("perf.video_settings")),
			() -> this.minecraft.gui.setScreen(ModConfigScreens.videoSettings(this))).selected(true);
		this.addRenderableWidget(video);
	}

	/** DNZ style: page picker with Apply / Undo on top, the page's settings as rows on a scrolling page below. */
	private void initSodiumOptions() {
		this.pages = SodiumSettings.pages();
		pageIndex = Math.max(0, Math.min(pageIndex, this.pages.size() - 1));
		List<String> names = new ArrayList<>();
		for (SodiumSettings.PageInfo page : this.pages) {
			names.add(page.name().getString() + "  ·  " + page.mod().getString());
		}
		int buttonW = 52;
		int buttonsW = 2 * buttonW + 4;
		// The page picker is a one-row list of its own, so its dropdown opens over the settings below.
		OptionList top = new OptionList(this.cx, this.cy, this.cw - buttonsW - 6, 24);
		top.set(List.of(new ChoiceOption(L.t("perf.page"), names, () -> pageIndex, i -> {
			pageIndex = i;
			this.list.set(this.sodiumRows());
			this.list.scrollToTop();
		}).wide()));
		this.addRenderableWidget(top);
		this.applyButton = this.addRenderableWidget(new DnzButton(this.cx + this.cw - buttonsW, this.cy + 4, buttonW, 16,
			Component.literal(L.t("perf.apply")), () -> {
				SodiumSettings.apply();
				this.status = Component.literal(L.t("perf.applied")).withColor(0x7CE38B);
				this.refresh();
			}));
		this.undoButton = this.addRenderableWidget(new DnzButton(this.cx + this.cw - buttonW, this.cy + 4, buttonW, 16,
			Component.literal(L.t("perf.undo")), () -> {
				SodiumSettings.undo();
				this.status = null;
				this.refresh();
			}));
		this.list = this.addRenderableWidget(new OptionList(this.cx, this.cy + 30, this.cw, this.ch - 30));
		this.list.set(this.sodiumRows());
		this.updateButtons();
	}

	/** Settings of the chosen page; the first page starts with DNZ's own FPS switches. */
	private List<Option> sodiumRows() {
		List<Option> rows = new ArrayList<>();
		com.dnz.client.DnzConfig config = com.dnz.client.DnzConfig.get();
		if (pageIndex == 0) {
			rows.add(new HeaderOption("DNZ Client"));
			rows.add(new SwitchOption("DNZ Turbo", () -> config.turbo, on -> {
				config.turbo = on;
				config.save();
			}).note(L.t("perf.turbo.note")).tooltip(Component.literal(L.t("perf.turbo_tip"))));
			if (com.dnz.client.MacRetina.isMac()) {
				rows.add(new SwitchOption("Retina", () -> config.macRetina, on -> {
					config.macRetina = on;
					config.save();
					this.status = Component.literal(L.t("perf.retina_restart")).withColor(0xFFD24A);
				}).note(L.t("perf.retina.note")).tooltip(Component.literal(L.t("perf.retina_tip"))));
			}
			rows.add(new HeaderOption(L.t("visual.section.more")));
			rows.addAll(DnzVisualScreen.moreRows(this.minecraft, this::rebuildWidgets));
		}
		for (SodiumSettings.Row row : SodiumSettings.rows(pageIndex)) {
			String name = row.name().getString();
			Option option = switch (row.kind()) {
				case HEADER -> new HeaderOption(name);
				case TOGGLE -> new SwitchOption(name, () -> row.current() == 1, on -> {
					row.press().accept(this);
					this.refresh();
				});
				case SLIDER -> {
					int[] value = {row.current()};
					yield new SliderOption(name, row.min(), row.max(), row.step(), () -> value[0], v -> {
						value[0] = v;
						row.set().accept(v);
					}, v -> row.format().apply(v).getString()).onRelease(this::refresh);
				}
				case CYCLE -> row.choices().isEmpty()
					? new ButtonOption(name, row.value().getString(), () -> {
						row.press().accept(this);
						this.refresh();
					}).plain()
					: new ChoiceOption(name, row.choices().stream().map(Component::getString).toList(), row::current, i -> {
						row.set().accept(i);
						this.refresh();
					});
				case BUTTON -> new ButtonOption(name, row.value().getString(), () -> row.press().accept(this)).plain();
			};
			if (row.kind() != SodiumSettings.Kind.HEADER) {
				boolean enabled = row.enabled();
				option.activeIf(() -> enabled).marked(row.changed());
				if (row.tooltip() != null) {
					option.tooltip(row.tooltip());
				}
			}
			rows.add(option);
		}
		return rows;
	}

	private void updateButtons() {
		boolean changed = SodiumSettings.anyChanged();
		this.applyButton.active = changed;
		this.applyButton.selected(changed);
		this.undoButton.active = changed;
	}

	private void initSodium() {
		this.pages = SodiumSettings.pages();
		pageIndex = Math.max(0, Math.min(pageIndex, this.pages.size() - 1));
		this.rows = SodiumSettings.rows(pageIndex);
		this.scroll = Math.max(0, Math.min(this.scroll, this.rows.size() - this.visibleRows()));

		// Page selector
		DnzButton prev = new DnzButton(this.cx, this.cy, 20, 20, Component.literal("<"), () -> this.changePage(-1));
		DnzButton next = new DnzButton(this.cx + this.cw - 20, this.cy, 20, 20, Component.literal(">"), () -> this.changePage(1));
		prev.active = this.pages.size() > 1;
		next.active = this.pages.size() > 1;
		this.addRenderableWidget(prev);
		this.addRenderableWidget(next);

		// Option rows
		int controlW = this.controlW();
		int x = this.cx + this.cw - controlW - 6;
		int end = Math.min(this.rows.size(), this.scroll + this.visibleRows());
		for (int i = this.scroll; i < end; i++) {
			SodiumSettings.Row row = this.rows.get(i);
			int y = this.rowsTop() + (i - this.scroll) * ROW_H + 1;
			AbstractWidget widget = switch (row.kind()) {
				case HEADER -> null;
				case SLIDER -> new DnzSlider(x, y, controlW, 18, row.min(), row.max(), row.step(), row.current(),
					row.format(), row.set(), this::refresh);
				case TOGGLE -> new DnzButton(x, y, controlW, 18, row.value(), () -> {
					row.press().accept(this);
					this.refresh();
				}).selected(row.current() == 1);
				case CYCLE, BUTTON -> new DnzButton(x, y, controlW, 18, row.value(), () -> {
					row.press().accept(this);
					this.refresh();
				});
			};
			if (widget == null) {
				continue;
			}
			widget.active = row.enabled();
			if (row.tooltip() != null) {
				this.tip(widget, row.tooltip());
			}
			this.addRenderableWidget(widget);
		}

		// Bottom bar
		int by = this.cy + this.ch - 20;
		boolean changed = SodiumSettings.anyChanged();
		DnzButton apply = new DnzButton(this.cx, by, 80, 20, Component.literal(L.t("perf.apply")), () -> {
			SodiumSettings.apply();
			this.status = Component.literal(L.t("perf.applied")).withColor(0x7CE38B);
			this.refresh();
		}).selected(changed);
		apply.active = changed;
		DnzButton undo = new DnzButton(this.cx + 84, by, 80, 20, Component.literal(L.t("perf.undo")), () -> {
			SodiumSettings.undo();
			this.status = null;
			this.refresh();
		});
		undo.active = changed;
		this.addRenderableWidget(apply);
		this.addRenderableWidget(undo);
		this.addTurboButton(this.cx + 168, by);
	}

	/** DNZ Turbo on/off (our own FPS boost, next to Sodium's settings). */
	private void addTurboButton(int x, int y) {
		com.dnz.client.DnzConfig config = com.dnz.client.DnzConfig.get();
		DnzButton turbo = new DnzButton(x, y, 90, 20, Component.literal("Turbo: " + (config.turbo ? L.t("on") : L.t("off"))), () -> {
			config.turbo = !config.turbo;
			config.save();
			this.rebuildWidgets();
		}).selected(config.turbo);
		this.tip(turbo, Component.literal(L.t("perf.turbo_tip")));
		this.addRenderableWidget(turbo);
		if (com.dnz.client.MacRetina.isMac()) {
			DnzButton retina = new DnzButton(x + 94, y, 90, 20, Component.literal("Retina: " + (config.macRetina ? L.t("on") : L.t("off"))), () -> {
				config.macRetina = !config.macRetina;
				config.save();
				this.status = Component.literal(L.t("perf.retina_restart")).withColor(0xFFD24A);
				this.rebuildWidgets();
			}).selected(config.macRetina);
			this.tip(retina, Component.literal(L.t("perf.retina_tip")));
			this.addRenderableWidget(retina);
		}
	}

	private void changePage(int delta) {
		if (this.pages.isEmpty()) {
			return;
		}
		pageIndex = Math.floorMod(pageIndex + delta, this.pages.size());
		this.scroll = 0;
		this.refresh();
	}

	/** Shows the new values (other settings may have become usable); the DNZ style keeps its scroll and animations. */
	private void refresh() {
		if (this.list != null) {
			try {
				this.list.set(this.sodiumRows());
				this.updateButtons();
				return;
			} catch (Throwable t) {
				this.sodium = false; // Sodium changed how its settings work; use its own screen instead
			}
		}
		this.rebuildWidgets();
	}

	@Override
	protected boolean scrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (this.list == null && this.sodium && scrollY != 0 && mouseX >= this.cx && mouseX < this.cx + this.cw
			&& mouseY >= this.rowsTop() && mouseY < this.rowsTop() + this.visibleRows() * ROW_H) {
			int max = Math.max(0, this.rows.size() - this.visibleRows());
			int next = Math.max(0, Math.min(max, this.scroll - (int) Math.signum(scrollY)));
			if (next != this.scroll) {
				this.scroll = next;
				this.refresh();
			}
			return true;
		}
		return super.scrolled(mouseX, mouseY, scrollX, scrollY);
	}

	/** Leaving the tab keeps the changes, like closing any other DNZ setting. */
	@Override
	public void removed() {
		if (this.sodium) {
			try {
				if (SodiumSettings.anyChanged()) {
					SodiumSettings.apply();
				}
			} catch (Throwable ignored) {
			}
		}
		super.removed();
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int fps = this.minecraft.getFps();
		int fpsColor = fps >= 60 ? 0xFF7CE38B : fps >= 30 ? 0xFFFFD24A : 0xFFFF6B6B;
		if (!this.sodium) {
			Theme.scaledCentered(g, this.font, Component.literal(Integer.toString(fps)), this.cx + this.cw / 2, this.cy + 8, 3.0F, fpsColor);
			g.centeredText(this.font, Component.literal("FPS"), this.cx + this.cw / 2, this.cy + 40, Theme.MUTED);
			g.textWithWordWrap(this.font, Component.literal(L.t("perf.no_sodium")), this.cx, this.cy + 104, this.cw, 0xFFFF6B6B);
			return;
		}

		// FPS in the header corner
		Component fpsText = Theme.smooth(Component.literal(fps + " FPS").withColor(fpsColor & 0xFFFFFF));
		g.text(this.font, fpsText, this.topBarRight() - this.font.width(fpsText), this.py + 13, 0xFFFFFFFF);
		if (this.list != null) {
			return; // the DNZ style's rows draw themselves
		}

		// Page title between the arrows
		if (!this.pages.isEmpty()) {
			SodiumSettings.PageInfo page = this.pages.get(pageIndex);
			Component title = page.name().copy().withColor(0xFFFFFF)
				.append(Component.literal("  •  ").withColor(0x6A7080))
				.append(page.mod().copy().withColor(0x9AA3B5));
			g.centeredText(this.font, title, this.cx + this.cw / 2, this.cy + 6, 0xFFFFFFFF);
			String counter = (pageIndex + 1) + "/" + this.pages.size();
			g.text(this.font, counter, this.cx + 26, this.cy + 6, 0xFF6A7080);
		}

		// Row labels
		int controlW = this.controlW();
		int nameW = this.cw - controlW - 16;
		int end = Math.min(this.rows.size(), this.scroll + this.visibleRows());
		for (int i = this.scroll; i < end; i++) {
			SodiumSettings.Row row = this.rows.get(i);
			int y = this.rowsTop() + (i - this.scroll) * ROW_H;
			if (row.kind() == SodiumSettings.Kind.HEADER) {
				g.text(this.font, row.name().copy().withColor(Theme.accentInfo().rgb()), this.cx + 2, y + 7, 0xFFFFFFFF);
				g.fill(this.cx + 6 + this.font.width(row.name()), y + 11, this.cx + this.cw, y + 12, 0x30FFFFFF);
				continue;
			}
			g.fill(this.cx, y, this.cx + this.cw - 2, y + 20, 0x28000000);
			if (row.changed()) {
				g.fill(this.cx, y, this.cx + 2, y + 20, Theme.accent());
			}
			int color = row.enabled() ? 0xFFFFFFFF : 0xFF7A7F8C;
			String name = this.font.plainSubstrByWidth(row.name().getString(), nameW);
			g.text(this.font, name, this.cx + 6, y + 6, color);
		}

		// Scroll bar
		int visible = this.visibleRows();
		if (this.rows.size() > visible) {
			int trackTop = this.rowsTop();
			int trackH = visible * ROW_H - 2;
			int thumbH = Math.max(12, trackH * visible / this.rows.size());
			int thumbY = trackTop + (trackH - thumbH) * this.scroll / (this.rows.size() - visible);
			g.fill(this.cx + this.cw + 2, trackTop, this.cx + this.cw + 4, trackTop + trackH, 0x30FFFFFF);
			g.fill(this.cx + this.cw + 2, thumbY, this.cx + this.cw + 4, thumbY + thumbH, Theme.accent());
		}

		// Credit
		Component credit = Component.literal("Powered by ").withColor(0xB0B8C8).append(Component.literal("Sodium").withColor(0x7CE38B));
		// Only where it doesn't run into the Turbo button.
		if (this.cw - 262 >= this.font.width(credit)) {
			g.text(this.font, credit, this.cx + this.cw - this.font.width(credit), this.cy + this.ch - 14, 0xFFFFFFFF);
		}
	}

	@Override
	protected void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		if (this.status != null) {
			g.centeredText(this.font, this.status, this.width / 2, this.py + this.panelH + 6, 0xFFFFFFFF);
		}
	}
}
