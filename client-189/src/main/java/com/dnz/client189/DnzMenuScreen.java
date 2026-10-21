package com.dnz.client189;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiControls;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * The DNZ menu for 1.8.9, laid out like DNZ Client 26.x: a near-black panel with an icon rail on the left
 * (orange DNZ logo, pages, the player's face), "DNZ CLIENT" over the big page title, a search box and close
 * button at the top, and the modules as dark cards (orange edge when on) in a grid.
 */
public class DnzMenuScreen extends GuiScreen {
	private static final int PANEL_W = 560;
	private static final int PANEL_H = 340;
	private static final int RAIL_W = 34;
	private static final int COLS = 4;
	private static final int GAP = 6;
	private static final int CARD_H = 58;
	private static int accent() {
		return GameTheme.accent();
	}
	private static final int MUTED = 0xFF8D97A8;

	private final GuiScreen parent;
	private final long openedAt;
	private float scale;
	private float uw;
	private float uh;
	private int px;
	private int py;
	private int cx;
	private int cw;

	private static String query = "";
	private boolean searchFocused;
	private float scroll;
	private float scrollTarget;
	private final Map<String, float[]> anim = new HashMap<String, float[]>();
	private final float[] railHover = new float[4];
	private float closeHover;
	private String tip;

	public DnzMenuScreen(GuiScreen parent) {
		this.parent = parent;
		this.openedAt = parent instanceof DnzShiftScreen ? 0 : System.currentTimeMillis();
	}

	@Override
	public void initGui() {
		Blur.on();
		GameTheme.refresh();
		Keyboard.enableRepeatEvents(true);
		this.scale = Math.min(this.width * 0.72F / PANEL_W, this.height * 0.78F / PANEL_H);
		this.uw = this.width / this.scale;
		this.uh = this.height / this.scale;
		this.px = Math.round((this.uw - PANEL_W) / 2);
		this.py = Math.round((this.uh - PANEL_H) / 2);
		this.cx = this.px + RAIL_W + 10;
		this.cw = PANEL_W - RAIL_W - 22;
	}

	@Override
	public void onGuiClosed() {
		Keyboard.enableRepeatEvents(false);
		if (!(this.mc.currentScreen instanceof DnzShiftScreen) && !(this.mc.currentScreen instanceof HudEditorScreen)) {
			Blur.off(this.mc);
		}
	}

	// ------------------------------------------------------------------ drawing

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		if (this.mc.theWorld == null) {
			this.drawDefaultBackground();
		}
		if (GameTheme.background != null) {
			GameTheme.cover(GameTheme.background, 0, 0, this.width, this.height, 1);
			drawRect(0, 0, this.width, this.height, Math.round(GameTheme.backgroundDim * 255) << 24);
		}
		float mx = mouseX / this.scale;
		float my = mouseY / this.scale;
		float open = this.openedAt == 0 ? 1 : Math.min(1, (System.currentTimeMillis() - this.openedAt) / 200F);
		float ease = 1 - (1 - open) * (1 - open) * (1 - open);
		this.tip = null;

		GlStateManager.pushMatrix();
		GlStateManager.scale(this.scale, this.scale, 1);
		GlStateManager.translate(this.uw / 2, this.uh / 2, 0);
		GlStateManager.scale(0.96F + 0.04F * ease, 0.96F + 0.04F * ease, 1);
		GlStateManager.translate(-this.uw / 2, -this.uh / 2 + (1 - ease) * 6, 0);

		this.drawFrame(mx, my);
		this.drawGrid(mx, my);
		if (this.tip != null) {
			float tw = TtfFont.MEDIUM.width(this.tip, 6) + 10;
			Render2D.roundedRect(mx + 8, my + 6, tw, 13, 4, 0xF0141A24);
			TtfFont.MEDIUM.draw(this.tip, mx + 13, my + 9.5F, 6, 0xFFE6EAF2);
		}
		GlStateManager.popMatrix();
	}

	private void drawFrame(float mx, float my) {
		// Panel: near-black with a soft shadow and a thin edge
		float r = GameTheme.radius;
		if (GameTheme.glow > 0) {
			Render2D.shadow(this.px, this.py, PANEL_W, PANEL_H, r, 22, Math.round(0x99 * GameTheme.glow) << 24 | (accent() & 0xFFFFFF));
		}
		Render2D.shadow(this.px, this.py + 5, PANEL_W, PANEL_H, r, 16, 0x99000000);
		Render2D.roundedRect(this.px - 0.5F, this.py - 0.5F, PANEL_W + 1, PANEL_H + 1, r + 0.5F, GameTheme.panelEdge);
		Render2D.roundedRect(this.px, this.py, PANEL_W, PANEL_H, r, GameTheme.panel);
		if (GameTheme.panelGradient != 0) {
			drawGradientRect(this.px, (int) (this.py + r), this.px + PANEL_W, (int) (this.py + PANEL_H - r), GameTheme.panel, (GameTheme.panel & 0xFF000000) | (GameTheme.panelGradient & 0xFFFFFF));
		}
		if (GameTheme.pattern != null) {
			GameTheme.cover(GameTheme.pattern, this.px + r / 2, this.py + r / 2, PANEL_W - r, PANEL_H - r, GameTheme.patternOpacity);
		}
		this.drawStickers();

		// Logo: orange square with "DNZ".
		Render2D.roundedRect(this.px + 9, this.py + 9, 16, 16, 4, accent());
		TtfFont.BOLD.drawCentered("DNZ", this.px + 17, this.py + 13.8F, 5.2F, 0xFF0A0D12);

		// Icon rail
		char[] icons = {Icons.MODS, Icons.EDIT_HUD, Icons.KEYBINDS, Icons.PREFERENCES};
		String[] names = {Lang.t("Modlar", "Mods"), Lang.t("HUD Düzenle", "Edit HUD"), Lang.t("Tuşlar", "Keybinds"), Lang.t("Ayarlar", "Settings")};
		for (int i = 0; i < icons.length; i++) {
			float x = this.px + 7;
			float y = this.py + 34 + i * 24;
			boolean over = in(mx, my, x, y, 20, 20);
			this.railHover[i] = Anim.approach(this.railHover[i], over ? 1 : 0);
			boolean selected = i == 0;
			if (selected) {
				Render2D.roundedRect(x, y, 20, 20, 5, 0x29FF8A3D);
			} else if (this.railHover[i] > 0) {
				Render2D.roundedRect(x, y, 20, 20, 5, Render2D.lerp(0x00FFFFFF, 0x14FFFFFF, this.railHover[i]));
			}
			int color = selected ? accent() : Render2D.lerp(0xFF6F7787, 0xFFFFFFFF, this.railHover[i]);
			TtfFont.icon(icons[i], x + 5, y + 5, 10, color);
			if (over) {
				this.tip = names[i];
			}
		}
		this.drawPlayerFace(this.px + 10, this.py + PANEL_H - 24);

		// "DNZ CLIENT" tag and the page title
		float tagW = TtfFont.BOLD.width("DNZ CLIENT", 5) + 10;
		Render2D.roundedRect(this.cx, this.py + 10, tagW, 10, 3, 0x29000000 | (accent() & 0xFFFFFF));
		TtfFont.BOLD.draw("DNZ CLIENT", this.cx + 5, this.py + 12.4F, 5, accent());
		TtfFont.BOLD.draw(Lang.t("Modlar", "Mods"), this.cx - 0.5F, this.py + 24, 18, GameTheme.text);

		// Search box
		float sw = Math.min(124, this.cw * 0.29F);
		float sx = this.cx + this.cw - sw;
		float sy = this.py + 19;
		Render2D.roundedRect(sx - 0.5F, sy - 0.5F, sw + 1, 19, 6.5F, this.searchFocused ? 0xB3000000 | (accent() & 0xFFFFFF) : GameTheme.panelEdge);
		Render2D.roundedRect(sx, sy, sw, 18, 6, GameTheme.search);
		TtfFont.icon(Icons.SEARCH, sx + 5, sy + 5, 8, 0xFF6F7787);
		if (query.isEmpty() && !this.searchFocused) {
			TtfFont.REGULAR.draw(Lang.t("Mod ara...", "Search mods..."), sx + 16, sy + 5.5F, 7, 0xFF6F7787);
		} else {
			boolean caret = this.searchFocused && System.currentTimeMillis() / 500 % 2 == 0;
			TtfFont.REGULAR.draw(query + (caret ? "|" : ""), sx + 16, sy + 5.5F, 7, 0xFFFFFFFF);
		}

		// close
		float clx = this.px + PANEL_W - 19;
		float cly = this.py + 6;
		this.closeHover = Anim.approach(this.closeHover, in(mx, my, clx, cly, 13, 13) ? 1 : 0);
		TtfFont.icon(Icons.CLOSE, clx + 1.5F, cly + 1.5F, 10, Render2D.lerp(0xFF6F7787, 0xFFFFFFFF, this.closeHover));
	}

	private void drawStickers() {
		double t = System.nanoTime() / 1e9;
		for (int i = 0; i < GameTheme.stickers.size(); i++) {
			GameTheme.Sticker s = GameTheme.stickers.get(i);
			float x = s.anchor.endsWith("left") ? this.px + s.x : this.px + PANEL_W - s.size + s.x;
			float y = s.anchor.startsWith("top") ? this.py + s.y : this.py + PANEL_H - s.size + s.y;
			if (s.floating) {
				y += (float) Math.sin(t * 2 + i) * 2.5F;
			}
			float h = s.size * s.picture.height / s.picture.width;
			GlStateManager.pushMatrix();
			GlStateManager.translate(x + s.size / 2, y + s.size / 2, 0);
			GlStateManager.rotate(s.spin, 0, 0, 1);
			GameTheme.draw(s.picture, -s.size / 2, -h / 2, s.size, h);
			GlStateManager.popMatrix();
		}
	}

	private void drawPlayerFace(float x, float y) {
		if (this.mc.thePlayer == null) {
			Render2D.roundedRect(x, y, 14, 14, 3, accent());
			return;
		}
		this.mc.getTextureManager().bindTexture(this.mc.thePlayer.getLocationSkin());
		GlStateManager.enableBlend();
		GlStateManager.color(1, 1, 1, 1);
		GlStateManager.pushMatrix();
		GlStateManager.translate(x, y, 0);
		GlStateManager.scale(14 / 8F, 14 / 8F, 1);
		drawScaledCustomSizeModalRect(0, 0, 8, 8, 8, 8, 8, 8, 64, 64);
		drawScaledCustomSizeModalRect(0, 0, 40, 8, 8, 8, 8, 8, 64, 64);
		GlStateManager.popMatrix();
	}

	private List<Module> cards() {
		List<Module> list = new ArrayList<Module>();
		String q = query.trim().toLowerCase();
		for (Module m : Modules.ALL) {
			if (q.isEmpty() || m.name().toLowerCase().contains(q) || m.id.contains(q)) {
				list.add(m);
			}
		}
		return list;
	}

	private float gridTop() {
		return this.py + 52;
	}

	private float gridHeight() {
		return PANEL_H - 60;
	}

	private float cardW() {
		return (this.cw - GAP * (COLS - 1)) / (float) COLS;
	}

	private void drawGrid(float mx, float my) {
		List<Module> list = this.cards();
		float top = this.gridTop();
		float height = this.gridHeight();
		int rows = (list.size() + COLS - 1) / COLS;
		float max = Math.max(0, rows * (CARD_H + GAP) - GAP - height);
		this.scrollTarget = Math.max(0, Math.min(max, this.scrollTarget));
		this.scroll = Anim.approach(this.scroll, this.scrollTarget, 16);
		if (list.isEmpty()) {
			TtfFont.REGULAR.drawCentered(Lang.t("Sonuç yok", "No results"), this.cx + this.cw / 2F, top + height / 2 - 4, 8, MUTED);
			return;
		}
		Scissor.push(this.cx * this.scale, top * this.scale, (this.cw + 8) * this.scale, height * this.scale, this.mc);
		float w = this.cardW();
		for (int i = 0; i < list.size(); i++) {
			Module m = list.get(i);
			float x = this.cx + (i % COLS) * (w + GAP);
			float y = top + (i / COLS) * (CARD_H + GAP) - this.scroll;
			if (y + CARD_H < top || y > top + height) {
				continue;
			}
			boolean over = in(mx, my, x, y, w, CARD_H) && my >= top && my < top + height;
			float[] st = this.anim.get(m.id);
			if (st == null) {
				st = new float[] {0, m.enabled() ? 1 : 0};
				this.anim.put(m.id, st);
			}
			st[0] = Anim.approach(st[0], over ? 1 : 0);
			st[1] = Anim.approach(st[1], m.enabled() ? 1 : 0, 12);
			int edge = Render2D.lerp(Render2D.lerp(GameTheme.cardEdge, Render2D.lerp(GameTheme.cardEdge, 0xFFFFFFFF, 0.18F), st[0]), 0x99000000 | (accent() & 0xFFFFFF), st[1]);
			Render2D.roundedRect(x - 0.75F, y - 0.75F, w + 1.5F, CARD_H + 1.5F, GameTheme.cardRadius + 0.75F, edge);
			Render2D.roundedRect(x, y, w, CARD_H, GameTheme.cardRadius, Render2D.lerp(GameTheme.card, GameTheme.cardHover, st[0]));
			TtfFont.icon(Icons.of(m.id), x + 8, y + 8, 12, Render2D.lerp(0xFF6F7787, accent(), st[1]));
			String name = m.name();
			while (TtfFont.BOLD.width(name, 7.5F) > w - 14 && name.length() > 2) {
				name = name.substring(0, name.length() - 2) + "…";
			}
			TtfFont.BOLD.draw(name, x + 8, y + CARD_H - 22, 7.5F, GameTheme.text);
			String status = m.enabled() ? Lang.t("Açık", "On") : Lang.t("Kapalı", "Off");
			if (m.id.equals("blockoutline")) {
				status += " · " + BlockOutline.colorName() + Lang.t(" (sağ tık)", " (right-click)");
			}
			TtfFont.REGULAR.draw(status, x + 8, y + CARD_H - 11, 5.8F, GameTheme.muted);
		}
		Scissor.pop();
		if (max > 0) {
			float total = max + height;
			float barH = Math.max(16, height * height / total);
			float barY = top + (height - barH) * (this.scroll / max);
			Render2D.roundedRect(this.cx + this.cw + 4, barY, 3, barH, 1.5F, 0x40FFFFFF);
		}
	}

	// ------------------------------------------------------------------ input

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
		float mx = mouseX / this.scale;
		float my = mouseY / this.scale;
		float sw = Math.min(124, this.cw * 0.29F);
		float sx = this.cx + this.cw - sw;
		this.searchFocused = in(mx, my, sx, this.py + 19, sw, 18);
		if (button == 1) {
			this.rightClickCard(mx, my);
			return;
		}
		if (button != 0) {
			return;
		}
		if (in(mx, my, this.px + PANEL_W - 19, this.py + 6, 13, 13)) {
			this.mc.displayGuiScreen(null);
			return;
		}
		for (int i = 0; i < 4; i++) {
			if (in(mx, my, this.px + 7, this.py + 34 + i * 24, 20, 20)) {
				if (i == 1) {
					this.mc.displayGuiScreen(new HudEditorScreen(this));
				} else if (i == 2) {
					this.mc.displayGuiScreen(new GuiControls(this, this.mc.gameSettings));
				} else if (i == 3) {
					this.mc.displayGuiScreen(new GuiOptions(this, this.mc.gameSettings));
				}
				return;
			}
		}
		float top = this.gridTop();
		if (my < top || my >= top + this.gridHeight()) {
			return;
		}
		List<Module> list = this.cards();
		float w = this.cardW();
		for (int i = 0; i < list.size(); i++) {
			float x = this.cx + (i % COLS) * (w + GAP);
			float y = top + (i / COLS) * (CARD_H + GAP) - this.scroll;
			if (in(mx, my, x, y, w, CARD_H)) {
				Module m = list.get(i);
				m.setEnabled(!m.enabled());
				return;
			}
		}
	}

	private void rightClickCard(float mx, float my) {
		float top = this.gridTop();
		if (my < top || my >= top + this.gridHeight()) {
			return;
		}
		List<Module> list = this.cards();
		float w = this.cardW();
		for (int i = 0; i < list.size(); i++) {
			float x = this.cx + (i % COLS) * (w + GAP);
			float y = top + (i / COLS) * (CARD_H + GAP) - this.scroll;
			if (in(mx, my, x, y, w, CARD_H) && list.get(i).id.equals("blockoutline")) {
				BlockOutline.cycle();
				return;
			}
		}
	}

	@Override
	public void handleMouseInput() throws IOException {
		super.handleMouseInput();
		int wheel = Mouse.getEventDWheel();
		if (wheel != 0) {
			this.scrollTarget -= Math.signum(wheel) * 30;
		}
	}

	@Override
	protected void keyTyped(char typedChar, int keyCode) throws IOException {
		if (keyCode == Keyboard.KEY_ESCAPE) {
			if (this.searchFocused) {
				this.searchFocused = false;
				return;
			}
			this.mc.displayGuiScreen(null);
			return;
		}
		if (!this.searchFocused) {
			// Typing anywhere searches, like on 26.x
			if (Character.isLetterOrDigit(typedChar)) {
				this.searchFocused = true;
			} else {
				return;
			}
		}
		if (keyCode == Keyboard.KEY_BACK) {
			if (!query.isEmpty()) {
				query = query.substring(0, query.length() - 1);
			}
		} else if (typedChar >= ' ' && query.length() < 32) {
			query += typedChar;
		}
		this.scrollTarget = 0;
	}

	static boolean in(float mx, float my, float x, float y, float w, float h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}
}
