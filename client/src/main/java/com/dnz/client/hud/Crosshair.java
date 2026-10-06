package com.dnz.client.hud;

import com.dnz.client.DnzConfig;
import com.dnz.client.gui.Smooth;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Custom crosshair (module "crosshair"): style, size, gap, thickness, color, outline and center dot, in real screen
 * pixels (the same at every GUI scale). Off, in third person or with the F3 screen open, Minecraft's own is drawn.
 */
public final class Crosshair {
	public static final String[] STYLES = {"cross", "crossdot", "dot", "t", "box", "circle"};

	private Crosshair() {
	}

	public static boolean active(Minecraft mc) {
		HudModule m = DnzHud.module("crosshair");
		return m != null && m.enabled() && mc.options.getCameraType().isFirstPerson() && !mc.debugEntries.isOverlayVisible();
	}

	public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		DnzConfig.Crosshair c = DnzConfig.get().crosshair;
		float scale = (float) mc.getWindow().getGuiScale();
		g.pose().pushMatrix();
		// Real pixels from here on, centered on the screen.
		g.pose().translate(g.guiWidth() / 2.0F, g.guiHeight() / 2.0F);
		g.pose().scale(1.0F / scale, 1.0F / scale);
		int color = c.color;
		String style = STYLES[Math.floorMod(c.style, STYLES.length)];
		float t = c.thickness;
		float half = t / 2.0F;
		float gap = c.gap;
		float len = c.size;
		if (style.equals("dot")) {
			dot(g, Math.max(2, t * 1.5F), color, c.outline);
		} else if (style.equals("circle")) {
			// A ring of small dots (smooth at any size).
			float r = gap + len / 2;
			int n = Math.max(12, Math.round(r * 2.5F));
			for (int i = 0; i < n; i++) {
				double a = i * Math.PI * 2 / n;
				float x = (float) Math.cos(a) * r, y = (float) Math.sin(a) * r;
				if (c.outline) {
					Smooth.rect(g, x - t / 2 - 1, y - t / 2 - 1, t + 2, t + 2, t / 2 + 1, 0x60000000);
				}
				Smooth.rect(g, x - t / 2, y - t / 2, t, t, t / 2, color);
			}
		} else if (style.equals("box")) {
			float r = gap + len;
			bar(g, -r, -r, 2 * r, t, color, c.outline);
			bar(g, -r, r - t, 2 * r, t, color, c.outline);
			bar(g, -r, -r, t, 2 * r, color, c.outline);
			bar(g, r - t, -r, t, 2 * r, color, c.outline);
		} else {
			if (!style.equals("t")) {
				bar(g, -half, -gap - len, t, len, color, c.outline); // top
			}
			bar(g, -half, gap, t, len, color, c.outline); // bottom
			bar(g, -gap - len, -half, len, t, color, c.outline); // left
			bar(g, gap, -half, len, t, color, c.outline); // right
			if (style.equals("crossdot") || c.dot) {
				dot(g, Math.max(2, t), color, c.outline);
			}
		}
		g.pose().popMatrix();
	}

	private static void bar(GuiGraphicsExtractor g, float x, float y, float w, float h, int color, boolean outline) {
		if (outline) {
			Smooth.rect(g, x - 1, y - 1, w + 2, h + 2, 0.5F, 0xC0000000);
		}
		Smooth.rect(g, x, y, w, h, 0, color);
	}

	private static void dot(GuiGraphicsExtractor g, float size, int color, boolean outline) {
		if (outline) {
			Smooth.rect(g, -size / 2 - 1, -size / 2 - 1, size + 2, size + 2, size / 2 + 1, 0xC0000000);
		}
		Smooth.rect(g, -size / 2, -size / 2, size, size, size / 2, color);
	}
}
