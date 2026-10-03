package com.dnz.client.gui;

import com.dnz.client.L;
import com.dnz.client.gui.config.OptionList;
import com.mojang.blaze3d.platform.Window;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.joml.Vector2f;

/**
 * The DNZ menu: tab sidebar on the left, the selected tab's content on the right.
 * Each tab is its own screen extending this class; switching tabs keeps the original parent.
 * <p>
 * In the DNZ style the whole menu is laid out in its own units and drawn scaled (see {@link MenuScale}), so it has
 * the same proportions at every resolution and GUI scale. {@link #width} and {@link #height} are then the screen
 * size in those units, and the mouse comes in the same units: pages override {@link #clicked}, {@link #scrolled}
 * and friends instead of the mouse methods.
 */
public abstract class DnzMenuScreen extends Screen {
	public enum Tab {
		HUD("menu.hud", DnzHudTab::new),
		VISUAL("menu.visual", DnzVisualScreen::new),
		PERFORMANCE("menu.performance", DnzPerformanceTab::new),
		MODS("menu.mods", DnzModsScreen::new),
		SCRIPTS("menu.scripts", DnzScriptsTab::new),
		SKIN("menu.skin", DnzSkinScreen::new),
		/** Only shown when the DNZ Schematic mod is installed; opens its own menu. */
		SCHEMATIC("menu.schematic", DnzMenuScreen::openSchematic),
		/** DNZ style only: version notes and the feedback page. */
		CHANGELOG("menu.changelog", DnzChangelogTab::new),
		FEEDBACK("menu.feedback", DnzFeedbackTab::new);

		private final String key;
		private final Function<Screen, Screen> factory;

		Tab(String key, Function<Screen, Screen> factory) {
			this.key = key;
			this.factory = factory;
		}

		public String label() {
			return L.t(this.key);
		}

		boolean available() {
			if (this == CHANGELOG || this == FEEDBACK) {
				return Theme.dnzStyle();
			}
			return this != SCHEMATIC || FabricLoader.getInstance().isModLoaded("dnzschematic");
		}
	}

	/** DNZ Schematic is a separate mod, so its menu is found by name (DNZ Client also works without it). */
	private static Screen openSchematic(Screen parent) {
		try {
			return (Screen) Class.forName("com.dnz.schematic.gui.SchematicScreen").getMethod("open", Screen.class).invoke(null, parent);
		} catch (ReflectiveOperationException e) {
			return parent;
		}
	}

	protected static final int SIDEBAR_W = 96;
	/** Tab shown when the menu is opened with the menu key. */
	private static Tab lastTab = Tab.HUD;

	protected final Screen parent;
	private final Tab tab;
	protected int px;
	protected int py;
	protected int panelW;
	protected int panelH;
	/** Content area of the selected tab. */
	protected int cx;
	protected int cy;
	protected int cw;
	protected int ch;

	/** When the menu was opened (switching tabs inside the menu does not play the opening animation again). */
	private final long openedAt;

	/** GUI units per menu unit: the DNZ-style menu is drawn scaled by this (1 in the other styles). */
	private float scale = 1;
	/** The real screen size in GUI units ({@link #width} and {@link #height} are in menu units). */
	private int guiWidth;
	private int guiHeight;
	/** Help texts of widgets, drawn in the DNZ style (Minecraft's own tooltips in the other styles). */
	private final Map<AbstractWidget, Component> tips = new IdentityHashMap<>();
	/** What the mouse rests on for a help box, and since when. */
	private Object tipOwner;
	private long tipSince;

	protected DnzMenuScreen(Tab tab, Screen parent) {
		super(Component.literal(tab.label()));
		this.tab = tab;
		this.parent = parent;
		lastTab = tab;
		boolean fromMenu = net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof DnzMenuScreen;
		this.openedAt = fromMenu ? 0 : System.nanoTime();
	}

