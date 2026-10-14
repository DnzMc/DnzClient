package com.dnz.client.gui;

import com.mojang.realmsclient.RealmsMainScreen;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.LevelSummary;

/**
 * Test title screen (only with "testTitle" in the DNZ config): the vanilla title screen's choices as wide dark
 * buttons in the middle (Singleplayer with Continue, Multiplayer, Realms, Settings), a Quickplay list of the last
 * worlds on the left, and small DNZ / Mods / Quit buttons under them.
 */
public class DnzTitleScreen extends Screen {
	private static final Identifier PICTURE = Identifier.fromNamespaceAndPath("dnzclient", "textures/gui/title_background.png");
	private static final int FILL = 0xE61A1E28;
	private static final int FILL_HOVER = 0xF0222836;
	private static final int EDGE = 0x24FFFFFF;
	private static final int TEXT = 0xFFF1F3F7;
	private static final int MUTED = 0xFF9AA2B2;
	private static final int QUIT = 0xFFF4566A;

	/** Recent worlds, newest first (null while they load). Kept while the game runs. */
	private static List<LevelSummary> worlds;
	private static boolean quickplayOpen = true;

	public DnzTitleScreen() {
		super(Component.translatable("narrator.screen.title"));
	}

	private static int accent() {
		return DnzShiftScreen.ORANGE;
	}

	@Override
	protected void init() {
		if (worlds == null) {
			this.loadWorlds();
		}
		float h = Math.max(22, Math.min(36, this.height * 0.07F));
		int bh = Math.round(h);
		int gap = Math.max(4, Math.round(h * 0.22F));
		int colW = Math.round(Math.max(200, Math.min(320, this.width * 0.325F)));
		int x = (this.width - colW) / 2;
		int y = Math.round(this.height * 0.45F);

		LevelSummary last = worlds == null || worlds.isEmpty() ? null : worlds.get(0);
		this.addRenderableWidget(new Wide(x, y, colW, bh, "Singleplayer", Kind.PLAY, false, () -> this.minecraft.gui.setScreen(new SelectWorldScreen(this))));
		if (last != null) {
			// "Continue" on the right of Singleplayer: straight into the last played world.
			int cw = Math.round(colW * 0.26F);
			this.addRenderableWidget(new Continue(x + colW - cw, y, cw, bh, () -> this.open(last)));
		}
		this.addRenderableWidget(new Wide(x, y + (bh + gap), colW, bh, "Multiplayer", Kind.MULTIPLAYER, true, () -> this.minecraft.gui.setScreen(new JoinMultiplayerScreen(this))));
		this.addRenderableWidget(new Wide(x, y + 2 * (bh + gap), colW, bh, "Realms", Kind.REALMS, true, () -> this.minecraft.gui.setScreen(new RealmsMainScreen(this))));
		this.addRenderableWidget(new Wide(x, y + 3 * (bh + gap), colW, bh, "Settings", Kind.SETTINGS, false, () -> this.minecraft.gui.setScreen(com.dnz.client.compat.Compat.optionsScreen(this))));

		int sq = Math.round(bh * 0.86F);
		int sy = y + 4 * (bh + gap) + gap;
		int sx = this.width / 2 - (3 * sq + 2 * gap) / 2;
		this.addRenderableWidget(new Square(sx, sy, sq, "DNZ", Kind.DNZ, () -> this.minecraft.gui.setScreen(new DnzShiftScreen())));
		this.addRenderableWidget(new Square(sx + sq + gap, sy, sq, "Mods", Kind.MODS, () -> this.minecraft.gui.setScreen(DnzMenuScreen.Tab.HUD.open(this))));
		this.addRenderableWidget(new Square(sx + 2 * (sq + gap), sy, sq, "Quit", Kind.QUIT, this.minecraft::stop));

		// Quickplay on the left: a header that folds the list, then the last three worlds.
		int qx = Math.max(8, Math.round(this.width * 0.016F));
		int qw = Math.round(Math.max(150, Math.min(240, this.width * 0.19F)));
		int qh = Math.round(bh * 0.88F);
		this.addRenderableWidget(new Header(qx, y + 2, qw, qh, () -> {
			quickplayOpen = !quickplayOpen;
			this.rebuildWidgets();
		}));
		if (quickplayOpen && worlds != null) {
			int wy = y + 2 + qh + gap;
			for (LevelSummary world : worlds.subList(0, Math.min(3, worlds.size()))) {
				this.addRenderableWidget(new WorldRow(qx, wy, qw, qh, world, () -> this.open(world)));
				wy += qh + gap;
			}
		}
	}

