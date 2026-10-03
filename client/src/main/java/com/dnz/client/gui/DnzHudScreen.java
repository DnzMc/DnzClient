package com.dnz.client.gui;

import com.dnz.client.DnzConfig;
import com.dnz.client.L;
import com.dnz.client.hud.DnzHud;
import com.dnz.client.hud.HudModule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * HUD editor: drag panels (they snap to the screen center, edges and each other), scroll to resize,
 * right-click a panel for its settings.
 */
public class DnzHudScreen extends Screen {
	private static final int SNAP = 4;
	private static final int POPUP_W = 150;

	private final Screen parent;
	private String dragging;
	private double offX;
	private double offY;
	/** Guide lines shown while a panel snapped: x of vertical lines, y of horizontal lines. */
	private final List<Integer> guidesX = new ArrayList<>();
	private final List<Integer> guidesY = new ArrayList<>();
	/** Panel whose settings popup is open. */
	private String popup;
	private int popX;
	private int popY;
	private int popH;

	public DnzHudScreen(Screen parent) {
		super(Component.literal("HUD"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int bw = 80;
		int x = this.width / 2 - (bw * 3 + 8) / 2;
		int y = this.height - 28;
		this.addRenderableWidget(new DnzButton(x, y, bw, 20, Component.literal(L.t("hud.modules")), this::onClose));
		this.addRenderableWidget(new DnzButton(x + bw + 4, y, bw, 20, Component.literal(L.t("hud.reset")), () -> {
			DnzHud.reset();
			this.popup = null;
			this.rebuildWidgets();
		}));
		this.addRenderableWidget(new DnzButton(x + 2 * (bw + 4), y, bw, 20, Component.literal(L.t("done")), () -> {
			DnzConfig.get().save();
			this.minecraft.gui.setScreen(null);
		}).selected(true));

		if (this.popup != null) {
			this.initPopup();
		}
	}

	private void initPopup() {
		HudModule m = DnzHud.module(this.popup);
		if (m == null) {
			this.popup = null;
			return;
		}
		DnzConfig.HudPos p = m.pos();
		int rows = 3 + m.options().size();
		this.popH = 22 + rows * 20 + 4;
		int[] at = DnzHud.screenPos(m.id, this.width, this.height);
		int[] size = DnzHud.size(m.id);
		// Next to the panel, on whichever side has room.
		this.popX = at[0] + size[0] + 6 + POPUP_W < this.width ? at[0] + size[0] + 6 : Math.max(2, at[0] - POPUP_W - 6);
		this.popY = Math.max(2, Math.min(at[1], this.height - this.popH - 34));
		int x = this.popX + 6, w = POPUP_W - 12;
		int y = this.popY + 22;

		this.addRenderableWidget(new DnzButton(x, y, 20, 18, Component.literal("-"), () -> this.setScale(p, p.scale - 0.1F)));
		this.addRenderableWidget(new DnzButton(x + w - 20, y, 20, 18, Component.literal("+"), () -> this.setScale(p, p.scale + 0.1F)));
		y += 20;
		this.addRenderableWidget(new DnzButton(x, y, w, 18, Component.literal(L.t("hud.opt.bg") + ": " + onOff(p.bg)), () -> {
			p.bg = !p.bg;
			this.changed();
		}).selected(p.bg));
		y += 20;
		for (String key : m.options()) {
			boolean on = m.opt(key);
			this.addRenderableWidget(new DnzButton(x, y, w, 18, Component.literal(L.t("hud.opt." + key) + ": " + onOff(on)), () -> {
				p.opts.put(key, !on);
				this.changed();
			}).selected(on));
			y += 20;
		}
		this.addRenderableWidget(new DnzButton(x, y, w, 18, Component.literal(L.t("hud.hide")), () -> {
			m.setEnabled(false);
			this.popup = null;
			this.changed();
		}));
	}

	private void setScale(DnzConfig.HudPos p, float scale) {
		p.scale = Math.round(Math.clamp(scale, 0.5F, 2.5F) * 10) / 10.0F;
		this.changed();
	}

	private void changed() {
		DnzConfig.get().save();
		this.rebuildWidgets();
	}

	private static String onOff(boolean on) {
		return on ? L.t("on") : L.t("off");
	}

	@Override
	public void onClose() {
		DnzConfig.get().save();
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fill(0, 0, this.width, this.height, 0x40000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		// Center lines, faint, so centering is easy.
		g.fill(this.width / 2, 0, this.width / 2 + 1, this.height, 0x18FFFFFF);
		g.fill(0, this.height / 2, this.width, this.height / 2 + 1, 0x18FFFFFF);

		DnzHud.renderAll(g, true);
		for (String id : DnzHud.elements()) {
			int[] size = DnzHud.size(id);
			if (size[0] <= 0 || !DnzHud.module(id).enabled()) {
				continue;
			}
			int[] at = DnzHud.screenPos(id, this.width, this.height);
			boolean hot = id.equals(this.dragging) || id.equals(this.popup) || (this.dragging == null && this.hit(id, mouseX, mouseY));
			g.outline(at[0] - 1, at[1] - 1, size[0] + 2, size[1] + 2, hot ? Theme.accent() : 0x50FFFFFF);
		}
		if (this.dragging != null) {
			for (int x : this.guidesX) {
				g.fill(x, 0, x + 1, this.height, 0xC0FF4FD8);
			}
			for (int y : this.guidesY) {
				g.fill(0, y, this.width, y + 1, 0xC0FF4FD8);
			}
		}

		g.centeredText(this.font, Component.literal(L.t("hud.drag_hint")), this.width / 2, this.height - 42, 0xFFFFFFFF);

		if (this.popup != null) {
			HudModule m = DnzHud.module(this.popup);
			Theme.panel(g, this.popX, this.popY, POPUP_W, this.popH);
			g.centeredText(this.font, Component.literal(m.label()), this.popX + POPUP_W / 2, this.popY + 7, 0xFFFFFFFF);
			String scale = L.t("hud.opt.size") + " " + Math.round(m.pos().scale * 100) + "%";
			g.centeredText(this.font, Component.literal(scale), this.popX + POPUP_W / 2, this.popY + 27, 0xFFFFFFFF);
		}
		super.extractRenderState(g, mouseX, mouseY, a);
	}

	private boolean hit(String id, double mx, double my) {
		int[] size = DnzHud.size(id);
		if (size[0] <= 0) {
			return false;
		}
		int[] at = DnzHud.screenPos(id, this.width, this.height);
		return mx >= at[0] && my >= at[1] && mx < at[0] + size[0] && my < at[1] + size[1];
	}

	private String panelAt(double mx, double my) {
		for (String id : DnzHud.elements().reversed()) {
			if (DnzHud.module(id).enabled() && this.hit(id, mx, my)) {
				return id;
			}
		}
		return null;
	}

	private boolean overPopup(double mx, double my) {
		return this.popup != null && mx >= this.popX && my >= this.popY && mx < this.popX + POPUP_W && my < this.popY + this.popH;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick) || this.overPopup(event.x(), event.y())) {
			return true;
		}
		String id = this.panelAt(event.x(), event.y());
		if (event.button() == 1) {
			// Right click: settings of that panel (or close the open ones).
			this.popup = id;
			this.rebuildWidgets();
			return true;
		}
		if (this.popup != null) {
			this.popup = null;
			this.rebuildWidgets();
		}
		if (id != null) {
			int[] at = DnzHud.screenPos(id, this.width, this.height);
			this.dragging = id;
			this.offX = event.x() - at[0];
			this.offY = event.y() - at[1];
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (this.dragging == null) {
			return super.mouseDragged(event, dx, dy);
		}
		int[] size = DnzHud.size(this.dragging);
		double x = Math.max(0, Math.min(this.width - size[0], event.x() - this.offX));
		double y = Math.max(0, Math.min(this.height - size[1], event.y() - this.offY));
		this.guidesX.clear();
		this.guidesY.clear();
		x = this.snap(x, size[0], true);
		y = this.snap(y, size[1], false);
		DnzConfig.HudPos pos = DnzHud.pos(this.dragging);
		pos.x = (float) (x / this.width);
		pos.y = (float) (y / this.height);
		return true;
	}

	/** Snaps a panel edge or center to the screen edges/center or to another panel's edges. */
	private double snap(double v, int size, boolean horizontal) {
		int screen = horizontal ? this.width : this.height;
		List<Integer> targets = new ArrayList<>(List.of(0, screen / 2, screen));
		for (String id : DnzHud.elements()) {
			int[] s = DnzHud.size(id);
			if (id.equals(this.dragging) || s[0] <= 0 || !DnzHud.module(id).enabled()) {
				continue;
			}
			int[] at = DnzHud.screenPos(id, this.width, this.height);
			int start = horizontal ? at[0] : at[1];
			int length = horizontal ? s[0] : s[1];
			targets.add(start);
			targets.add(start + length);
		}
		// Try the panel's start, center and end against every target; take the closest.
		double best = SNAP + 1;
		double result = v;
		int guide = -1;
		double[] anchors = {0, size / 2.0, size};
		for (int t : targets) {
			for (double anchor : anchors) {
				double d = Math.abs(v + anchor - t);
				if (d < best) {
					best = d;
					result = t - anchor;
					guide = t;
				}
			}
		}
		if (guide >= 0) {
			(horizontal ? this.guidesX : this.guidesY).add(Math.min(guide, screen - 1));
		}
		return Math.max(0, Math.min(screen - size, result));
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (this.dragging != null) {
			this.dragging = null;
			this.guidesX.clear();
			this.guidesY.clear();
			DnzConfig.get().save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		String id = this.panelAt(mouseX, mouseY);
		if (id != null && scrollY != 0) {
			DnzConfig.HudPos p = DnzHud.pos(id);
			this.setScale(p, p.scale + (scrollY > 0 ? 0.1F : -0.1F));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}
}
