package com.dnz.client189;

import java.io.IOException;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiControls;
import net.minecraft.client.gui.GuiOptions;

/**
 * What Right Shift opens, the same as on 26.x: the game stays visible behind a light blur, with "DNZ CLIENT",
 * a big orange MODS button (the DNZ menu) and small buttons under it.
 */
public class DnzShiftScreen extends GuiScreen {
	private final long openedAt = System.currentTimeMillis();
	private float hoverBig;
	private final float[] hoverSmall = new float[3];

	@Override
	public void initGui() {
		Blur.on();
		GameTheme.refresh();
	}

	@Override
	public void onGuiClosed() {
		Blur.off(this.mc);
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		if (this.mc.theWorld == null) {
			this.drawDefaultBackground();
		}
		float fade = Math.min(1.0F, (System.currentTimeMillis() - this.openedAt) / 180.0F);
		int cx = this.width / 2;
		int cy = this.height / 2;
		TtfFont.BOLD.drawSpaced("DNZ CLIENT", cx, cy - 36, 12, 1, Render2D.lerp(0x0DFFFFFF, 0xFFFFFFFF, fade));

		// Orange MODS button
		int bx = cx - 65;
		int by = cy - 10;
		boolean overBig = DnzMenuScreen.in(mouseX, mouseY, bx, by, 130, 32);
		this.hoverBig = Anim.approach(this.hoverBig, overBig ? 1 : 0);
		Render2D.shadow(bx, by + 4, 130, 32, 8, 10, 0x59000000 | (GameTheme.accent() & 0xFFFFFF));
		Render2D.roundedRect(bx, by, 130, 32, 8, Render2D.lerp(GameTheme.accent(), Render2D.lerp(GameTheme.accent(), 0xFFFFFFFF, 0.25F), this.hoverBig));
		float textW = TtfFont.BOLD.width("MODS", 11);
		float total = 11 + 5 + textW;
		float sx = bx + (130 - total) / 2;
		TtfFont.icon(Icons.MODS, sx, by + 10.5F, 11, 0xFF0A0D12);
		TtfFont.BOLD.draw("MODS", sx + 16, by + 10.5F, 11, 0xFF0A0D12);

		// small buttons
		String[] labels = {Lang.t("HUD Düzenle", "Edit HUD"), Lang.t("Tuşlar", "Keybinds"), Lang.t("Ayarlar", "Settings")};
		char[] icons = {Icons.EDIT_HUD, Icons.KEYBINDS, Icons.PREFERENCES};
		int w = 60;
		int gap = 5;
		int x = cx - (3 * w + 2 * gap) / 2;
		int y = cy + 30;
		for (int i = 0; i < 3; i++) {
			int x0 = x + i * (w + gap);
			this.hoverSmall[i] = Anim.approach(this.hoverSmall[i], DnzMenuScreen.in(mouseX, mouseY, x0, y, w, 26) ? 1 : 0);
			float h = this.hoverSmall[i];
			Render2D.roundedRect(x0 - 0.5F, y - 0.5F, w + 1, 27, 7.5F, Render2D.lerp(0xFF1B212C, 0x66FF8A3D, h));
			Render2D.roundedRect(x0, y, w, 26, 7, Render2D.lerp(0xD10A0D12, 0xE6141A24, h));
			int color = Render2D.lerp(0xFFC3C9D6, 0xFFFFFFFF, h);
			TtfFont.icon(icons[i], x0 + (w - 8) / 2.0F, y + 4, 8, color);
			TtfFont.MEDIUM.drawCentered(labels[i], x0 + w / 2.0F, y + 15, 5.6F, color);
		}
		super.drawScreen(mouseX, mouseY, partialTicks);
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
		super.mouseClicked(mouseX, mouseY, button);
		if (button != 0) {
			return;
		}
		int cx = this.width / 2;
		int cy = this.height / 2;
		if (DnzMenuScreen.in(mouseX, mouseY, cx - 65, cy - 10, 130, 32)) {
			this.mc.displayGuiScreen(new DnzMenuScreen(this));
			return;
		}
		int w = 60;
		int x = cx - (3 * w + 10) / 2;
		int y = cy + 30;
		for (int i = 0; i < 3; i++) {
			if (DnzMenuScreen.in(mouseX, mouseY, x + i * (w + 5), y, w, 26)) {
				if (i == 0) {
					this.mc.displayGuiScreen(new HudEditorScreen(this));
				} else if (i == 1) {
					this.mc.displayGuiScreen(new GuiControls(this, this.mc.gameSettings));
				} else {
					this.mc.displayGuiScreen(new GuiOptions(this, this.mc.gameSettings));
				}
				return;
			}
		}
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}
}