	/** Opens the DNZ menu on the last used tab. */
	public static Screen open(Screen parent) {
		return lastTab.factory.apply(parent);
	}

	@Override
	protected final void init() {
		Window window = this.minecraft.getWindow();
		this.guiWidth = window.getGuiScaledWidth();
		this.guiHeight = window.getGuiScaledHeight();
		this.tips.clear();
		if (Theme.dnzStyle()) {
			// The screen in menu units: the panel always gets its full size, whatever the GUI scale.
			this.scale = MenuScale.gui(window);
			Ui.unitPx = MenuScale.pixels(window);
			this.width = (int) Math.ceil(this.guiWidth / this.scale);
			this.height = (int) Math.ceil(this.guiHeight / this.scale);
			SharpFonts.update(this.minecraft, this); // letters rendered for the menu's real pixel size
			// Sizes measured in the font the menu is drawn with.
			boolean before = Theme.smoothAll;
			Theme.smoothAll = true;
			try {
				this.initDnz();
				this.initContent();
			} finally {
				Theme.smoothAll = before;
			}
			return;
		}
		this.scale = 1;
		this.width = this.guiWidth;
		this.height = this.guiHeight;
		this.panelW = Math.min(470, this.width - 16);
		this.panelH = Math.min(270, this.height - 36);
		this.px = (this.width - this.panelW) / 2;
		this.py = Math.max(4, (this.height - 24 - this.panelH) / 2);
		this.cx = this.px + SIDEBAR_W + 10;
		this.cw = this.panelW - SIDEBAR_W - 20;
		this.cy = this.py + 32;
		this.ch = this.panelH - 40;

		// Tabs get closer together on small screens so they never reach the "Done" button.
		java.util.List<Tab> tabs = java.util.Arrays.stream(Tab.values()).filter(Tab::available).toList();
		int step = Math.max(16, Math.min(24, (this.panelH - 40 - 34) / tabs.size()));
		int y = this.py + 40;
		boolean simple = Theme.simpleStyle();
		for (Tab t : tabs) {
			Runnable open = () -> {
				if (t != this.tab) {
					this.minecraft.gui.setScreen(t.factory.apply(this.parent));
				}
			};
			if (simple) {
				// Simple style tabs: see-through until hovered, the open tab in the accent color.
				this.addRenderableWidget(new GlassButton(this.px + 8, y, SIDEBAR_W - 16, step - 4, Component.literal(t.label()),
					t == this.tab ? GlassButton.Look.ACCENT : GlassButton.Look.GHOST, 0.8F, open));
			} else {
				this.addRenderableWidget(new DnzButton(this.px + 10, y, SIDEBAR_W - 18, step - 4, Component.literal(t.label()), open)
					.look(DnzButton.Look.TAB).selected(t == this.tab));
			}
			y += step;
		}
		if (simple) {
			this.addRenderableWidget(new GlassButton(this.px + 8, this.py + this.panelH - 28, SIDEBAR_W - 16, 20,
				Component.literal(L.t("done")), GlassButton.Look.GLASS, 0.8F, this::onClose));
		} else {
			this.addRenderableWidget(new DnzButton(this.px + 10, this.py + this.panelH - 30, SIDEBAR_W - 18, 20,
				Component.literal(L.t("done")), this::onClose));
		}

		this.initContent();
	}

	// ---------------------------------------------------------------- DNZ style frame
	// Sidebar with grouped pages and line icons, a top bar with back/forward arrows, the page title, a search box
	// and a close button, and the player at the bottom of the sidebar.