	private void loadWorlds() {
		try {
			var source = this.minecraft.getLevelSource();
			source.loadLevelSummaries(source.findLevelCandidates()).thenAccept(list -> this.minecraft.execute(() -> {
				List<LevelSummary> usable = new ArrayList<>();
				for (LevelSummary s : list) {
					if (!s.isDisabled() && !s.isLocked()) {
						usable.add(s);
					}
				}
				usable.sort(Comparator.comparingLong(LevelSummary::getLastPlayed).reversed());
				worlds = usable;
				if (this.minecraft.gui.screen() == this) {
					this.rebuildWidgets();
				}
			}));
		} catch (Exception e) {
			worlds = List.of();
		}
	}

	private void open(LevelSummary world) {
		this.minecraft.createWorldOpenFlows().openWorld(world.getLevelId(), () -> this.minecraft.gui.setScreen(this));
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		// The DNZ title picture (already soft), covering the screen without stretching, a little darker.
		float scale = Math.max(this.width / 1920.0F, this.height / 1080.0F);
		int w = (int) Math.ceil(1920 * scale), h = (int) Math.ceil(1080 * scale);
		int x = (this.width - w) / 2, y = (this.height - h) / 2;
		SmoothBlit.blit(g, PICTURE, x, y, x + w, y + h);
		g.fill(0, 0, this.width, this.height, 0x33000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		float s = 0.62F;
		int y = this.height - 12;
		Component brand = Ui.semibold("DNZ CLIENT");
		Ui.text(g, brand, 8, y - 0.5F, 0.7F, 0xCCFFFFFF);
		Ui.text(g, Ui.regular("Fabric " + SharedConstants.getCurrentVersion().name()), 8 + Ui.width(brand, 0.7F) + 8, y, s, 0x99FFFFFF);
		Component copy = Ui.regular("Copyright Mojang AB. Do not distribute!");
		Ui.text(g, copy, this.width - 8 - Ui.width(copy, s), y, s, 0x99FFFFFF);
	}

	// ------------------------------------------------------------------ drawing helpers

	private enum Kind { PLAY, MULTIPLAYER, REALMS, SETTINGS, DNZ, MODS, QUIT }

	private static void card(GuiGraphicsExtractor g, float x, float y, float w, float h, float hover, boolean highlight) {
		int edge = highlight ? Theme.withAlpha(accent(), 0.85F) : Theme.lerp(EDGE, Theme.withAlpha(accent(), 0.9F), hover);
		Smooth.rect(g, x - 0.75F, y - 0.75F, w + 1.5F, h + 1.5F, 5.75F, edge);
		Smooth.rect(g, x, y, w, h, 5, Theme.lerp(FILL, FILL_HOVER, hover));
	}

	/** Small "play" triangle, drawn as thin slices so it stays smooth at any size. */
	private static void triangle(GuiGraphicsExtractor g, float x, float y, float size, int color) {
		int slices = 14;
		for (int i = 0; i < slices; i++) {
			float t = (i + 0.5F) / slices;
			float half = size / 2 * (1 - Math.abs(t * 2 - 1));
			Smooth.rect(g, x + t * size * 0.86F, y + size / 2 - half, size * 0.86F / slices + 0.15F, half * 2, 0, color);
		}
	}

	/** A thin line [length] long through (cx, cy), turned by [degrees]. */
	private static void stroke(GuiGraphicsExtractor g, float cx, float cy, float length, float thick, float degrees, int color) {
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().rotate((float) Math.toRadians(degrees));
		Smooth.rect(g, -length / 2, -thick / 2, length, thick, thick / 2, color);
		g.pose().popMatrix();
	}

	/** "›" or "⌄": two short strokes. */
	private static void chevron(GuiGraphicsExtractor g, float cx, float cy, float size, boolean down, int color) {
		float l = size * 0.62F, t = Math.max(1.1F, size * 0.16F), o = size * 0.2F;
		if (down) {
			stroke(g, cx - o, cy, l, t, 45, color);
			stroke(g, cx + o, cy, l, t, -45, color);
		} else {
			stroke(g, cx, cy - o, l, t, 45, color);
			stroke(g, cx, cy + o, l, t, -45, color);
		}
	}

	/** A lightning bolt (Quickplay). */
	private static void bolt(GuiGraphicsExtractor g, float x, float y, float size, int color) {
		float t = Math.max(1.1F, size * 0.15F);
		stroke(g, x + size * 0.55F, y + size * 0.27F, size * 0.62F, t, 115, color);
		stroke(g, x + size * 0.5F, y + size * 0.5F, size * 0.42F, t, 0, color);
		stroke(g, x + size * 0.45F, y + size * 0.73F, size * 0.62F, t, 115, color);
	}

	private static Minecraft mc() {
		return Minecraft.getInstance();
	}

	private abstract static class Base extends AbstractButton {
		private final Runnable action;
		float hover;

		Base(int x, int y, int w, int h, String label, Runnable action) {
			super(x, y, w, h, Component.literal(label));
			this.action = action;
		}

		@Override
		public void onPress(InputWithModifiers input) {
			this.action.run();
		}

		float animate() {
			return this.hover = Anim.approach(this.hover, this.isHoveredOrFocused() ? 1.0F : 0.0F, 14.0F);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			this.defaultButtonNarrationText(output);
		}
	}

	/** Wide button in the middle column: icon, name, and a "›" when it leads to a list. */
	private static final class Wide extends Base {
		private final Kind kind;
		private final boolean arrow;

		Wide(int x, int y, int w, int h, String label, Kind kind, boolean arrow, Runnable action) {
			super(x, y, w, h, label, action);
			this.kind = kind;
			this.arrow = arrow;
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
			float hv = this.animate();
			int x = this.getX(), y = this.getY(), w = this.width, h = this.height;
			card(g, x, y, w, h, hv, this.kind == Kind.PLAY);
			float icon = h * 0.42F;
			float ix = x + h * 0.45F, iy = y + (h - icon) / 2;
			int iconColor = Theme.lerp(0xFFC9CFDA, TEXT, hv);
			switch (this.kind) {
				case PLAY -> triangle(g, ix, iy, icon, accent());
				case MULTIPLAYER -> Icon.draw(g, Icon.PROFILES, ix - 0.5F, iy - 0.5F, icon + 1, iconColor);
				case REALMS -> Icon.draw(g, Icon.SERVER, ix - 0.5F, iy - 0.5F, icon + 1, iconColor);
				default -> Icon.draw(g, Icon.PREFERENCES, ix - 0.5F, iy - 0.5F, icon + 1, iconColor);
			}
			float s = h / 30.0F;
			Ui.text(g, Ui.semibold(this.getMessage().getString()), ix + icon + h * 0.32F, y + (h - 9 * s) / 2 + 0.5F, s, TEXT);
			if (this.arrow) {
				chevron(g, x + w - h * 0.55F, y + h / 2.0F, h * 0.26F, false, Theme.lerp(MUTED, TEXT, hv));
			}
		}
	}

	/** "Continue" on the right of Singleplayer. */
	private static final class Continue extends Base {
		Continue(int x, int y, int w, int h, Runnable action) {
			super(x, y, w, h, "Continue", action);
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
			float hv = this.animate();
			float s = this.height / 44.0F;
			Component text = Ui.medium("Continue");
			float tx = this.getX() + this.width - this.height * 0.45F - Ui.width(text, s);
			float ty = this.getY() + (this.height - 9 * s) / 2 + 0.5F;
			Ui.text(g, text, tx, ty, s, Theme.lerp(accent(), 0xFFFFB27A, hv));
			if (hv > 0.01F) {
				Smooth.rect(g, tx, ty + 9 * s + 0.5F, Ui.width(text, s), 0.8F, 0, Theme.withAlpha(accent(), hv));
			}
		}
	}

	/** Small square button under the column: DNZ, Mods, Quit. */
	private static final class Square extends Base {
		private final Kind kind;

		Square(int x, int y, int size, String label, Kind kind, Runnable action) {
			super(x, y, size, size, label, action);
			this.kind = kind;
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
			float hv = this.animate();
			int x = this.getX(), y = this.getY(), w = this.width;
			if (this.kind == Kind.QUIT) {
				Smooth.rect(g, x, y, w, w, 5, Theme.lerp(QUIT, 0xFFFF7083, hv));
				chevronX(g, x + w / 2.0F, y + w / 2.0F, w * 0.34F, 0xFF2A0B10);
				return;
			}
			card(g, x, y, w, w, hv, false);
			int color = Theme.lerp(0xFFD5DAE3, 0xFFFFFFFF, hv);
			if (this.kind == Kind.DNZ) {
				float s = w / 46.0F;
				Component t = Ui.semibold("DNZ");
				Ui.text(g, t, x + (w - Ui.width(t, s)) / 2.0F, y + (w - 9 * s) / 2 + 0.5F, s, Theme.lerp(accent(), 0xFFFFB27A, hv));
			} else {
				float icon = w * 0.46F;
				Icon.draw(g, Icon.MODS, x + (w - icon) / 2, y + (w - icon) / 2, icon, color);
			}
		}

		private static void chevronX(GuiGraphicsExtractor g, float cx, float cy, float size, int color) {
			float t = Math.max(1.3F, size * 0.2F);
			stroke(g, cx, cy, size * 1.25F, t, 45, color);
			stroke(g, cx, cy, size * 1.25F, t, -45, color);
		}
	}

	/** Quickplay header: bolt, name and a fold arrow. */
	private static final class Header extends Base {
		Header(int x, int y, int w, int h, Runnable action) {
			super(x, y, w, h, "Quickplay", action);
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
			float hv = this.animate();
			int x = this.getX(), y = this.getY(), w = this.width, h = this.height;
			card(g, x, y, w, h, hv, false);
			float icon = h * 0.46F;
			bolt(g, x + h * 0.35F, y + (h - icon) / 2, icon, 0xFF34D399);
			float s = h / 29.0F;
			Ui.text(g, Ui.semibold("Quickplay"), x + h * 0.35F + icon + h * 0.28F, y + (h - 9 * s) / 2 + 0.5F, s, TEXT);
			chevron(g, x + w - h * 0.5F, y + h / 2.0F, h * 0.24F, quickplayOpen, Theme.lerp(MUTED, TEXT, hv));
		}
	}

	/** One recent world: its name and "Singleplayer · Survival". */
	private static final class WorldRow extends Base {
		private final LevelSummary world;

		WorldRow(int x, int y, int w, int h, LevelSummary world, Runnable action) {
			super(x, y, w, h, world.getLevelName(), action);
			this.world = world;
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
			float hv = this.animate();
			int x = this.getX(), y = this.getY(), w = this.width, h = this.height;
			card(g, x, y, w, h, hv, false);
			float pad = h * 0.36F;
			float s = h / 32.0F;
			Component name = Ui.fit(this.world.getLevelName(), Theme.UI_SEMIBOLD, w - pad - h * 0.9F, s);
			Ui.text(g, name, x + pad, y + h * 0.2F, s, TEXT);
			String mode = this.world.isHardcore() ? "Hardcore" : capitalize(this.world.getGameMode().getName());
			Ui.text(g, Ui.regular("Singleplayer · " + mode), x + pad, y + h * 0.56F, s * 0.82F, MUTED);
			chevron(g, x + w - h * 0.45F, y + h / 2.0F, h * 0.24F, false, Theme.lerp(MUTED, TEXT, hv));
		}

		private static String capitalize(String s) {
			return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
		}
	}
}
