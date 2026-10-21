package com.dnz.client189;

import java.io.IOException;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;

/** Drag HUD modules where you want them; the mouse wheel over one changes its size. */
public class HudEditorScreen extends GuiScreen {
	private final GuiScreen parent;
	private Module dragging;
	private float grabX;
	private float grabY;

	public HudEditorScreen(GuiScreen parent) {
		this.parent = parent;
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		if (this.mc.theWorld == null) {
			this.drawDefaultBackground();
		}
		drawRect(0, 0, this.width, this.height, 0x55000000);
		String hint = Lang.t("Modülleri sürükle · Tekerlek: boyut · Esc: geri", "Drag modules · Wheel: size · Esc: back");
		TtfFont.MEDIUM.drawCentered(hint, this.width / 2F, 8, 8, 0xFFC3C9D6);
		Hud.render(this.mc, true, mouseX, mouseY);
		int wheel = org.lwjgl.input.Mouse.getDWheel();
		if (wheel != 0) {
			Module m = Hud.at(this.mc, mouseX, mouseY);
			if (m != null) {
				Config.ModuleState s = m.state();
				s.scale = Math.max(0.5F, Math.min(2.5F, s.scale + (wheel > 0 ? 0.1F : -0.1F)));
				Config.get().save();
			}
		}
		super.drawScreen(mouseX, mouseY, partialTicks);
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
		super.mouseClicked(mouseX, mouseY, button);
		Module m = Hud.at(this.mc, mouseX, mouseY);
		if (m != null && button == 0) {
			ScaledResolution sr = new ScaledResolution(this.mc);
			this.dragging = m;
			this.grabX = mouseX - m.state().x * sr.getScaledWidth();
			this.grabY = mouseY - m.state().y * sr.getScaledHeight();
		}
	}

	@Override
	protected void mouseClickMove(int mouseX, int mouseY, int button, long time) {
		if (this.dragging != null) {
			ScaledResolution sr = new ScaledResolution(this.mc);
			int[] size = Hud.size(this.dragging);
			float x = Math.max(0, Math.min(sr.getScaledWidth() - size[0], mouseX - this.grabX));
			float y = Math.max(0, Math.min(sr.getScaledHeight() - size[1], mouseY - this.grabY));
			this.dragging.state().x = x / sr.getScaledWidth();
			this.dragging.state().y = y / sr.getScaledHeight();
		}
	}

	@Override
	protected void mouseReleased(int mouseX, int mouseY, int state) {
		if (this.dragging != null) {
			this.dragging = null;
			Config.get().save();
		}
	}

	@Override
	protected void keyTyped(char typedChar, int keyCode) throws IOException {
		if (keyCode == 1) {
			this.mc.displayGuiScreen(this.parent);
		}
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}
}
