package com.dnz.schematic.gui;

import com.dnz.schematic.Checker;
import com.dnz.schematic.Compat;
import com.dnz.schematic.DnzSchematic;
import com.dnz.schematic.GhostRenderer;
import com.dnz.schematic.Materials;
import com.dnz.schematic.Placement;
import com.dnz.schematic.S;
import com.dnz.schematic.SchematicConfig;
import com.dnz.schematic.Schematics;
import com.dnz.schematic.litematic.Litematic;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * The M menu. Sits on the left side and leaves the world visible, so moving and turning the schematic
 * can be watched live.
 */
public class SchematicScreen extends Screen {
	private enum Tab {
		FILES("tab.files"), PLACE("tab.place"), LAYERS("tab.layers"), ERRORS("tab.errors"), MATERIALS("tab.materials"), SETTINGS("tab.settings");

		final String key;

		Tab(String key) {
			this.key = key;
		}
	}

	private static final int SIDE = 104;
	private static final int ROW = 20;
	private static Tab tab = Tab.FILES;

	private final Screen parent;
	private int px, py, pw, ph, cx, cy, cw, ch;
	private int scroll;
	private List<Path> files = new ArrayList<>();
	private List<Materials.Row> materials = new ArrayList<>();
	private boolean onlyMissing = true;
	private int ticks;
	private Placement builtFor;

	public SchematicScreen(Screen parent) {
		super(Component.literal("DNZ Schematic"));
		this.parent = parent;
		if (Schematics.placement() != null && tab == Tab.FILES) {
			tab = Tab.PLACE;
		}
	}

	/** Used by DNZ Client's menu (by name, so this mod never needs DNZ Client). */
	public static Screen open(Screen parent) {
		return new SchematicScreen(parent);
	}

	/** Opens a given tab (0 = Files ... 5 = Settings); used by the in-game test. */
	public static Screen open(Screen parent, int tabIndex) {
		tab = Tab.values()[tabIndex];
		return new SchematicScreen(parent);
	}

	@Override
	protected void init() {
		this.builtFor = Schematics.placement();
		this.pw = Math.min(400, this.width - 16);
		this.ph = Math.min(280, this.height - 16);
		this.px = 8;
		this.py = (this.height - this.ph) / 2;
		this.cx = this.px + SIDE + 10;
		this.cw = this.pw - SIDE - 20;
		this.cy = this.py + 32;
		this.ch = this.ph - 40;

		int y = this.py + 34;
		for (Tab t : Tab.values()) {
			this.addRenderableWidget(new UiButton(this.px + 8, y, SIDE - 16, 18, S.t(t.key), () -> this.switchTab(t)).selected(t == tab));
			y += 22;
		}
		this.addRenderableWidget(new UiButton(this.px + 8, this.py + this.ph - 26, SIDE - 16, 18, S.t("close"), this::onClose));

		switch (tab) {
			case FILES -> this.initFiles();
			case PLACE -> this.initPlace();
			case LAYERS -> this.initLayers();
			case ERRORS -> this.initErrors();
			case MATERIALS -> this.initMaterials();
			case SETTINGS -> this.initSettings();
		}
	}

	private void switchTab(Tab t) {
		tab = t;
		this.scroll = 0;
		this.rebuildWidgets();
	}

	private boolean needsPlacement() {
		return Schematics.placement() == null && tab != Tab.FILES && tab != Tab.SETTINGS;
	}

	// ------------------------------------------------------------------ files

