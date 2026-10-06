package com.dnz.client.gui;

import com.dnz.client.DnzConfig;
import com.dnz.client.L;
import com.dnz.client.compat.Compat;
import com.dnz.client.hud.DnzHud;
import com.dnz.client.hud.HudModule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * DNZ menu "Mods" tab: every module (HUD panels and features like zoom or turbo) can be switched
 * on and off here, filtered by category. The HUD editor opens from the sidebar / the button at the bottom.
 * DNZ style: a scrolling grid of cards (line icon, name bar that lights up when on) with search; installed mods,
 * scripts and the schematic mod open from cards of their own.
 */
public class DnzHudTab extends DnzMenuScreen {
	private static final int ROW_H = 22;
	private static final int GAP = 6;
	/** null = all categories (Java and Simple styles). */
	private static HudModule.Category category;
	/** Category chip of the DNZ style. */
	private static Chip chip = Chip.ALL;
	private int page;

	/** Grid scroll in pixels: where it is drawn now and where the mouse wheel sent it. */
	private float scroll;
	private float scrollTarget;
	/** Per card: hover and on/off animation (0..1). */
	private final Map<String, float[]> anim = new HashMap<>();

	private enum Chip {
		ALL("All mods", (char) 0, m -> true),
		HUD("HUD", Icon.EDIT_HUD, HudModule::movable),
		COMBAT("Combat", Icon.COMBAT, m -> m.category == HudModule.Category.PVP),
		QUALITY("Quality of Life", Icon.QUALITY, m -> m.category == HudModule.Category.FOOD || m.category == HudModule.Category.ITEMS
			|| m.category == HudModule.Category.VISUAL),
		OTHER("Other", (char) 0, m -> m.category == HudModule.Category.PERFORMANCE || m.category == HudModule.Category.INFO && !m.movable());

		final String label;
		final char icon;
		final Predicate<HudModule> filter;

		Chip(String label, char icon, Predicate<HudModule> filter) {
			this.label = label;
			this.icon = icon;
			this.filter = filter;
		}
	}

	/** Card id of Open World (opens the single player world to friends). */
	private static final String OPEN_WORLD = "page.openworld";

	/** One card: a module (switches on/off) or a page (opens). */
	private record Card(String id, String name, char icon, HudModule module, Tab page) {
	}

	public DnzHudTab(Screen parent) {
		super(Tab.HUD, parent);
	}

	@Override
	protected void initContent() {
		if (Theme.dnzStyle()) {
			// Chips sized to their text; the grid is drawn and clicked in extractContent / clicked.
			int x = this.cx;
			for (Chip c : Chip.values()) {
				int w = ChipButton.widthFor(this.font, c);
				if (x + w > this.cx + this.cw) {
					break;
				}
				this.addRenderableWidget(new ChipButton(x, this.cy, w, 13, c, chip == c, () -> {
					chip = c;
					this.scroll = 0;
					this.scrollTarget = 0;
					this.rebuildWidgets();
				}));
				x += w + 3;
			}
			return;
		}

		// Category chips
		HudModule.Category[] cats = HudModule.Category.values();
		int chips = cats.length + 1;
		int chipW = (this.cw - (chips - 1) * 2) / chips;
		for (int i = 0; i < chips; i++) {
			HudModule.Category c = i == 0 ? null : cats[i - 1];
			String label = c == null ? L.t("cat.all") : c.label();
			this.addRenderableWidget(new DnzButton(this.cx + i * (chipW + 2), this.cy, chipW, 14,
				Component.literal(this.font.plainSubstrByWidth(label, chipW - 4)), () -> {
					category = c;
					this.page = 0;
					this.rebuildWidgets();
				}).look(DnzButton.Look.CHIP).selected(category == c));
		}

		List<HudModule> modules = DnzHud.modules().stream().filter(m -> category == null || m.category == category).toList();
		int colW = (this.cw - 6) / 2;
		int top = this.cy + 20;
		int rows = Math.max(1, (this.ch - 20 - 46) / ROW_H);
		int perPage = rows * 2;
		int pages = Math.max(1, (modules.size() + perPage - 1) / perPage);
		this.page = Math.min(this.page, pages - 1);
		int from = this.page * perPage;
		for (int i = from; i < Math.min(modules.size(), from + perPage); i++) {
			HudModule m = modules.get(i);
			int x = this.cx + ((i - from) % 2) * (colW + 6);
			int y = top + ((i - from) / 2) * ROW_H;
			boolean on = m.enabled();
			String text = m.label() + ": " + (on ? L.t("on") : L.t("off"));
			this.addRenderableWidget(new DnzButton(x, y, colW, 20, Component.literal(this.font.plainSubstrByWidth(text, colW - 8)), () -> {
				m.setEnabled(!on);
				DnzConfig.get().save();
				this.rebuildWidgets();
			}).selected(on));
		}
		if (pages > 1) {
			int py = top + rows * ROW_H;
			DnzButton prev = new DnzButton(this.cx + this.cw / 2 - 50, py, 30, 16, Component.literal("<"), () -> {
				this.page--;
				this.rebuildWidgets();
			});
			prev.active = this.page > 0;
			DnzButton next = new DnzButton(this.cx + this.cw / 2 + 20, py, 30, 16, Component.literal(">"), () -> {
				this.page++;
				this.rebuildWidgets();
			});
			next.active = this.page < pages - 1;
			this.addRenderableWidget(prev);
			this.addRenderableWidget(next);
		}

		int by = this.cy + this.ch - 20;
		int resetW = 80;
		this.addRenderableWidget(new DnzButton(this.cx, by, this.cw - resetW - 4, 20, Component.literal(L.t("hud.edit")),
			() -> this.minecraft.gui.setScreen(new DnzHudScreen(this))).selected(true));
		this.addRenderableWidget(new DnzButton(this.cx + this.cw - resetW, by, resetW, 20, Component.literal(L.t("hud.reset")), () -> {
			DnzHud.reset();
			this.rebuildWidgets();
		}));
	}

