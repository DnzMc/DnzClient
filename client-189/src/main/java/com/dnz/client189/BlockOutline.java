package com.dnz.client189;

import java.awt.Color;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.opengl.GL11;

/**
 * Module "blockoutline" on 1.8.9: the frame around the looked-at block in a color of the player's choice
 * (right-click the card in the DNZ menu to change it; the last one is rainbow), a bit thicker, with a
 * see-through fill.
 */
public final class BlockOutline {
	private static final int[] COLORS = {0xFFFFFF, 0x55FF55, 0x00FFFF, 0xFF5555, 0xFFFF55, 0xFF55FF, 0xFF8A3D, 0x000000, -1};
	private static final String[] NAMES_TR = {"Beyaz", "Yeşil", "Turkuaz", "Kırmızı", "Sarı", "Pembe", "Turuncu", "Siyah", "Gökkuşağı"};
	private static final String[] NAMES_EN = {"White", "Green", "Cyan", "Red", "Yellow", "Pink", "Orange", "Black", "Rainbow"};

	private BlockOutline() {
	}

	public static boolean on() {
		Module m = Modules.get("blockoutline");
		return m != null && m.enabled();
	}

	private static int index() {
		Module m = Modules.get("blockoutline");
		try {
			return Math.floorMod(Integer.parseInt(m.state().value), COLORS.length);
		} catch (Exception e) {
			return 0;
		}
	}

	public static void cycle() {
		Module m = Modules.get("blockoutline");
		m.state().value = Integer.toString((index() + 1) % COLORS.length);
		Config.get().save();
	}

	public static String colorName() {
		return Lang.tr() ? NAMES_TR[index()] : NAMES_EN[index()];
	}

	private static int rgb() {
		int c = COLORS[index()];
		return c >= 0 ? c : Color.HSBtoRGB((System.currentTimeMillis() % 4000L) / 4000.0F, 0.8F, 1.0F) & 0xFFFFFF;
	}

	/** The line color, in place of vanilla's see-through black. */
	public static void lineColor() {
		int c = rgb();
		GlStateManager.color((c >> 16 & 255) / 255.0F, (c >> 8 & 255) / 255.0F, (c & 255) / 255.0F, 0.9F);
	}

	/** Fills [box] see-through, then the caller draws the lines. */
	public static void fill(AxisAlignedBB b) {
		int c = rgb();
		GlStateManager.color((c >> 16 & 255) / 255.0F, (c >> 8 & 255) / 255.0F, (c & 255) / 255.0F, 0.18F);
		Tessellator tess = Tessellator.getInstance();
		WorldRenderer wr = tess.getWorldRenderer();
		wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
		wr.pos(b.minX, b.minY, b.minZ).endVertex();
		wr.pos(b.maxX, b.minY, b.minZ).endVertex();
		wr.pos(b.maxX, b.minY, b.maxZ).endVertex();
		wr.pos(b.minX, b.minY, b.maxZ).endVertex();
		wr.pos(b.minX, b.maxY, b.minZ).endVertex();
		wr.pos(b.minX, b.maxY, b.maxZ).endVertex();
		wr.pos(b.maxX, b.maxY, b.maxZ).endVertex();
		wr.pos(b.maxX, b.maxY, b.minZ).endVertex();
		wr.pos(b.minX, b.minY, b.minZ).endVertex();
		wr.pos(b.minX, b.maxY, b.minZ).endVertex();
		wr.pos(b.maxX, b.maxY, b.minZ).endVertex();
		wr.pos(b.maxX, b.minY, b.minZ).endVertex();
		wr.pos(b.minX, b.minY, b.maxZ).endVertex();
		wr.pos(b.maxX, b.minY, b.maxZ).endVertex();
		wr.pos(b.maxX, b.maxY, b.maxZ).endVertex();
		wr.pos(b.minX, b.maxY, b.maxZ).endVertex();
		wr.pos(b.minX, b.minY, b.minZ).endVertex();
		wr.pos(b.minX, b.minY, b.maxZ).endVertex();
		wr.pos(b.minX, b.maxY, b.maxZ).endVertex();
		wr.pos(b.minX, b.maxY, b.minZ).endVertex();
		wr.pos(b.maxX, b.minY, b.minZ).endVertex();
		wr.pos(b.maxX, b.maxY, b.minZ).endVertex();
		wr.pos(b.maxX, b.maxY, b.maxZ).endVertex();
		wr.pos(b.maxX, b.minY, b.maxZ).endVertex();
		GlStateManager.disableCull();
		tess.draw();
		GlStateManager.enableCull();
		lineColor();
	}
}
