package com.dnz.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * What Right Shift opens: the game stays visible behind a light blur, with "DNZ CLIENT", a big orange MODS button
 * (the DNZ menu) and small Profiles, Themes and Settings buttons under it.
 */
public class DnzShiftScreen extends Screen {
	static final int ORANGE = 0xFFFF8A3D;
	private long openedAt;

	public DnzShiftScreen() {
		super(Component.literal("DNZ Client"));
	}

	@Override
	protected void init() {
		this.openedAt = System.currentTimeMillis();
		int cx = this.width / 2;
		int cy = this.height / 2;
		this.addRenderableWidget(new Big(cx - 65, cy - 10, 130, 32, () -> this.open(DnzMenuScreen.Tab.HUD)));
		int w = 60, gap = 5, x = cx - (3 * w + 2 * gap) / 2, y = cy + 30;
		this.addRenderableWidget(new Small(x, y, w, 26, Icon.PROFILES, "Profiles", () -> this.open(DnzMenuScreen.Tab.SKIN)));
		this.addRenderableWidget(new Small(x + w + gap, y, w, 26, Icon.THEMES, "Themes", () -> this.open(DnzMenuScreen.Tab.VISUAL)));
		this.addRenderableWidget(new Small(x + 2 * (w + gap), y, w, 26, Icon.PREFERENCES, "Settings", () -> this.open(DnzMenuScreen.Tab.PERFORMANCE)));
	}

	private void open(DnzMenuScreen.Tab tab) {
		this.minecraft.gui.setScreen(tab.open(this));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		// The game behind, just blurred a little (no dark layer); on the title screen the normal background.
		if (this.minecraft.level == null) {
			super.extractBackground(g, mouseX, mouseY, a);
			return;
		}
		this.extractBlurredBackground(g);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		float fade = Math.min(1.0F, (System.currentTimeMillis() - this.openedAt) / 180.0F);
		Theme.spacedText(g, this.font, "DNZ CLIENT", this.width / 2, this.height / 2 - 34, 1.5F, 1, Theme.withAlpha(0xFFFFFF, Math.max(0.05F, fade)), 0.0F);
	}

	/** The orange MODS button. */
	private static final class Big extends AbstractButton {
		private final Runnable action;
		private float hover;

		Big(int x, int y, int w, int h, Runnable action) {
			super(x, y, w, h, Component.literal("MODS"));
			this.action = action;
		}

		@Override
		public void onPress(InputWithModifiers input) {
			this.action.run();
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
			this.hover = Anim.approach(this.hover, this.isHoveredOrFocused() ? 1.0F : 0.0F, 14.0F);
			int x = this.getX(), y = this.getY(), w = this.width, h = this.height;
			Smooth.shadow(g, x, y + 4, w, h, 8, 10, Theme.withAlpha(ORANGE, 0.35F));
			Smooth.rect(g, x, y, w, h, 8, Theme.lerp(ORANGE, 0xFFFFA266, this.hover));
			int textW = this.minecraft().font.width(DnzMenuScreen.bold("MODS"));
			float s = 1.35F, iconSize = 11;
			float total = iconSize + 5 + textW * s;
			float sx = x + (w - total) / 2;
			Icon.draw(g, Icon.MODS, sx, y + (h - iconSize) / 2.0F, iconSize, 0xFF0A0D12);
			g.pose().pushMatrix();
			g.pose().translate(sx + iconSize + 5, y + (h - 8 * s) / 2.0F + 0.5F);
			g.pose().scale(s, s);
			g.text(this.minecraft().font, DnzMenuScreen.bold("MODS"), 0, 0, 0xFF0A0D12, false);
			g.pose().popMatrix();
		}

		private net.minecraft.client.Minecraft minecraft() {
			return net.minecraft.client.Minecraft.getInstance();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			this.defaultButtonNarrationText(output);
		}
	}

	/** A small dark button with an icon above its name. */
	private static final class Small extends AbstractButton {
		private final char icon;
		private final Runnable action;
		private float hover;

		Small(int x, int y, int w, int h, char icon, String label, Runnable action) {
			super(x, y, w, h, Component.literal(label));
			this.icon = icon;
			this.action = action;
		}

		@Override
		public void onPress(InputWithModifiers input) {
			this.action.run();
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
			this.hover = Anim.approach(this.hover, this.isHoveredOrFocused() ? 1.0F : 0.0F, 14.0F);
			int x = this.getX(), y = this.getY(), w = this.width, h = this.height;
			Smooth.rect(g, x - 0.5F, y - 0.5F, w + 1, h + 1, 7.5F, Theme.lerp(0xFF1B212C, 0x66FF8A3D, this.hover));
			Smooth.rect(g, x, y, w, h, 7, Theme.lerp(0xD10A0D12, 0xE6141A24, this.hover));
			int color = Theme.lerp(0xFFC3C9D6, 0xFFFFFFFF, this.hover);
			Icon.draw(g, this.icon, x + (w - 8) / 2.0F, y + 4, 8, color);
			var font = net.minecraft.client.Minecraft.getInstance().font;
			float s = 0.62F;
			g.pose().pushMatrix();
			g.pose().translate(x + w / 2.0F, y + 15);
			g.pose().scale(s, s);
			g.centeredText(font, Ui.medium(this.getMessage().getString()), 0, 0, color);
			g.pose().popMatrix();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			this.defaultButtonNarrationText(output);
		}
	}
}