	@Override
	protected void searchChanged() {
		this.scroll = 0;
		this.scrollTarget = 0;
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		if (!Theme.dnzStyle()) {
			List<HudModule> modules = DnzHud.modules().stream().filter(m -> category == null || m.category == category).toList();
			int rows = Math.max(1, (this.ch - 20 - 46) / ROW_H);
			int pages = Math.max(1, (modules.size() + rows * 2 - 1) / (rows * 2));
			if (pages > 1) {
				g.centeredText(this.font, Theme.smooth((this.page + 1) + " / " + pages), this.cx + this.cw / 2, this.cy + 20 + rows * ROW_H + 4, Theme.MUTED);
			}
			return;
		}
		this.extractGrid(g, mouseX, mouseY);
	}

	// ---------------------------------------------------------------- DNZ grid

	/** Cards of the chosen chip that match the search text. */
	private List<Card> cards() {
		String q = query.trim().toLowerCase(Locale.ROOT);
		List<Card> list = new ArrayList<>();
		for (HudModule m : DnzHud.modules()) {
			if (chip.filter.test(m)) {
				list.add(new Card(m.id, m.label(), icon(m.id), m, null));
			}
		}
		if (chip == Chip.ALL || chip == Chip.OTHER) {
			list.add(new Card("page.mods", "Installed Mods", '\ue87b', null, Tab.MODS));
			list.add(new Card("page.scripts", "Scripts", '\ue86f', null, Tab.SCRIPTS));
			if (Tab.SCHEMATIC.available()) {
				list.add(new Card("page.schematic", "Schematic", '\uea3b', null, Tab.SCHEMATIC));
			}
			// Opens the single player world to friends anywhere (internet hosting comes with DNZ Client).
			if (this.minecraft.hasSingleplayerServer()) {
				list.add(new Card(OPEN_WORLD, "Open World", Icon.SERVER, null, null));
			}
		}
		if (!q.isEmpty()) {
			list.removeIf(c -> !c.name().toLowerCase(Locale.ROOT).contains(q));
		}
		return list;
	}

	private int gridTop() {
		return this.cy + 19;
	}

	private int gridHeight() {
		return this.ch - 19;
	}

	private int columns() {
		return 4;
	}

	/** Cards a little less than half as tall as wide. */
	private int cardH() {
		return Math.max(40, Math.round(this.cardW() * 0.47F));
	}

	/** The gear in a module card's top right corner (opens its settings). */
	private static final int GEAR = 14;

	private int cardW() {
		int cols = this.columns();
		return (this.cw - GAP * (cols - 1)) / cols;
	}

	private float maxScroll(int count) {
		int rows = (count + this.columns() - 1) / this.columns();
		return Math.max(0, rows * (this.cardH() + GAP) - GAP - this.gridHeight());
	}

	/** Set by cardAt: the mouse is on the card's gear (opens its settings) instead of the card (switches it). */
	private boolean onGear;