	private static final int DNZ_SIDEBAR_W = 104;
	private static final int BG_TOP = 0xF71D2633;
	private static final int BG_BOTTOM = 0xF7171F2A;
	private static final int SIDEBAR_BG = 0xFF222C39;
	protected static final int MUTED = 0xFF8D97A8;
	/** Text in the search box; kept while switching pages. */
	protected static String query = "";
	/** The search box gets the keyboard after the page was rebuilt because of typing. */
	private static boolean focusSearch;
	/** Pages visited inside the menu, for the back and forward arrows. */
	private static final ArrayDeque<Tab> BACK = new ArrayDeque<>();
	private static final ArrayDeque<Tab> FORWARD = new ArrayDeque<>();
	private EditBox search;
	/** Group headings of the sidebar: y position and text. */
	private final List<Map.Entry<Integer, String>> groupHeads = new ArrayList<>();

	private record SideEntry(String label, char icon, boolean selected, boolean beta, Runnable action) {
	}

	/** Page title in the DNZ style (the same names as the sidebar). */
	protected static String dnzTitle(Tab t) {
		return switch (t) {
			case HUD -> "Mods";
			case SKIN -> "Profiles";
			case VISUAL -> "Themes";
			case PERFORMANCE -> "Preferences";
			case CHANGELOG -> "Changelog";
			case FEEDBACK -> "Feedback";
			default -> t.label();
		};
	}

	/** Title shown in the top bar (module pages show the module's name). */
	protected String pageTitle() {
		return dnzTitle(this.tab);
	}

	/** Opens a page that is not in the sidebar (e.g. a module's settings); the back arrow returns here. */
	protected void openPage(Screen page) {
		BACK.push(this.tab);
		FORWARD.clear();
		this.minecraft.gui.setScreen(page);
	}

	/** Text in the bold font (titles). */
	protected static Component bold(String text) {
		return Component.literal(text).withStyle(style -> style.withFont(Theme.BOLD_FONT));
	}

	/** Opens another page of the menu and remembers this one for the back arrow. */
	protected void go(Tab t) {
		if (t == this.tab) {
			return;
		}
		BACK.push(this.tab);
		FORWARD.clear();
		this.minecraft.gui.setScreen(t.factory.apply(this.parent));
	}

	private void history(ArrayDeque<Tab> from, ArrayDeque<Tab> to) {
		if (!from.isEmpty()) {
			to.push(this.tab);
			this.minecraft.gui.setScreen(from.pop().factory.apply(this.parent));
		}
	}

