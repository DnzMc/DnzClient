package com.dnz.client.gui;

import com.dnz.client.L;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import com.dnz.client.compat.Compat;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Modern replacement for the vanilla ESC menu. */
public class DnzPauseScreen extends Screen {
	private static final int LEFT_W = 190;
	private static final int RIGHT_W = 150;
	private static final int GAP = 12;
	private static final int BTN_H = 20;
	private static final int BTN_STEP = 24;

	private int panelTop;
	private int panelHeight;
	private int leftX;
	private int rightX;
	private long openedAt;

	public DnzPauseScreen() {
		super(Component.translatable("menu.game"));
	}

	@Override
	protected void init() {
		this.openedAt = System.currentTimeMillis();
		if (Theme.simpleStyle()) {
			this.initSimple();
			return;
		}
		int buttons = this.minecraft.level != null ? 5 : 4;
		this.panelHeight = 46 + buttons * BTN_STEP + 12;
		this.panelTop = Math.max(8, (this.height - this.panelHeight) / 2);
		int total = LEFT_W + GAP + RIGHT_W;
		this.leftX = this.width / 2 - total / 2;
		this.rightX = this.leftX + LEFT_W + GAP;

		int bx = this.leftX + 10;
		int bw = LEFT_W - 20;
		int y = this.panelTop + 46;

		this.addRenderableWidget(new DnzButton(bx, y, bw, BTN_H, Component.literal(L.t("pause.back")), () -> {
			this.minecraft.gui.setScreen(null);
			this.minecraft.mouseHandler.grabMouse();
		}));
		y += BTN_STEP;

		int optHalf = (bw - 4) / 2;
		this.addRenderableWidget(new DnzButton(bx, y, optHalf, BTN_H, Component.literal(L.t("pause.options")),
			() -> this.minecraft.gui.setScreen(Compat.optionsScreen(this))));
		this.addRenderableWidget(new DnzButton(bx + optHalf + 4, y, bw - optHalf - 4, BTN_H, Component.literal(L.t("pause.dnz_menu")),
			() -> this.minecraft.gui.setScreen(DnzMenuScreen.open(this))).selected(true));
		y += BTN_STEP;

		if (this.minecraft.level != null && this.minecraft.player != null) {
			int half = (bw - 4) / 2;
			this.addRenderableWidget(new DnzButton(bx, y, half, BTN_H, Component.literal(L.t("pause.advancements")),
				() -> this.minecraft.gui.setScreen(new AdvancementsScreen(this.minecraft.player.connection.getAdvancements(), this))));
			this.addRenderableWidget(new DnzButton(bx + half + 4, y, bw - half - 4, BTN_H, Component.literal(L.t("pause.stats")),
				() -> this.minecraft.gui.setScreen(new StatsScreen(this, this.minecraft.player.getStats()))));
			y += BTN_STEP;
		}

		int third = (bw - 8) / 3;
		this.addRenderableWidget(new DnzButton(bx, y, third, BTN_H, Component.literal("Skin"),
			() -> this.minecraft.gui.setScreen(new DnzSkinScreen(this))));
		this.addRenderableWidget(new DnzButton(bx + third + 4, y, third, BTN_H, Component.literal(L.t("pause.mods")),
			() -> this.minecraft.gui.setScreen(new DnzModsScreen(this))));
		this.addRenderableWidget(new DnzButton(bx + 2 * (third + 4), y, bw - 2 * (third + 4), BTN_H, Component.literal("HUD"),
			() -> this.minecraft.gui.setScreen(new DnzHudTab(this))));
		y += BTN_STEP;

		if (this.minecraft.level != null) {
			y += 6;
		}

		this.addRenderableWidget(new DnzButton(bx, y, bw, BTN_H, CommonComponents.disconnectButtonLabel(this.minecraft.isLocalServer()), () -> {
			this.minecraft.getReportingContext()
				.draftReportHandled(this.minecraft, this, () -> this.minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE), true);
		}));

		this.addRenderableWidget(new DnzButton(this.rightX + 10, this.panelTop + this.panelHeight - 30, RIGHT_W - 20, BTN_H,
			Component.literal(L.t("pause.edit_skin")), () -> this.minecraft.gui.setScreen(new DnzSkinScreen(this))));
	}

	/**
	 * Simple style (clean, glass): big spaced "DNZ CLIENT" title, Client Settings (DNZ menu) and an accent
	 * Disconnect button. ESC goes back to the game.
	 */
	private void initSimple() {
		int bw = Math.min(200, this.width - 40);
		int x = this.width / 2 - bw / 2;
		int y = this.height / 2 - 4;
		// Top: Client Settings (DNZ menu). Bottom: Disconnect (accent color).
		this.addRenderableWidget(new GlassButton(x, y, bw, 20, Component.literal("Client Settings"), false,
			() -> this.minecraft.gui.setScreen(DnzMenuScreen.open(this))));
		this.addRenderableWidget(new GlassButton(x, y + 24, bw, 20, Component.literal("Disconnect"), true, () ->
			this.minecraft.getReportingContext()
				.draftReportHandled(this.minecraft, this, () -> this.minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE), true)));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		if (Theme.javaStyle()) {
			super.extractBackground(g, mouseX, mouseY, a);
			return;
		}
		this.extractBlurredBackground(g);
		if (Theme.simpleStyle()) {
			g.fill(0, 0, this.width, this.height, 0x22000000); // Simple: just blur, barely darkened
			return;
		}
		Theme.screenBackground(g, this.width, this.height);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		float fade = Math.min(1.0F, (System.currentTimeMillis() - this.openedAt) / 200.0F);
		if (Theme.simpleStyle()) {
			// Bold, tight title.
			Theme.spacedText(g, this.font, "DNZ CLIENT", this.width / 2, this.height / 2 - 30, 1.8F, 1, Theme.withAlpha(0xFFFFFF, Math.max(0.1F, fade)), 0.0F);
			super.extractRenderState(g, mouseX, mouseY, a);
			return;
		}

		Theme.panel(g, this.leftX, this.panelTop, LEFT_W, this.panelHeight);
		if (!Theme.javaStyle()) {
			// Java style: the character box has no border or background.
			Theme.panel(g, this.rightX, this.panelTop, RIGHT_W, this.panelHeight);
		}

		// Header
		// Smooth brand title (bold font), no underline: cleaner, like the rest of the DNZ menu.
		Theme.scaledCentered(g, this.font, Theme.smooth(Theme.brand()).withStyle(s -> s.withFont(Theme.BOLD_FONT)), this.leftX + LEFT_W / 2, this.panelTop + 12, 1.6F, Theme.withAlpha(0xFFFFFF, fade));

		// Player preview
		if (this.minecraft.player != null) {
			g.centeredText(this.font, Theme.smooth(this.minecraft.getUser().getName()), this.rightX + RIGHT_W / 2, this.panelTop + 10, 0xFFFFFFFF);
			int top = this.panelTop + 24;
			int bottom = this.panelTop + this.panelHeight - 36;
			int size = Math.max(20, Math.min(60, (bottom - top) / 2 - 4));
			InventoryScreen.extractEntityInInventoryFollowsMouse(g, this.rightX + 10, top, this.rightX + RIGHT_W - 10, bottom,
				size, 0.0625F, mouseX, mouseY, this.minecraft.player);
		}

		super.extractRenderState(g, mouseX, mouseY, a);

		g.centeredText(this.font, Theme.smooth(Theme.poweredBy()), this.width / 2, this.height - 14, 0xFFFFFFFF);
	}
}