	/** The card under the mouse, or null. */
	private Card cardAt(List<Card> list, double mouseX, double mouseY) {
		int top = this.gridTop();
		if (mouseX < this.cx || mouseX >= this.cx + this.cw || mouseY < top || mouseY >= top + this.gridHeight()) {
			return null;
		}
		int cols = this.columns();
		int w = this.cardW();
		int col = (int) ((mouseX - this.cx) / (w + GAP));
		double inRow = mouseY - top + this.scroll;
		int row = (int) (inRow / (this.cardH() + GAP));
		double inCardY = inRow - row * (this.cardH() + GAP);
		boolean inCard = (mouseX - this.cx) - col * (w + GAP) < w && inCardY < this.cardH();
		double inCardX = (mouseX - this.cx) - col * (w + GAP);
		this.onGear = inCardX >= w - GEAR - 2 && inCardY <= GEAR + 2;
		int index = row * cols + col;
		return inCard && col < cols && index >= 0 && index < list.size() ? list.get(index) : null;
	}

	@Override
	protected boolean clicked(MouseButtonEvent event, boolean doubleClick) {
		if (Theme.dnzStyle() && event.button() == 0) {
			Card c = this.cardAt(this.cards(), event.x(), event.y());
			if (c != null) {
				AbstractWidget.playButtonClickSound(this.minecraft.getSoundManager());
				if (OPEN_WORLD.equals(c.id())) {
					this.minecraft.gui.setScreen(Compat.openWorldScreen(this));
				} else if (c.module() == null) {
					this.go(c.page());
				} else if (this.onGear) {
					this.openPage(new DnzModuleScreen(this.parent, c.module()));
				} else {
					c.module().setEnabled(!c.module().enabled());
					DnzConfig.get().save();
				}
				return true;
			}
		}
		return super.clicked(event, doubleClick);
	}

	@Override
	protected boolean scrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (Theme.dnzStyle() && scrollY != 0 && mouseX >= this.cx && mouseX < this.cx + this.cw
			&& mouseY >= this.gridTop() && mouseY < this.gridTop() + this.gridHeight()) {
			float max = this.maxScroll(this.cards().size());
			this.scrollTarget = Math.max(0, Math.min(max, this.scrollTarget - (float) scrollY * 30));
			return true;
		}
		return super.scrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private void extractGrid(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		List<Card> list = this.cards();
		int top = this.gridTop();
		int height = this.gridHeight();
		float max = this.maxScroll(list.size());
		this.scrollTarget = Math.min(this.scrollTarget, max);
		this.scroll = Anim.approach(this.scroll, this.scrollTarget, 16.0F);
		if (list.isEmpty()) {
			g.centeredText(this.font, Theme.smooth(L.t("hud.no_results")), this.cx + this.cw / 2, top + height / 2 - 4, MUTED);
			return;
		}

		Card hovered = this.cardAt(list, mouseX, mouseY);
		int cols = this.columns();
		int w = this.cardW();
		int ch = this.cardH();
		int accent = Theme.menuAccent();
		g.enableScissor(this.cx, top, this.cx + this.cw, top + height);
		for (int i = 0; i < list.size(); i++) {
			Card c = list.get(i);
			boolean on = c.module() != null && c.module().enabled();
			float[] st = this.anim.computeIfAbsent(c.id(), k -> new float[] {0, on ? 1 : 0});
			st[0] = Anim.approach(st[0], c.equals(hovered) ? 1.0F : 0.0F, 14.0F);
			st[1] = Anim.approach(st[1], on ? 1.0F : 0.0F, 12.0F);
			int x = this.cx + (i % cols) * (w + GAP);
			float y = top + (i / cols) * (ch + GAP) - this.scroll;
			if (y + ch < top || y > top + height) {
				continue;
			}
			// Dark card, its edge orange when on; icon top left, name and On / Off at the bottom, gear top right.
			boolean page = c.module() == null;
			int edge = page ? Theme.lerp(0xFF1B212C, 0xFF2A3342, st[0]) : Theme.lerp(Theme.lerp(0xFF1B212C, 0xFF2A3342, st[0]), Theme.withAlpha(accent, 0.6F), st[1]);
			Smooth.rect(g, x - 0.75F, y - 0.75F, w + 1.5F, ch + 1.5F, 8.75F, edge);
			Smooth.rect(g, x, y, w, ch, 8, Theme.lerp(0xFF121821, 0xFF161D28, st[0]));
			int iconColor = page ? 0xFFC3C9D6 : Theme.lerp(0xFF6F7787, accent, st[1]);
			Icon.draw(g, c.icon(), x + 8, y + 8, 12, iconColor);
			if (!page) {
				int gear = this.onGear && c.equals(hovered) ? 0xFFFFFFFF : 0xFF5A6272;
				Icon.draw(g, Icon.PREFERENCES, x + w - GEAR, y + 4, 9, gear);
			}
			float s = 0.78F;
			Component name = DnzMenuScreen.bold(c.name());
			if (this.font.width(name) * s > w - 14) {
				name = DnzMenuScreen.bold(this.font.plainSubstrByWidth(c.name(), (int) ((w - 20) / s)) + "…");
			}
			g.pose().pushMatrix();
			g.pose().translate(x + 8, y + ch - (page ? 15 : 22));
			g.pose().scale(s, s);
			g.text(this.font, name, 0, 0, 0xFFF1F3F7, false);
			g.pose().popMatrix();
			if (!page) {
				g.pose().pushMatrix();
				g.pose().translate(x + 8, y + ch - 11);
				g.pose().scale(0.6F, 0.6F);
				g.text(this.font, Ui.regular(c.module().enabled() ? "On" : "Off"), 0, 0, 0xFF8A93A6, false);
				g.pose().popMatrix();
			}
		}
		g.disableScissor();

		// Thin scrollbar when the grid is taller than the area.
		if (max > 0) {
			float total = max + height;
			float barH = Math.max(16, height * height / total);
			float barY = top + (height - barH) * (this.scroll / max);
			Smooth.rect(g, this.cx + this.cw + 4, barY, 3, barH, 1.5F, 0x40FFFFFF);
		}
	}