	private void initFiles() {
		this.files = Schematics.files();
		int listH = this.ch - 28;
		int visible = listH / ROW;
		this.scroll = Math.clamp(this.scroll, 0, Math.max(0, this.files.size() - visible));
		Path folder = Schematics.folder();
		Placement current = Schematics.placement();
		for (int k = 0; k < visible && this.scroll + k < this.files.size(); k++) {
			Path file = this.files.get(this.scroll + k);
			String name = folder.relativize(file).toString().replace('\\', '/').replaceFirst("(?i)\\.litematic$", "");
			name = this.font.plainSubstrByWidth(name, this.cw - 14);
			boolean loaded = current != null && current.schematic.file.toAbsolutePath().equals(file.toAbsolutePath());
			this.addRenderableWidget(new UiButton(this.cx, this.cy + k * ROW, this.cw, ROW - 2, name, () -> {
				Schematics.load(file);
				tab = Tab.PLACE;
				this.rebuildWidgets();
			}).left().selected(loaded));
		}
		int by = this.cy + this.ch - 20;
		int half = (this.cw - 6) / 2;
		this.addRenderableWidget(new UiButton(this.cx, by, half, 18, S.t("files.open_folder"), () -> Compat.openFolder(Schematics.folder())));
		this.addRenderableWidget(new UiButton(this.cx + half + 6, by, half, 18, S.t("files.refresh"), this::rebuildWidgets));
	}

	// ------------------------------------------------------------------ placement

	private void initPlace() {
		Placement p = Schematics.placement();
		if (p == null) {
			return;
		}
		int y = this.cy + 40;
		String[] axes = {"X", "Y", "Z"};
		for (int a = 0; a < 3; a++) {
			int axis = a;
			int bx = this.cx + 70;
			int[] steps = {-10, -1, 1, 10};
			for (int step : steps) {
				String label = (step > 0 ? "+" : "") + step;
				this.addRenderableWidget(new UiButton(bx, y, 30, 16, label, () -> this.move(axis, step)));
				bx += 33;
			}
			y += 19;
		}
		y += 6;
		int third = (this.cw - 8) / 3;
		this.addRenderableWidget(new UiButton(this.cx, y, third, 18, S.t("place.here"), () -> {
			if (this.minecraft.player != null) {
				p.setOrigin(this.minecraft.player.blockPosition());
				Schematics.saveState();
			}
		}));
		this.addRenderableWidget(new UiButton(this.cx + third + 4, y, third, 18, S.t("place.rotate"), () -> {
			p.setRotation(p.rotation().getRotated(Rotation.CLOCKWISE_90));
			Schematics.saveState();
			this.rebuildWidgets();
		}));
		this.addRenderableWidget(new UiButton(this.cx + 2 * (third + 4), y, third, 18, S.t("place.mirror", mirrorName(p.mirror())), () -> {
			Mirror next = switch (p.mirror()) {
				case NONE -> Mirror.FRONT_BACK;
				case FRONT_BACK -> Mirror.LEFT_RIGHT;
				default -> Mirror.NONE;
			};
			p.setMirror(next);
			Schematics.saveState();
			this.rebuildWidgets();
		}));
		y += 22;
		SchematicConfig config = SchematicConfig.get();
		int half = (this.cw - 4) / 2;
		this.addRenderableWidget(new UiButton(this.cx, y, half, 18, S.t(config.visible ? "place.hide" : "place.show"), () -> {
			config.visible = !config.visible;
			config.save();
			this.rebuildWidgets();
		}));
		this.addRenderableWidget(new UiButton(this.cx + half + 4, y, half, 18, S.t("place.remove"), () -> {
			Schematics.remove();
			tab = Tab.FILES;
			this.rebuildWidgets();
		}).danger());
	}

	private void move(int axis, int step) {
		Placement p = Schematics.placement();
		if (p == null) {
			return;
		}
		BlockPos o = p.origin();
		p.setOrigin(axis == 0 ? o.offset(step, 0, 0) : axis == 1 ? o.offset(0, step, 0) : o.offset(0, 0, step));
		Schematics.saveState();
	}

	private static String mirrorName(Mirror mirror) {
		return S.t(switch (mirror) {
			case FRONT_BACK -> "mirror.x";
			case LEFT_RIGHT -> "mirror.z";
			default -> "mirror.none";
		});
	}

