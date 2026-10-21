package com.dnz.client189;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/**
 * Smooth TrueType text for 1.8.9 (whose own font is pixel-only), so the DNZ menu uses the same Poppins /
 * Inter letters and Material icons as on 26.x.
 */
public final class TtfFont {
	private static final int PX = 64;
	private static final int PAD = 4;

	public static final TtfFont REGULAR = new TtfFont("/assets/dnzclient/font/inter.ttf");
	public static final TtfFont MEDIUM = new TtfFont("/assets/dnzclient/font/poppins-semibold.ttf");
	public static final TtfFont BOLD = new TtfFont("/assets/dnzclient/font/poppins-bold.ttf");
	public static final TtfFont ICONS = new TtfFont("/assets/dnzclient/font/icons.ttf");

	private final String path;
	private Font font;
	private FontMetrics metrics;
	private final Map<Character, Glyph> glyphs = new HashMap<Character, Glyph>();

	private static final class Glyph {
		DynamicTexture texture;
		int w;
		int h;
		int advance;
	}

	private TtfFont(String path) {
		this.path = path;
	}

	private void load() {
		if (this.font != null) {
			return;
		}
		try {
			InputStream in = TtfFont.class.getResourceAsStream(this.path);
			try {
				this.font = Font.createFont(Font.TRUETYPE_FONT, in).deriveFont((float) PX);
			} finally {
				in.close();
			}
		} catch (Exception e) {
			this.font = new Font("SansSerif", Font.PLAIN, PX);
		}
		Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
		hints(g);
		this.metrics = g.getFontMetrics(this.font);
		g.dispose();
	}

	private static void hints(Graphics2D g) {
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
	}

	private Glyph glyph(char c) {
		Glyph glyph = this.glyphs.get(c);
		if (glyph == null) {
			this.load();
			glyph = new Glyph();
			glyph.advance = Math.max(1, this.metrics.charWidth(c));
			glyph.w = glyph.advance + PAD * 2;
			glyph.h = this.metrics.getHeight() + PAD * 2;
			BufferedImage image = new BufferedImage(glyph.w, glyph.h, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = image.createGraphics();
			hints(g);
			g.setFont(this.font);
			g.setColor(Color.WHITE);
			g.drawString(String.valueOf(c), PAD, PAD + this.metrics.getAscent());
			g.dispose();
			glyph.texture = new DynamicTexture(image);
			GlStateManager.bindTexture(glyph.texture.getGlTextureId());
			// Smaller copies of the letter (mipmaps), so it stays sharp and calm when drawn small
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL14.GL_GENERATE_MIPMAP, GL11.GL_TRUE);
			glyph.texture.updateDynamicTexture();
			GlStateManager.bindTexture(glyph.texture.getGlTextureId());
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
			this.glyphs.put(c, glyph);
		}
		return glyph;
	}

	/** Width of [text] when drawn [size] units tall. */
	public float width(String text, float size) {
		this.load();
		float scale = size / this.metrics.getHeight();
		float w = 0;
		for (int i = 0; i < text.length(); i++) {
			w += this.metrics.charWidth(text.charAt(i)) * scale;
		}
		return w;
	}

	public float draw(String text, float x, float y, float size, int color) {
		this.load();
		float scale = size / this.metrics.getHeight();
		GlStateManager.enableBlend();
		GlStateManager.enableTexture2D();
		GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
		GlStateManager.color((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F, (color >>> 24) / 255F);
		Tessellator tess = Tessellator.getInstance();
		WorldRenderer wr = tess.getWorldRenderer();
		float cx = x;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			Glyph glyph = this.glyph(c);
			if (c != ' ') {
				GlStateManager.bindTexture(glyph.texture.getGlTextureId());
				float gx = cx - PAD * scale;
				float gy = y - PAD * scale;
				float gw = glyph.w * scale;
				float gh = glyph.h * scale;
				wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
				wr.pos(gx, gy + gh, 0).tex(0, 1).endVertex();
				wr.pos(gx + gw, gy + gh, 0).tex(1, 1).endVertex();
				wr.pos(gx + gw, gy, 0).tex(1, 0).endVertex();
				wr.pos(gx, gy, 0).tex(0, 0).endVertex();
				tess.draw();
			}
			cx += glyph.advance * scale;
		}
		GlStateManager.color(1, 1, 1, 1);
		return cx - x;
	}

	public void drawCentered(String text, float cx, float y, float size, int color) {
		this.draw(text, cx - this.width(text, size) / 2, y, size, color);
	}

	public void drawSpaced(String text, float cx, float y, float size, float spacing, int color) {
		float total = this.width(text, size) + spacing * (text.length() - 1);
		float x = cx - total / 2;
		for (int i = 0; i < text.length(); i++) {
			String s = String.valueOf(text.charAt(i));
			this.draw(s, x, y, size, color);
			x += this.width(s, size) + spacing;
		}
	}

	public static void icon(char icon, float x, float y, float size, int color) {
		ICONS.draw(String.valueOf(icon), x - size * 0.06F, y - size * 0.18F, size * 1.25F, color);
	}
}