	/** Line icon of a module card (Material Icons code points). */
	private static char icon(String id) {
		return switch (id) {
			case "fps" -> '\ue9e4';
			case "ping" -> '\ue63e';
			case "cps" -> '\ue323';
			case "coords" -> '\ue55c';
			case "armor" -> '\ue9e0';
			case "effects" -> '\uea4b';
			case "totems" -> '\ue1d5';
			case "compass" -> '\ue87a';
			case "biome" -> '\uea63';
			case "clock" -> '\uefd6';
			case "gametime" -> '\ue430';
			case "speed" -> '\ue566';
			case "memory" -> '\ue322';
			case "server" -> '\ue875';
			case "players" -> '\uea21';
			case "light" -> '\ue90f';
			case "session" -> '\ue425';
			case "keystrokes" -> '\ue312';
			case "reach" -> '\ue41c';
			case "combo" -> '\uea0b';
			case "target" -> '\ue55c';
			case "togglesprint" -> '\ue566';
			case "helditem" -> '\ue925';
			case "pots" -> '\ue544';
			case "gapples" -> '\ue87e';
			case "pearls" -> '\ue837';
			case "arrows" -> '\uf1e1';
			case "foodsaturation" -> '\ue56c';
			case "foodpreview" -> '\uea61';
			case "foodtooltip" -> '\ue88e';
			case "shulkerpreview" -> '\ue1a1';
			case "cleanhotbar" -> '\ue8f3';
			case "zoom" -> '\ue8ff';
			case "fullbright" -> '\ue1ac';
			case "streamer" -> '\ue04b';
			case "turbo" -> '\ueb9b';
			default -> '\ue87b';
		};
	}

	/** Category chip of the DNZ style: optional line icon and the name, the chosen one filled. */
	private static final class ChipButton extends net.minecraft.client.gui.components.AbstractButton {
		private final Chip chip;
		private final boolean selected;
		private final Runnable action;
		private float hover;

		ChipButton(int x, int y, int w, int h, Chip chip, boolean selected, Runnable action) {
			super(x, y, w, h, Component.literal(chip.label));
			this.chip = chip;
			this.selected = selected;
			this.action = action;
		}

		static int widthFor(net.minecraft.client.gui.Font font, Chip chip) {
			return (int) (font.width(bold(chip.label)) * Ui.scaled(0.66F)) + 10 + (chip.icon != 0 ? 10 : 0);
		}

		@Override
		public void onPress(net.minecraft.client.input.InputWithModifiers input) {
			this.action.run();
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
			net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
			this.hover = Anim.approach(this.hover, this.isHoveredOrFocused() ? 1.0F : 0.0F, 14.0F);
			int x = this.getX();
			int y = this.getY();
			if (this.selected) {
				Smooth.rect(g, x, y, this.width, this.height, 3, Theme.menuAccent());
			} else {
				Smooth.rect(g, x, y, this.width, this.height, 3, Theme.lerp(0xFF29313D, 0xFF353E4B, this.hover));
			}
			float tx = x + 5;
			if (this.chip.icon != 0) {
				Icon.draw(g, this.chip.icon, tx, y + (this.height - 7) / 2.0F, 7, this.selected ? Palette.ON_ACCENT : 0xFFFFFFFF);
				tx += 10;
			}
			float s = 0.66F;
			g.pose().pushMatrix();
			g.pose().translate(tx, y + (this.height - 8 * s) / 2.0F + 0.5F);
			g.pose().scale(s, s);
			g.text(font, bold(this.chip.label), 0, 0, this.selected ? Palette.ON_ACCENT : 0xFFFFFFFF, false);
			g.pose().popMatrix();
		}

		@Override
		protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
			this.defaultButtonNarrationText(output);
		}
	}
}