	private void initDnz() {
		this.panelW = Math.min(MenuScale.PANEL_W, this.width - 16);
		this.panelH = Math.min(MenuScale.PANEL_H, this.height - 30);
		this.px = (this.width - this.panelW) / 2;
		this.py = Math.max(4, (this.height - 22 - this.panelH) / 2);
		this.cx = this.px + DNZ_SIDEBAR_W + 10;
		this.cw = this.panelW - DNZ_SIDEBAR_W - 18;
		this.cy = this.py + 34;
		this.ch = this.panelH - 42;

		// Installed mods, scripts and schematic open from cards on the Mods page, so "Mods" stays selected there.
		Tab current = this.tab == Tab.MODS || this.tab == Tab.SCRIPTS || this.tab == Tab.SCHEMATIC ? Tab.HUD : this.tab;
		Map<String, List<SideEntry>> groups = new LinkedHashMap<>();
		groups.put("", List.of(
			new SideEntry("Edit HUD", Icon.EDIT_HUD, false, false, () -> this.minecraft.gui.setScreen(new DnzHudScreen(this))),
			new SideEntry("Global Search", Icon.SEARCH, false, false, () -> this.setFocused(this.search))));
		groups.put("MODS & OPTIONS", List.of(
			this.page(Tab.HUD, Icon.MODS, current),
			this.page(Tab.SKIN, Icon.PROFILES, current),
			new SideEntry("Keybinds", Icon.KEYBINDS, false, false,
				() -> this.minecraft.gui.setScreen(new KeyBindsScreen(this, this.minecraft.options)))));
		groups.put("PERSONALIZATION", List.of(this.page(Tab.VISUAL, Icon.THEMES, current), this.page(Tab.PERFORMANCE, Icon.PREFERENCES, current)));
		groups.put("DNZ CLIENT", List.of(
			this.page(Tab.CHANGELOG, Icon.CHANGELOG, current),
			new SideEntry("Feedback", Icon.FEEDBACK, current == Tab.FEEDBACK, true, () -> this.go(Tab.FEEDBACK))));

		// Entries get closer together on small screens so they never reach the player card.
		int items = groups.values().stream().mapToInt(List::size).sum();
		int heads = (int) groups.keySet().stream().filter(k -> !k.isEmpty()).count();
		int room = this.panelH - 32 - 40;
		int head = 13;
		int step = Math.max(12, Math.min(16, (room - heads * head) / items));
		if (heads * head + items * step > room) {
			head = 10;
		}
		int x = this.px + 7;
		int w = DNZ_SIDEBAR_W - 12;
		int y = this.py + 27;
		this.groupHeads.clear();
		for (Map.Entry<String, List<SideEntry>> group : groups.entrySet()) {
			if (!group.getKey().isEmpty()) {
				y += 3;
				this.groupHeads.add(Map.entry(y + head - 10, group.getKey()));
				y += head - 3;
			}
			for (SideEntry e : group.getValue()) {
				this.addRenderableWidget(new DnzSideItem(x, y, w, step - 2, e.label(), e.icon(), e.selected(), e.beta(), e.action()));
				y += step;
			}
		}

		// Top bar: back / forward, search box, close
		this.addRenderableWidget(new IconButton(this.cx - 2, this.py + 9, 13, 13, Icon.BACK, () -> !BACK.isEmpty(), () -> this.history(BACK, FORWARD)));
		this.addRenderableWidget(new IconButton(this.cx + 11, this.py + 9, 13, 13, Icon.FORWARD, () -> !FORWARD.isEmpty(), () -> this.history(FORWARD, BACK)));
		int sw = this.searchWidth();
		int sx = this.searchX();
		this.search = new EditBox(this.font, sx + 14, this.py + 12, sw - 17, 10, Component.literal("Search mods..."));
		this.search.setBordered(false);
		this.search.setMaxLength(32);
		this.search.setTextColor(0xFFFFFFFF);
		this.search.setHint(Theme.smooth(Component.literal("Search mods...").withColor(0x7D8798)));
		this.search.setValue(query);
		this.search.setResponder(this::onSearch);
		this.addRenderableWidget(this.search);
		this.addRenderableWidget(new IconButton(this.cx + this.cw - 15, this.py + 8, 15, 15, Icon.CLOSE, () -> true, this::onClose));
		// Bell next to the player: opens the changelog.
		this.addRenderableWidget(new IconButton(this.px + DNZ_SIDEBAR_W - 21, this.py + this.panelH - 25, 13, 13, Icon.BELL, () -> true,
			() -> this.go(Tab.CHANGELOG)));
		if (focusSearch) {
			focusSearch = false;
			this.setInitialFocus(this.search);
		}
	}

	private int searchWidth() {
		return Math.min(124, (int) (this.cw * 0.29F));
	}

	private int searchX() {
		return this.cx + this.cw - 22 - this.searchWidth();
	}

	private SideEntry page(Tab t, char icon, Tab current) {
		return new SideEntry(dnzTitle(t), icon, t == current, false, () -> this.go(t));
	}

	private void onSearch(String text) {
		query = text;
		if (this.tab == Tab.HUD) {
			this.searchChanged();
		} else if (!text.isBlank()) {
			// Searching from another page jumps to the mods, keeping what was typed.
			focusSearch = true;
			this.go(Tab.HUD);
		}
	}

	/** Right end of the free room in the title line (left of the search box in the DNZ style). */
	protected int topBarRight() {
		return Theme.dnzStyle() ? this.searchX() - 8 : this.cx + this.cw;
	}

	/** Called on the mods page when the search text changes. */
	protected void searchChanged() {
	}

