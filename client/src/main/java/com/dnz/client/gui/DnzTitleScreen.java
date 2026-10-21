package com.dnz.client.gui;

import com.dnz.client.Notifications;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * DNZ title screen: the DNZ logo (app icon, "DNZ", "CLIENT") over the title picture, Singleplayer /
 * Multiplayer / Quit as dark rounded buttons, the player's head and name with round icon buttons at the top
 * right (DNZ, mods, friends, screenshots, settings) and a notifications card at the bottom right.
 */
public class DnzTitleScreen extends Screen {
	private static final Identifier PICTURE = Identifier.fromNamespaceAndPath("dnzclient", "textures/gui/title_background.png");
	private static final Identifier APP_ICON = Identifier.fromNamespaceAndPath("dnzclient", "textures/gui/app_icon.png");
	private static final FontDescription TITLE_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("dnzclient", "title"));
	static final int GREEN = 0xFF3EDBA3;
	private static final int FILL = 0xC4171B24;
	private static final int FILL_HOVER = 0xDC1F2531;
	private static final int TEXT = 0xFFE9ECF2;
	private static final int MUTED = 0xFF8D95A5;

	private Supplier<PlayerSkin> skin;

	public DnzTitleScreen() {
		super(Component.translatable("narrator.screen.title"));
	}

	private float u() {
		return this.height / 720.0F;
	}

	@Override
	protected void init() {
		if (this.skin == null) {
			this.skin = this.minecraft.getSkinManager().createLookup(this.minecraft.getGameProfile(), true);
		}
		float u = this.u();
		int bw = Math.round(360 * u), bh = Math.round(55 * u), gap = Math.round(15 * u);
		int x = (this.width - bw) / 2;
		int y = Math.round(270 * u);
		this.addRenderableWidget(new MenuButton(x, y, bw, bh, "Singleplayer", true, () -> this.minecraft.gui.setScreen(new SelectWorldScreen(this))));
		this.addRenderableWidget(new MenuButton(x, y + bh + gap, bw, bh, "Multiplayer", true, () -> this.minecraft.gui.setScreen(new JoinMultiplayerScreen(this))));
		this.addRenderableWidget(new MenuButton(x, y + 2 * (bh + gap), bw, bh, "Quit", false, this.minecraft::stop));

		// top right, from the right edge: settings, screenshots, friends, mods, DNZ.
		int sq = Math.round(42 * u), sgap = Math.round(10 * u), top = Math.round(26 * u);
		int ix = this.width - Math.round(25 * u) - sq;
		this.addRenderableWidget(new IconButton(ix, top, sq, Icon.PREFERENCES, "Settings", () -> this.minecraft.gui.setScreen(com.dnz.client.compat.Compat.optionsScreen(this))));
		ix -= sq + sgap;
		this.addRenderableWidget(new IconButton(ix, top, sq, '', "Screenshots", () -> openFolder(this.minecraft.gameDirectory.toPath().resolve("screenshots"))));
		ix -= sq + sgap;
		this.addRenderableWidget(new IconButton(ix, top, sq, '', "Friends", () -> this.minecraft.gui.setScreen(DnzMenuScreen.Tab.FRIENDS.open(this))));
		ix -= sq + sgap;
		this.addRenderableWidget(new IconButton(ix, top, sq, Icon.EDIT_HUD, "Mods", () -> this.minecraft.gui.setScreen(DnzMenuScreen.Tab.HUD.open(this))));
		ix -= sq + sgap;
		this.addRenderableWidget(new IconButton(ix, top, sq, (char) 0, "DNZ", () -> this.minecraft.gui.setScreen(new DnzShiftScreen())));
		ix -= sgap;
		int chipW = Math.round(143 * u);
		this.addRenderableWidget(new AccountChip(ix - chipW, top, chipW, sq));
	}

	private static void openFolder(java.nio.file.Path dir) {
		try {
			java.nio.file.Files.createDirectories(dir);
			String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
			String[] cmd = os.contains("win") ? new String[] {"explorer", dir.toString()}
				: os.contains("mac") ? new String[] {"open", dir.toString()} : new String[] {"xdg-open", dir.toString()};
			new ProcessBuilder(cmd).start();
		} catch (Exception ignored) {
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		// the title picture covering the screen without stretching, a little darker so the buttons stand out
		float scale = Math.max(this.width / 1920.0F, this.height / 1080.0F);
		int w = (int) Math.ceil(1920 * scale), h = (int) Math.ceil(1080 * scale);
		int x = (this.width - w) / 2, y = (this.height - h) / 2;
		SmoothBlit.blit(g, PICTURE, x, y, x + w, y + h);
		g.fill(0, 0, this.width, this.height, 0x38000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		float u = this.u();
		this.logo(g, u);
		this.notifications(g, u);
		Component version = Ui.regular("DNZ Client · Minecraft " + SharedConstants.getCurrentVersion().name());
		Ui.text(g, version, 10 * u, this.height - 18 * u, 0.62F, 0x88FFFFFF);
	}

	private void logo(GuiGraphicsExtractor g, float u) {
		float icon = 86 * u;
		Component dnz = Component.literal("DNZ").withStyle(s -> s.withFont(TITLE_FONT));
		float ts = 40 * u / 9.0F;
		float textW = Math.max(Ui.width(dnz, ts), Ui.width(Ui.medium("C L I E N T"), 12.5F * u / 9.0F));
		float total = icon + 16 * u + textW;
		float x = (this.width - 360 * u) / 2;
		x = Math.min(x, (this.width - total) / 2);
		float y = 141 * u;
		Smooth.rect(g, x - 1, y - 1, icon + 2, icon + 2, 21 * u, 0x40000000);
		SmoothBlit.blit(g, APP_ICON, Math.round(x), Math.round(y), Math.round(x + icon), Math.round(y + icon));
		float tx = x + icon + 16 * u;
		Ui.text(g, dnz, tx, y + 11 * u, ts, 0xFFF5F7FA);
		Ui.text(g, Ui.medium("C L I E N T"), tx + 1 * u, y + 62 * u, 12.5F * u / 9.0F, MUTED);
	}

	private void notifications(GuiGraphicsExtractor g, float u) {
		float w = 318 * u, x = this.width - 25 * u - w;
		List<Notifications.Entry> list = Notifications.latest(3);
		float row = 22 * u;
		float h = Math.max(88 * u, 26 * u + list.size() * row + 10 * u);
		float y = this.height - 25 * u - h;
		Smooth.rect(g, x, y, w, h, 9 * u, 0xD8161A23);
		float s = 13 * u / 9.0F;
		if (list.isEmpty()) {
			Ui.text(g, Ui.regular("You will see your notifications here."), x + 18 * u, y + h - 26 * u, s, MUTED);
			return;
		}
		Ui.text(g, Ui.semibold("Notifications"), x + 18 * u, y + 13 * u, s, TEXT);
		float ry = y + 34 * u;
		for (Notifications.Entry e : list) {
			Smooth.rect(g, x + 18 * u, ry + 4 * u, 6 * u, 6 * u, 3 * u, GREEN);
			Component line = Ui.fit(e.title() + " " + e.text(), Theme.UI_REGULAR, w - 50 * u, s);
			Ui.text(g, line, x + 32 * u, ry, s, TEXT);
			ry += row;
		}
	}

	// ------------------------------------------------------------------ buttons

	private abstract static class Base extends AbstractButton {
		private final Runnable action;
		float hover;

		Base(int x, int y, int w, int h, String label, Runnable action) {
			super(x, y, w, h, Component.literal(label));
			this.action = action;
		}

		@Override
		public void onPress(InputWithModifiers input) {
			if (this.action != null) {
				this.action.run();
			}
		}

		float animate() {
			return this.hover = Anim.approach(this.hover, this.isHoveredOrFocused() ? 1.0F : 0.0F, 14.0F);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			this.defaultButtonNarrationText(output);
		}
	}

	private static final class MenuButton extends Base {
		private final boolean dot;

		MenuButton(int x, int y, int w, int h, String label, boolean dot, Runnable action) {
			super(x, y, w, h, label, action);
			this.dot = dot;
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
			float hv = this.animate();
			int x = this.getX(), y = this.getY(), w = this.width, h = this.height;
			float u = h / 55.0F;
			Smooth.rect(g, x, y, w, h, 8 * u, Theme.lerp(FILL, FILL_HOVER, hv));
			if (hv > 0.01F) {
				Smooth.rect(g, x, y + h - 2 * u, w * hv, 2 * u, u, Theme.withAlpha(GREEN, 0.9F * hv));
			}
			float s = 17 * u / 9.0F;
			Component text = Ui.semibold(this.getMessage().getString());
			float ty = y + (h - 9 * s) / 2 + 0.5F;
			if (this.dot) {
				Smooth.rect(g, x + 20 * u, y + h / 2.0F - 3.5F * u, 7 * u, 7 * u, 3.5F * u, GREEN);
				Ui.text(g, text, x + 41 * u + 3 * u * hv, ty, s, TEXT);
			} else {
				Ui.text(g, text, x + 18 * u + 3 * u * hv, ty, s, Theme.lerp(GREEN, 0xFF7DF0C6, hv));
			}
		}
	}

	/** Square icon button at the top right. [icon] 0 = the DNZ "D". */
	private static final class IconButton extends Base {
		private final char icon;

		IconButton(int x, int y, int size, char icon, String label, Runnable action) {
			super(x, y, size, size, label, action);
			this.icon = icon;
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
			float hv = this.animate();
			int x = this.getX(), y = this.getY(), w = this.width;
			float u = w / 42.0F;
			Smooth.rect(g, x, y, w, w, 8 * u, Theme.lerp(0xE0141820, 0xF01C222D, hv));
			int color = Theme.lerp(0xFFD7DCE5, 0xFFFFFFFF, hv);
			if (this.icon == 0) {
				float s = 17 * u / 9.0F;
				Component d = Ui.semibold("D");
				Ui.text(g, d, x + (w - Ui.width(d, s)) / 2.0F, y + (w - 9 * s) / 2 + 0.5F, s, Theme.lerp(GREEN, 0xFF7DF0C6, hv));
			} else {
				float size = 20 * u;
				Icon.draw(g, this.icon, x + (w - size) / 2, y + (w - size) / 2, size, color);
			}
			if (hv > 0.01F) {
				// name under the button while the mouse is on it
				float s = 0.6F;
				Component name = Ui.medium(this.getMessage().getString());
				Ui.text(g, name, x + (w - Ui.width(name, s)) / 2.0F, y + w + 4 * u, s, Theme.withAlpha(0xFFFFFF, hv));
			}
		}
	}

	private final class AccountChip extends Base {
		AccountChip(int x, int y, int w, int h) {
			super(x, y, w, h, Minecraft.getInstance().getUser().getName(), null);
			this.active = false;
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
			int x = this.getX(), y = this.getY(), w = this.width, h = this.height;
			float u = h / 42.0F;
			Smooth.rect(g, x, y, w, h, 8 * u, 0xE0141820);
			int head = Math.round(26 * u);
			int hx = Math.round(x + 9 * u), hy = y + (h - head) / 2;
			if (DnzTitleScreen.this.skin != null) {
				PlayerFaceExtractor.extractRenderState(g, DnzTitleScreen.this.skin.get(), hx, hy, head);
			}
			float s = 14 * u / 9.0F;
			Component name = Ui.fit(this.getMessage().getString(), Theme.UI_SEMIBOLD, w - head - 26 * u, s);
			Ui.text(g, name, hx + head + 9 * u, y + (h - 9 * s) / 2 + 0.5F, s, TEXT);
		}
	}
}
