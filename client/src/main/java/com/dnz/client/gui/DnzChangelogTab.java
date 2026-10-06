package com.dnz.client.gui;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/** DNZ menu "Changelog" page: what changed in the latest DNZ Client versions (newest first, scrolls). */
public class DnzChangelogTab extends DnzMenuScreen {
	private record Release(String version, List<String> notes) {
	}

	private static final List<Release> RELEASES = List.of(
		new Release("1.36.0", List.of(
			"New DNZ menu look: icon rail, orange accent, dark cards (click to switch, gear for settings).",
			"Right Shift opens a quick screen: DNZ CLIENT, a big MODS button, Profiles, Themes and Settings.",
			"Friends: add players by name; they get a green star in the player list and you see when they join or leave.",
			"ESC menu: Minecraft's own menu again, with a DNZ Settings button that opens this menu (Open World is here too).",
			"Buttons and sliders outside the DNZ menu look like Minecraft's own; \"DNZ Client\" at the bottom right of the inventory and ESC menu.",
			"The game window is called DNZ Client, and the title screen no longer shows the mod count.")),
		new Release("1.35.0", List.of(
			"New modules: Minimap, Look At (block or creature you look at), Custom Crosshair (6 styles, size, gap, thickness, color, outline), Hitboxes.",
			"More info modules: Direction, Yaw / Pitch, Dimension, TPS, Frame Time, Entities, Chunks, Date, XP Level, Block, Durability, Item Count, Saturation.")),
		new Release("1.34.0", List.of(
			"New menu look: flat dark panel, plain sidebar, cards with a name bar (blue when on).",
			"Menus are drawn by NanoVG on the graphics card (Minecraft 26.2, OpenGL): smoother and fewer stutters.")),
		new Release("1.33.0", List.of(
			"Buttons other mods add to the ESC menu (capes and more) now show in the DNZ ESC menu, top left.",
			"ESC and DNZ menus in a world draw at most 60 FPS (less heat), and Cool mode in Preferences caps FPS for laptops.",
			"Performance pack: Sciophobia, VMP, ServerCore and ModernFix.")),
		new Release("1.32.0", List.of(
			"Cloud save (optional): key bindings and DNZ settings follow your Minecraft account to other computers.",
			"Turn it on in Preferences or in DNZ Launcher settings; nothing is sent while it is off.",
			"Settings go to the cloud only when the game closes, never while you play (DNZ Launcher shows when it is saving).")),
		new Release("1.31.0", List.of(
			"Open World: open your single player world to friends anywhere (globe card in the Right Shift menu).",
			"Friends join with the address from chat, no port forwarding needed.")),
		new Release("1.30.1", List.of(
			"Title screen: new sunset background (the world and server lists keep theirs).",
			"Sharp \"DNZ CLIENT\" logo and smooth text on every title screen button.",
			"Background pictures stay smooth on big screens.")),
		new Release("1.30.0", List.of(
			"Themes: Modern (smooth text) or Minecraft (pixel font, black and white), each dark or white.",
			"Menu size, blur, chest color, fullbright and streamer mode moved to Preferences.",
			"With the Effects module on, the game's own effect icons are hidden.",
			"Cleaner buttons and cards (no gray edges), no Hypixel category.")),
		new Release("1.29.0", List.of(
			"The menu keeps the same size and shape on every screen and GUI scale.",
			"Text is rendered for your screen's real resolution: sharp after resizing too.",
			"New settings pages: switches, sliders, dropdown lists and text boxes, all scrolling.",
			"Themes and Preferences use the new settings rows; Menu Size option.")),
		new Release("1.26.0", List.of(
			"New menu layout: sidebar sections, back and forward arrows, search.",
			"Mods page with cards, categories and smooth scrolling.",
			"Line icons everywhere in the menu.")),
		new Release("1.24.2", List.of(
			"Blurred picture on the title, singleplayer and multiplayer screens.")),
		new Release("1.24.0", List.of(
			"Smooth rounded menus with soft shadows and animations.",
			"Six new performance mods in the pack.",
			"Fixed a crash on start in Minecraft 26.3.")),
		new Release("1.23.0", List.of(
			"Shulker box preview in the tooltip.",
			"Better server list with ping and player count.")));

	private float scroll;
	private float scrollTarget;

	public DnzChangelogTab(Screen parent) {
		super(Tab.CHANGELOG, parent);
	}

	@Override
	protected void initContent() {
	}

	private int contentHeight() {
		int h = 0;
		for (Release r : RELEASES) {
			h += 16;
			for (String note : r.notes()) {
				h += this.font.split(Theme.smooth(note), this.cw - 24).size() * 10 + 2;
			}
			h += 10;
		}
		return h;
	}

	@Override
	protected boolean scrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		float max = Math.max(0, this.contentHeight() - this.ch);
		this.scrollTarget = Math.max(0, Math.min(max, this.scrollTarget - (float) scrollY * 24));
		return true;
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		this.scroll = Anim.approach(this.scroll, this.scrollTarget, 16.0F);
		int accent = 0xFF000000 | Theme.accentInfo().rgb();
		g.enableScissor(this.cx, this.cy, this.cx + this.cw, this.cy + this.ch);
		float y = this.cy - this.scroll;
		boolean first = true;
		for (Release r : RELEASES) {
			g.text(this.font, bold("v" + r.version()), this.cx + 2, Math.round(y), 0xFFFFFFFF, false);
			if (first) {
				int vx = this.cx + 8 + this.font.width(bold("v" + r.version()));
				Smooth.rect(g, vx, y - 1, 30, 10, 5, accent);
				g.pose().pushMatrix();
				g.pose().translate(vx + 15, y + 1.5F);
				g.pose().scale(0.65F, 0.65F);
				g.centeredText(this.font, bold("LATEST"), 0, 0, Palette.ON_ACCENT);
				g.pose().popMatrix();
				first = false;
			}
			y += 16;
			for (String note : r.notes()) {
				Smooth.rect(g, this.cx + 6, y + 3, 3, 3, 1.5F, accent);
				for (var line : this.font.split(Theme.smooth(note), this.cw - 24)) {
					g.text(this.font, line, this.cx + 16, Math.round(y), 0xFFC9D0DB, false);
					y += 10;
				}
				y += 2;
			}
			y += 10;
		}
		g.disableScissor();
	}
}