	private void extractDnzFrame(GuiGraphicsExtractor g) {
		int accent = Theme.menuAccent();
		// Panel: soft shadow, thin light edge, dark blue-gray body a little darker at the bottom.
		Smooth.shadow(g, this.px, this.py + 3, this.panelW, this.panelH, 10, 16, 0x80000000);
		Smooth.rect(g, this.px - 0.75F, this.py - 0.75F, this.panelW + 1.5F, this.panelH + 1.5F, 10.75F, 0x30FFFFFF);
		Smooth.gradient(g, this.px, this.py, this.panelW, this.panelH, 10, BG_TOP, BG_BOTTOM, false);
		// Sidebar: the left part of the same rounded panel, a bit lighter, with a thin line on its right.
		g.enableScissor(this.px, this.py, this.px + DNZ_SIDEBAR_W, this.py + this.panelH);
		Smooth.rect(g, this.px, this.py, this.panelW, this.panelH, 10, SIDEBAR_BG);
		g.disableScissor();
		g.fill(this.px + DNZ_SIDEBAR_W, this.py, this.px + DNZ_SIDEBAR_W + 1, this.py + this.panelH, 0x18FFFFFF);

		// Brand: bold light blue capitals.
		g.pose().pushMatrix();
		g.pose().translate(this.px + DNZ_SIDEBAR_W / 2.0F, this.py + 11);
		g.pose().scale(1.15F, 1.15F);
		g.centeredText(this.font, bold("DNZ CLIENT"), 0, 0, Theme.lerp(accent, 0xFFFFFFFF, 0.3F));
		g.pose().popMatrix();

		for (Map.Entry<Integer, String> head : this.groupHeads) {
			g.pose().pushMatrix();
			g.pose().translate(this.px + 11, head.getKey() + 1);
			g.pose().scale(0.6F, 0.6F);
			g.text(this.font, bold(head.getValue()), 0, 0, 0xFFD3D9E2, false);
			g.pose().popMatrix();
		}
		this.extractPlayerCard(g, accent);

		// Top bar: page title and the search box frame
		g.pose().pushMatrix();
		g.pose().translate(this.cx + 30, this.py + 10.5F);
		g.pose().scale(1.3F, 1.3F);
		g.text(this.font, bold(this.pageTitle()), 0, 0, 0xFFFFFFFF, false);
		g.pose().popMatrix();
		int sw = this.searchWidth();
		int sx = this.searchX();
		boolean focused = this.search != null && this.search.isFocused();
		Smooth.rect(g, sx, this.py + 9, sw, 14, 3.5F, focused ? Theme.withAlpha(Theme.menuAccent(), 0.8F) : 0xFF3A4452);
		Smooth.rect(g, sx + 0.75F, this.py + 9.75F, sw - 1.5F, 12.5F, 2.75F, 0xFF242C37);
		Icon.draw(g, Icon.SEARCH, sx + 4, this.py + 12, 7.5F, 0xFFAEB6C3);
	}