	private static String rotationName(Rotation rotation) {
		return switch (rotation) {
			case CLOCKWISE_90 -> "90°";
			case CLOCKWISE_180 -> "180°";
			case COUNTERCLOCKWISE_90 -> "270°";
			default -> "0°";
		};
	}

	// ------------------------------------------------------------------ layers

	private void initLayers() {
		Placement p = Schematics.placement();
		if (p == null) {
			return;
		}
		SchematicConfig config = SchematicConfig.get();
		int third = (this.cw - 8) / 3;
		String[] modes = {"layer.all", "layer.single", "layer.below"};
		for (int m = 0; m < 3; m++) {
			int mode = m;
			this.addRenderableWidget(new UiButton(this.cx + m * (third + 4), this.cy + 14, third, 18, S.t(modes[m]), () -> {
				config.layerMode = mode;
				config.save();
				this.rebuildWidgets();
			}).selected(config.layerMode == m));
		}
		int y = this.cy + 58;
		this.addRenderableWidget(new UiButton(this.cx, y, 24, 18, "-", () -> {
			Schematics.changeLayer(-1);
			this.rebuildWidgets();
		}));
		this.addRenderableWidget(new UiButton(this.cx + 100, y, 24, 18, "+", () -> {
			Schematics.changeLayer(1);
			this.rebuildWidgets();
		}));
		this.addRenderableWidget(new UiButton(this.cx + 132, y, this.cw - 132, 18, S.t("layer.mine"), () -> {
			if (this.minecraft.player != null) {
				int rel = this.minecraft.player.blockPosition().getY() - p.origin().getY();
				config.layer = Math.clamp(rel, 0, p.schematic.sizeY - 1);
				if (config.layerMode == 0) {
					config.layerMode = 1;
				}
				config.save();
				this.rebuildWidgets();
			}
		}));
	}

	// ------------------------------------------------------------------ mistakes

	private void initErrors() {
		if (Schematics.placement() == null) {
			return;
		}
		SchematicConfig config = SchematicConfig.get();
		int y = this.cy + 104;
		this.addRenderableWidget(new UiButton(this.cx, y, this.cw, 18, S.t("errors.show_missing", onOff(config.showMissing)), () -> {
			config.showMissing = !config.showMissing;
			config.save();
			this.rebuildWidgets();
		}).left());
		this.addRenderableWidget(new UiButton(this.cx, y + 21, this.cw, 18, S.t("errors.show_wrong", onOff(config.showWrong)), () -> {
			config.showWrong = !config.showWrong;
			config.save();
			this.rebuildWidgets();
		}).left());
		this.addRenderableWidget(new UiButton(this.cx, y + 42, this.cw, 18, S.t("errors.show_extra", onOff(config.showExtra)), () -> {
			config.showExtra = !config.showExtra;
			config.save();
			this.rebuildWidgets();
		}).left());
	}

	// ------------------------------------------------------------------ materials

	private void initMaterials() {
		if (Schematics.placement() == null) {
			return;
		}
		this.addRenderableWidget(new UiButton(this.cx + this.cw - 110, this.cy, 110, 16, S.t(this.onlyMissing ? "materials.only_left" : "materials.all"), () -> {
			this.onlyMissing = !this.onlyMissing;
			this.scroll = 0;
			this.rebuildWidgets();
		}));
		this.refreshMaterials();
	}

	private void refreshMaterials() {
		Placement p = Schematics.placement();
		Checker checker = Schematics.checker();
		if (p == null || checker == null) {
			this.materials = new ArrayList<>();
			return;
		}
		List<Materials.Row> rows = Materials.compute(p, checker, this.minecraft.player);
		if (this.onlyMissing) {
			rows = rows.stream().filter(r -> r.left() > 0).toList();
		}
		this.materials = rows;
	}

	private int materialRows() {
		return (this.ch - 34) / 18;
	}

