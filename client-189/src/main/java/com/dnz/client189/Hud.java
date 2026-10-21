package com.dnz.client189;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;

/** Draws the enabled HUD modules at their saved places. */
public final class Hud {
	private static final Map<String, int[]> SIZES = new HashMap<String, int[]>();

	private Hud() {
	}

	public static void render(Minecraft mc, boolean editor, int mouseX, int mouseY) {
		ScaledResolution sr = new ScaledResolution(mc);
		for (Module m : Modules.ALL) {
			if (!m.hud || !m.enabled()) {
				continue;
			}
			Config.ModuleState s = m.state();
			float x = s.x * sr.getScaledWidth();
			float y = s.y * sr.getScaledHeight();
			GlStateManager.pushMatrix();
			GlStateManager.translate(x, y, 0);
			GlStateManager.scale(s.scale, s.scale, 1);
			int[] size = m.render(mc, editor);
			GlStateManager.popMatrix();
			int w = Math.round(size[0] * s.scale);
			int h = Math.round(size[1] * s.scale);
			SIZES.put(m.id, new int[] {w, h});
			if (editor) {
				boolean hover = DnzMenuScreen.in(mouseX, mouseY, x, y, w, h);
				int c = hover ? Render2D.ORANGE : 0x88FFFFFF;
				net.minecraft.client.gui.Gui.drawRect((int) x - 1, (int) y - 1, (int) x + w + 1, (int) y, c);
				net.minecraft.client.gui.Gui.drawRect((int) x - 1, (int) y + h, (int) x + w + 1, (int) y + h + 1, c);
				net.minecraft.client.gui.Gui.drawRect((int) x - 1, (int) y, (int) x, (int) y + h, c);
				net.minecraft.client.gui.Gui.drawRect((int) x + w, (int) y, (int) x + w + 1, (int) y + h, c);
			}
		}
		GlStateManager.color(1, 1, 1, 1);
	}

	public static int[] size(Module m) {
		int[] s = SIZES.get(m.id);
		return s == null ? new int[] {0, 0} : s;
	}

	public static Module at(Minecraft mc, int mouseX, int mouseY) {
		ScaledResolution sr = new ScaledResolution(mc);
		Module found = null;
		for (Module m : Modules.ALL) {
			if (!m.hud || !m.enabled()) {
				continue;
			}
			int[] size = size(m);
			float x = m.state().x * sr.getScaledWidth();
			float y = m.state().y * sr.getScaledHeight();
			if (DnzMenuScreen.in(mouseX, mouseY, x, y, size[0], size[1])) {
				found = m;
			}
		}
		return found;
	}
}