	/** The player's face, name and game version at the bottom of the sidebar. */
	private void extractPlayerCard(GuiGraphicsExtractor g, int accent) {
		int y = this.py + this.panelH - 32;
		g.fill(this.px + 1, y, this.px + DNZ_SIDEBAR_W, y + 1, 0x18FFFFFF);
		int x = this.px + 9;
		String name = this.minecraft.getUser().getName();
		if (this.minecraft.player != null) {
			PlayerFaceExtractor.extractRenderState(g, this.minecraft.player.getSkin(), x, y + 9, 14);
		} else {
			Smooth.rect(g, x, y + 9, 14, 14, 2, accent);
		}
		g.pose().pushMatrix();
		g.pose().translate(x + 19, y + 9.5F);
		g.pose().scale(0.8F, 0.8F);
		g.text(this.font, Theme.smooth(this.font.plainSubstrByWidth(name, (int) ((DNZ_SIDEBAR_W - 50) / 0.8F))), 0, 0, 0xFFFFFFFF, false);
		g.pose().popMatrix();
		String version = FabricLoader.getInstance().getModContainer("minecraft")
			.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
		g.pose().pushMatrix();
		g.pose().translate(x + 19, y + 18);
		g.pose().scale(0.6F, 0.6F);
		g.text(this.font, Theme.smooth("Fabric " + version), 0, 0, MUTED, false);
		g.pose().popMatrix();
		// Little dot on the bell: there is something new in the changelog.
		float dx = this.px + DNZ_SIDEBAR_W - 12.5F;
		float dy = this.py + this.panelH - 25.5F;
		Smooth.shadow(g, dx, dy, 3.5F, 3.5F, 1.75F, 3, Theme.withAlpha(accent, 0.7F));
		Smooth.rect(g, dx, dy, 3.5F, 3.5F, 1.75F, accent);
	}

	/** Small icon button (arrows, close, bell); grayed out while it can't be used. */
	private static final class IconButton extends net.minecraft.client.gui.components.AbstractButton {
		private final char icon;
		private final java.util.function.BooleanSupplier enabled;
		private final Runnable action;
		private float hover;

		IconButton(int x, int y, int w, int h, char icon, java.util.function.BooleanSupplier enabled, Runnable action) {
			super(x, y, w, h, Component.empty());
			this.icon = icon;
			this.enabled = enabled;
			this.action = action;
		}

		@Override
		public void onPress(net.minecraft.client.input.InputWithModifiers input) {
			if (this.enabled.getAsBoolean()) {
				this.action.run();
			}
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
			boolean on = this.enabled.getAsBoolean();
			this.hover = Anim.approach(this.hover, on && this.isHoveredOrFocused() ? 1.0F : 0.0F, 14.0F);
			if (this.hover > 0.01F) {
				Smooth.rect(g, this.getX(), this.getY(), this.width, this.height, 3, Theme.withAlpha(0xFFFFFF, 0.07F * this.hover));
			}
			int color = on ? Theme.lerp(0xFFE6EAF0, 0xFFFFFFFF, this.hover) : 0xFF8790A0;
			float size = Math.min(this.width, this.height) - 4;
			Icon.draw(g, this.icon, this.getX() + (this.width - size) / 2.0F, this.getY() + (this.height - size) / 2.0F, size, color);
		}