	// ------------------------------------------------------------------ settings

	private void initSettings() {
		SchematicConfig config = SchematicConfig.get();
		int y = this.cy + 6;
		this.addRenderableWidget(new UiButton(this.cx, y, this.cw, 18, S.t("settings.range", config.range), () -> {
			int[] values = {16, 24, 32, 48, 64};
			config.range = next(values, config.range);
			config.save();
			this.rebuildWidgets();
		}).left());
		this.addRenderableWidget(new UiButton(this.cx, y + 22, this.cw, 18, S.t("settings.alpha", config.ghostAlpha), () -> {
			int[] values = {30, 45, 55, 70, 85};
			config.ghostAlpha = next(values, config.ghostAlpha);
			config.save();
			this.rebuildWidgets();
		}).left());
		this.addRenderableWidget(new UiButton(this.cx, y + 44, this.cw, 18, S.t("settings.hud", onOff(config.hud)), () -> {
			config.hud = !config.hud;
			config.save();
			this.rebuildWidgets();
		}).left());
	}

	private static int next(int[] values, int current) {
		for (int v : values) {
			if (v > current) {
				return v;
			}
		}
		return values[0];
	}

	private static String onOff(boolean on) {
		return S.t(on ? "on" : "off");
	}

	// ------------------------------------------------------------------ input and drawing