		@Override
		protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
			this.defaultButtonNarrationText(output);
		}
	}

	/** Adds the tab's widgets inside the content area (cx, cy, cw, ch). */
	protected abstract void initContent();

	/** Draws the tab's content below its widgets. */
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
	}

	/** Drawn above everything, e.g. a status line under the panel. */
	protected void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	/** Gives [widget] a help text: a DNZ help box in the DNZ style, Minecraft's tooltip in the other styles. */
	protected <T extends AbstractWidget> T tip(T widget, Component text) {
		if (Theme.dnzStyle()) {
			this.tips.put(widget, text);
		} else {
			widget.setTooltip(Tooltip.create(text));
		}
		return widget;
	}

	/** GUI units per menu unit (1 outside the DNZ style). */
	protected float menuScale() {
		return this.scale;
	}

	/** The point x, y of the menu (as drawn now) in real GUI units, for the few things Minecraft draws unscaled. */
	protected static Vector2f toGui(GuiGraphicsExtractor g, float x, float y) {
		return g.pose().transformPosition(x, y, new Vector2f());
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		this.extractBlurredBackground(g);
		Theme.screenBackground(g, this.guiWidth, this.guiHeight);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.pose().pushMatrix();
		if (this.scale != 1) {
			// Menu units: drawn scaled, and the mouse read precisely (whole GUI pixels are coarse at a big GUI scale).
			g.pose().scale(this.scale, this.scale);
			Window window = this.minecraft.getWindow();
			mouseX = (int) Math.floor(this.minecraft.mouseHandler.getScaledXPos(window) / this.scale);
			mouseY = (int) Math.floor(this.minecraft.mouseHandler.getScaledYPos(window) / this.scale);
		}
		// Opening: the panel grows from 96% to full size in 0.18 s (ease-out).
		float open = this.openedAt == 0 ? 1.0F : Anim.easeOut((System.nanoTime() - this.openedAt) / 180_000_000.0F);
		if (open < 1.0F && !Theme.javaStyle()) {
			float s = 0.96F + 0.04F * open;
			g.pose().translate(this.width / 2.0F, this.height / 2.0F);
			g.pose().scale(s, s);
			g.pose().translate(-this.width / 2.0F, -this.height / 2.0F);
		}
		// DNZ style: every text in the menu font (Poppins, or the pixel font in the Minecraft look), colors of the theme.
		Theme.smoothAll = Theme.dnzStyle();
		Ui.unitPx = (float) (this.scale * this.minecraft.getWindow().getGuiScale());
		try {
			this.extractMenu(g, mouseX, mouseY, a);
		} finally {
			Theme.smoothAll = false;
			g.pose().popMatrix();
		}
	}

	private void extractMenu(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		Theme.panel(g, this.px, this.py, this.panelW, this.panelH);

		if (Theme.dnzStyle()) {
			this.extractDnzFrame(g);
			// The mouse on an open dropdown list: nothing under the list reacts to it.
			boolean onPopup = false;
			for (GuiEventListener child : this.children()) {
				onPopup |= child instanceof OptionList list && list.popupUnder(mouseX, mouseY);
			}
			int under = onPopup ? -100_000 : 0;
			this.extractContent(g, mouseX + under, mouseY + under, a);
			super.extractRenderState(g, mouseX + under, mouseY + under, a);
			this.extractPopups(g, mouseX, mouseY, onPopup);
			this.extractOverlay(g, mouseX, mouseY, a);
			g.centeredText(this.font, Theme.smooth(Theme.poweredBy()), this.width / 2, this.height - 12, 0xFFFFFFFF);
			return;
		}

		// Sidebar
		g.fill(this.px + 1, this.py + 1, this.px + SIDEBAR_W, this.py + this.panelH - 1, 0x30000000);
		g.fill(this.px + SIDEBAR_W, this.py + 8, this.px + SIDEBAR_W + 1, this.py + this.panelH - 8, 0x30FFFFFF);
		if (Theme.simpleStyle()) {
			// Simple style: bold spaced title, the tab name in bold capitals, thin light divider.
			Theme.spacedText(g, this.font, "DNZ CLIENT", this.px + SIDEBAR_W / 2, this.py + 13, 1.0F, 1, 0xFFFFFFFF, 0.0F);
			Theme.spacedText(g, this.font, this.tab.label().toUpperCase(java.util.Locale.ROOT), this.cx + this.cw / 2, this.py + 11, 1.1F, 2, 0xFFFFFFFF, 0.0F);
			g.fill(this.cx, this.py + 25, this.cx + this.cw, this.py + 26, 0x30FFFFFF);
		} else {
			// Java style: vanilla look, DNZ layout only.
			Theme.heading(g, this.font, Theme.brand(), this.px + SIDEBAR_W / 2, this.py + 12, 1.3F, 0xFFFFFFFF);
			Theme.heading(g, this.font, Component.literal(this.tab.label()), this.cx + this.cw / 2, this.py + 10, 1.3F, 0xFFFFFFFF);
			g.fill(this.cx, this.py + 25, this.cx + this.cw, this.py + 26, 0x40FFFFFF);
		}

		this.extractContent(g, mouseX, mouseY, a);
		super.extractRenderState(g, mouseX, mouseY, a);
		this.extractOverlay(g, mouseX, mouseY, a);

		g.centeredText(this.font, Theme.poweredBy(), this.width / 2, this.height - 12, 0xFFFFFFFF);
	}

	/** Open dropdown lists of the settings pages, then the help box of what the mouse rests on (after 0.35 s). */
	private void extractPopups(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean onPopup) {
		Component text = null;
		Object owner = null;
		for (GuiEventListener child : this.children()) {
			if (child instanceof OptionList list) {
				g.nextStratum();
				list.extractOverlay(g, mouseX, mouseY);
				if (list.tooltip() != null && !onPopup) {
					text = list.tooltip();
					owner = list.hoveredRow();
				}
			}
		}
		for (Map.Entry<AbstractWidget, Component> tip : this.tips.entrySet()) {
			if (tip.getKey().isHovered() && tip.getKey().visible && !onPopup) {
				text = tip.getValue();
				owner = tip.getKey();
			}
		}
		if (owner != this.tipOwner) {
			this.tipOwner = owner;
			this.tipSince = System.nanoTime();
		}
		if (text != null && System.nanoTime() - this.tipSince > 350_000_000L) {
			g.nextStratum();
			Ui.tooltip(g, text, mouseX, mouseY, this.width, this.height);
		}
	}

	// ---------------------------------------------------------------- input in menu units

	private MouseButtonEvent toMenu(MouseButtonEvent event) {
		return this.scale == 1 ? event : new MouseButtonEvent(event.x() / this.scale, event.y() / this.scale, event.buttonInfo());
	}

	/** Runs an input handler with text measured like it is drawn (the smooth menu font). */
	private boolean measuredLikeDrawn(java.util.function.BooleanSupplier handler) {
		boolean before = Theme.smoothAll;
		Theme.smoothAll = Theme.dnzStyle();
		try {
			return handler.getAsBoolean();
		} finally {
			Theme.smoothAll = before;
		}
	}

	@Override
	public final boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		MouseButtonEvent menu = this.toMenu(event);
		return this.measuredLikeDrawn(() -> this.clicked(menu, doubleClick));
	}

	/** Mouse pressed, in menu units. */
	protected boolean clicked(MouseButtonEvent event, boolean doubleClick) {
		// A click beside an open dropdown list only closes it.
		boolean used = false;
		for (GuiEventListener child : this.children()) {
			if (child instanceof OptionList list && list.clickedElsewhere(event.x(), event.y())) {
				used = true;
			}
		}
		return used || super.mouseClicked(event, doubleClick);
	}

	@Override
	public final boolean mouseReleased(MouseButtonEvent event) {
		MouseButtonEvent menu = this.toMenu(event);
		return this.measuredLikeDrawn(() -> this.released(menu));
	}

	/** Mouse let go, in menu units. */
	protected boolean released(MouseButtonEvent event) {
		return super.mouseReleased(event);
	}

	@Override
	public final boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		MouseButtonEvent menu = this.toMenu(event);
		return this.measuredLikeDrawn(() -> this.dragged(menu, dx / this.scale, dy / this.scale));
	}

	/** Mouse moved with a button held, in menu units. */
	protected boolean dragged(MouseButtonEvent event, double dx, double dy) {
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public final boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return this.scrolled(mouseX / this.scale, mouseY / this.scale, scrollX, scrollY);
	}

	/** Mouse wheel, position in menu units. */
	protected boolean scrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public final void mouseMoved(double mouseX, double mouseY) {
		super.mouseMoved(mouseX / this.scale, mouseY / this.scale);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		return this.measuredLikeDrawn(() -> {
			// A settings page that is typing or has a list open gets the key first (Escape closes that, not the menu).
			if (this.getFocused() instanceof OptionList list && (list.holdsEscape() || !event.isEscape()) && list.keyPressed(event)) {
				return true;
			}
			return super.keyPressed(event);
		});
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return this.measuredLikeDrawn(() -> super.charTyped(event));
	}
}