	@Override
	public void tick() {
		// A schematic finished loading (or was removed) since the buttons were made.
		if (Schematics.placement() != this.builtFor) {
			this.rebuildWidgets();
		}
		// Keep counts and the material list live while the menu is open.
		if (tab == Tab.MATERIALS && ++this.ticks % 20 == 0) {
			this.refreshMaterials();
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY != 0 && mouseX >= this.cx && mouseX < this.cx + this.cw && mouseY >= this.cy && mouseY < this.cy + this.ch) {
			int before = this.scroll;
			int max = tab == Tab.FILES ? Math.max(0, this.files.size() - (this.ch - 28) / ROW)
				: tab == Tab.MATERIALS ? Math.max(0, this.materials.size() - this.materialRows()) : 0;
			this.scroll = Math.clamp(this.scroll - (int) Math.signum(scrollY) * (tab == Tab.MATERIALS ? 3 : 1), 0, max);
			if (this.scroll != before && tab == Tab.FILES) {
				this.rebuildWidgets();
			}
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (DnzSchematic.MENU_KEY.matches(event)) {
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		// No blur or darkening: the schematic in the world stays visible while adjusting it.
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		Ui.roundedRect(g, this.px, this.py, this.pw, this.ph, 0xE0141824);
		Ui.roundedOutline(g, this.px, this.py, this.pw, this.ph, 0x38FFFFFF);
		g.fill(this.px + SIDE, this.py + 8, this.px + SIDE + 1, this.py + this.ph - 8, 0x30FFFFFF);
		g.centeredText(this.font, Component.literal("DNZ SCHEMATIC"), this.px + SIDE / 2, this.py + 12, 0xFFFFFFFF);
		g.fill(this.px + 24, this.py + 24, this.px + SIDE - 24, this.py + 25, UiButton.ACCENT);
		g.centeredText(this.font, Component.literal(S.t(tab.key)), this.cx + this.cw / 2, this.py + 12, 0xFFFFFFFF);
		g.fill(this.cx, this.py + 24, this.cx + this.cw, this.py + 25, 0x30FFFFFF);

		if (this.needsPlacement()) {
			this.wrapped(g, S.t("need_schematic"), this.cy + 10, 0xFFAAB0C0);
		} else {
			switch (tab) {
				case FILES -> this.drawFiles(g);
				case PLACE -> this.drawPlace(g);
				case LAYERS -> this.drawLayers(g);
				case ERRORS -> this.drawErrors(g);
				case MATERIALS -> this.drawMaterials(g);
				case SETTINGS -> this.drawSettings(g);
			}
		}
		super.extractRenderState(g, mouseX, mouseY, a);

		String message = Schematics.message();
		if (message != null) {
			g.text(this.font, message, this.px + 4, this.py + this.ph + 4, 0xFFFFD60A);
		}
	}

	private void drawFiles(GuiGraphicsExtractor g) {
		if (this.files.isEmpty()) {
			int y = this.wrapped(g, S.t("files.empty"), this.cy + 6, 0xFFFFFFFF);
			y = this.wrapped(g, S.t("files.where"), y + 6, 0xFFAAB0C0);
			this.wrapped(g, Schematics.folder().toAbsolutePath().toString(), y + 2, 0xFF9FD0FF);
		}
		if (Schematics.loading()) {
			g.text(this.font, S.t("files.loading"), this.cx, this.cy + this.ch - 34, 0xFFFFD60A);
		}
	}

	private void drawPlace(GuiGraphicsExtractor g) {
		Placement p = Schematics.placement();
		Litematic s = p.schematic;
		g.text(this.font, this.font.plainSubstrByWidth(s.name + (s.author.isBlank() ? "" : "  ·  " + s.author), this.cw), this.cx, this.cy, 0xFFFFFFFF);
		g.text(this.font, S.t("place.size", s.sizeX, s.sizeY, s.sizeZ, s.nonAirCount()), this.cx, this.cy + 12, 0xFFAAB0C0);
		g.text(this.font, S.t("place.turn", rotationName(p.rotation()), mirrorName(p.mirror())), this.cx, this.cy + 24, 0xFFAAB0C0);
		int[] values = {p.origin().getX(), p.origin().getY(), p.origin().getZ()};
		String[] axes = {"X", "Y", "Z"};
		for (int a = 0; a < 3; a++) {
			int y = this.cy + 44 + a * 19;
			g.text(this.font, axes[a] + ":", this.cx, y, 0xFF9FD0FF);
			g.text(this.font, String.valueOf(values[a]), this.cx + 16, y, 0xFFFFFFFF);
		}
		this.wrapped(g, S.t("place.hint"), this.cy + 160, 0xFF7F8696);
	}

	private void drawLayers(GuiGraphicsExtractor g) {
		Placement p = Schematics.placement();
		SchematicConfig config = SchematicConfig.get();
		g.text(this.font, S.t("layer.mode"), this.cx, this.cy, 0xFFAAB0C0);
		g.text(this.font, S.t("layer.which"), this.cx, this.cy + 44, 0xFFAAB0C0);
		String value = (config.layer + 1) + " / " + p.schematic.sizeY;
		g.centeredText(this.font, Component.literal(value), this.cx + 62, this.cy + 63, config.layerMode == 0 ? 0xFF7F8696 : 0xFFFFFFFF);
		this.wrapped(g, S.t("layer.hint"), this.cy + 90, 0xFF7F8696);
	}

	private void drawErrors(GuiGraphicsExtractor g) {
		Checker c = Schematics.checker();
		Placement p = Schematics.placement();
		int total = p.schematic.nonAirCount();
		int ok = c.count(Checker.OK);
		int y = this.cy;
		g.text(this.font, S.t("errors.progress", ok, total, total == 0 ? 100 : ok * 100L / total), this.cx, y, 0xFF7CE38B);
		// Progress bar
		g.fill(this.cx, y + 12, this.cx + this.cw, y + 16, 0x40FFFFFF);
		g.fill(this.cx, y + 12, this.cx + (int) ((long) this.cw * ok / Math.max(1, total)), y + 16, 0xFF7CE38B);
		y += 24;
		this.countLine(g, y, GhostRenderer.BLUE, S.t("errors.missing", c.count(Checker.MISSING)));
		this.countLine(g, y + 12, GhostRenderer.RED, S.t("errors.wrong", c.count(Checker.WRONG_BLOCK)));
		this.countLine(g, y + 24, GhostRenderer.YELLOW, S.t("errors.wrong_state", c.count(Checker.WRONG_STATE)));
		this.countLine(g, y + 36, GhostRenderer.ORANGE, S.t("errors.extra", c.count(Checker.EXTRA)));
		if (!c.complete()) {
			g.text(this.font, S.t("hud.checking"), this.cx, y + 52, 0xFFAAAAAA);
		} else if (c.count(Checker.UNKNOWN) > 0) {
			g.text(this.font, S.t("errors.unloaded", c.count(Checker.UNKNOWN)), this.cx, y + 52, 0xFFAAAAAA);
		}
		BlockPos nearest = Schematics.nearestError();
		if (nearest != null) {
			g.text(this.font, S.t("errors.nearest", nearest.getX(), nearest.getY(), nearest.getZ()), this.cx, y + 64, 0xFFFFFFFF);
		}
	}

	private void countLine(GuiGraphicsExtractor g, int y, int rgb, String text) {
		g.fill(this.cx, y + 1, this.cx + 7, y + 8, 0xFF000000 | rgb);
		g.text(this.font, text, this.cx + 12, y, 0xFFFFFFFF);
	}

	private void drawMaterials(GuiGraphicsExtractor g) {
		int y = this.cy + 4;
		g.text(this.font, S.t("materials.title", this.materials.size()), this.cx, y, 0xFFAAB0C0);
		int top = this.cy + 22;
		int colLeft = this.cx + this.cw - 120, colHave = this.cx + this.cw - 70, colMissing = this.cx + this.cw - 20;
		g.centeredText(this.font, Component.literal(S.t("materials.left")), colLeft, top - 2, 0xFF7F8696);
		g.centeredText(this.font, Component.literal(S.t("materials.have")), colHave, top - 2, 0xFF7F8696);
		g.centeredText(this.font, Component.literal(S.t("materials.missing")), colMissing, top - 2, 0xFF7F8696);
		int rows = this.materialRows();
		this.scroll = Math.clamp(this.scroll, 0, Math.max(0, this.materials.size() - rows));
		for (int k = 0; k < rows && this.scroll + k < this.materials.size(); k++) {
			Materials.Row r = this.materials.get(this.scroll + k);
			int ry = top + 10 + k * 18;
			if (k % 2 == 0) {
				g.fill(this.cx, ry - 1, this.cx + this.cw, ry + 17, 0x18FFFFFF);
			}
			g.item(r.icon(), this.cx + 1, ry);
			g.text(this.font, this.font.plainSubstrByWidth(r.name(), colLeft - this.cx - 40), this.cx + 20, ry + 5, 0xFFFFFFFF);
			g.centeredText(this.font, Component.literal(String.valueOf(r.left())), colLeft, ry + 5, r.left() == 0 ? 0xFF7CE38B : 0xFFFFFFFF);
			g.centeredText(this.font, Component.literal(String.valueOf(r.have())), colHave, ry + 5, 0xFFAAB0C0);
			g.centeredText(this.font, Component.literal(String.valueOf(r.missing())), colMissing, ry + 5, r.missing() > 0 ? 0xFFFF6B6B : 0xFF7CE38B);
		}
		if (this.materials.isEmpty()) {
			g.text(this.font, S.t("materials.none"), this.cx, top + 12, 0xFF7CE38B);
		}
	}

	private void drawSettings(GuiGraphicsExtractor g) {
		this.wrapped(g, S.t("settings.keys"), this.cy + 80, 0xFF7F8696);
	}

	/** Draws text wrapped to the content width; returns the y below it. */
	private int wrapped(GuiGraphicsExtractor g, String text, int y, int color) {
		for (var line : this.font.split(Component.literal(text), this.cw)) {
			g.text(this.font, line, this.cx, y, color);
			y += 10;
		}
		return y;
	}
}
