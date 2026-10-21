package com.dnz.client189;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * The applied DNZ theme on 1.8.9 (the same file as 26.x, see docs/dnz-theme.md): colors, roundness, glow and
 * decorations of the DNZ menu.
 */
public final class GameTheme {
	private static final File DIR = new File(System.getProperty("user.home"), ".dnzlauncher/theme");

	public static int accent = 0;
	public static int panel = 0xFA0A0D12;
	public static int panelEdge = 0xFF1B212C;
	public static int panelGradient = 0;
	public static int card = 0xFF121821;
	public static int cardHover = 0xFF161D28;
	public static int cardEdge = 0xFF1B212C;
	public static int search = 0xFF151B25;
	public static int text = 0xFFF1F3F7;
	public static int muted = 0xFF8A93A6;
	public static float radius = 10;
	public static float cardRadius = 8;
	public static float glow = 0;
	public static Picture background;
	public static float backgroundDim = 0.4F;
	public static Picture pattern;
	public static float patternOpacity = 0.08F;
	public static final List<Sticker> stickers = new ArrayList<Sticker>();

	private static long loadedModified = -1;

	public static final class Picture {
		final ResourceLocation id;
		final int width;
		final int height;

		Picture(ResourceLocation id, int width, int height) {
			this.id = id;
			this.width = width;
			this.height = height;
		}
	}

	public static final class Sticker {
		final Picture picture;
		final String anchor;
		final float x;
		final float y;
		final float size;
		final boolean floating;
		final float spin;

		Sticker(Picture picture, String anchor, float x, float y, float size, boolean floating, float spin) {
			this.picture = picture;
			this.anchor = anchor;
			this.x = x;
			this.y = y;
			this.size = size;
			this.floating = floating;
			this.spin = spin;
		}
	}

	private GameTheme() {
	}

	public static int accent() {
		return accent != 0 ? accent : Render2D.ORANGE;
	}

	public static void refresh() {
		File file = new File(DIR, "theme.json");
		long modified = file.isFile() ? file.lastModified() : 0;
		if (modified == loadedModified) {
			return;
		}
		loadedModified = modified;
		reset();
		if (modified == 0) {
			return;
		}
		try {
			JsonObject root = new JsonParser().parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
			JsonObject game = root.has("game") ? root.getAsJsonObject("game") : new JsonObject();
			if (game.has("colors")) {
				JsonObject c = game.getAsJsonObject("colors");
				accent = color(c, "accent", accent);
				panel = color(c, "panel", panel);
				panelEdge = color(c, "panelEdge", panelEdge);
				panelGradient = color(c, "panelGradient", panelGradient);
				card = color(c, "card", card);
				cardHover = color(c, "cardHover", cardHover);
				cardEdge = color(c, "cardEdge", cardEdge);
				search = color(c, "search", search);
				text = color(c, "text", text);
				muted = color(c, "muted", muted);
			}
			if (game.has("style")) {
				JsonObject s = game.getAsJsonObject("style");
				radius = number(s, "radius", radius, 0, 20);
				cardRadius = number(s, "cardRadius", cardRadius, 0, 20);
				float opacity = number(s, "panelOpacity", (panel >>> 24) / 255.0F, 0.5F, 1);
				panel = Math.round(opacity * 255) << 24 | (panel & 0xFFFFFF);
				glow = number(s, "glow", glow, 0, 1);
			}
			if (game.has("background")) {
				JsonObject b = game.getAsJsonObject("background");
				background = picture(b, "bg");
				backgroundDim = number(b, "dim", backgroundDim, 0, 1);
			}
			if (game.has("pattern")) {
				JsonObject p = game.getAsJsonObject("pattern");
				pattern = picture(p, "pattern");
				patternOpacity = number(p, "opacity", patternOpacity, 0, 0.5F);
			}
			if (game.has("decorations")) {
				JsonArray list = game.getAsJsonArray("decorations");
				for (int i = 0; i < Math.min(8, list.size()); i++) {
					JsonObject d = list.get(i).getAsJsonObject();
					Picture pic = picture(d, "sticker" + i);
					if (pic != null) {
						stickers.add(new Sticker(pic, d.has("anchor") ? d.get("anchor").getAsString() : "top-right",
							number(d, "x", 0, -200, 200), number(d, "y", 0, -200, 200), number(d, "size", 32, 8, 128),
							d.has("float") && d.get("float").getAsBoolean(), number(d, "spin", 0, -360, 360)));
					}
				}
			}
		} catch (Exception e) {
			reset();
		}
	}

	private static void reset() {
		accent = 0;
		panel = 0xFA0A0D12;
		panelEdge = 0xFF1B212C;
		panelGradient = 0;
		card = 0xFF121821;
		cardHover = 0xFF161D28;
		cardEdge = 0xFF1B212C;
		search = 0xFF151B25;
		text = 0xFFF1F3F7;
		muted = 0xFF8A93A6;
		radius = 10;
		cardRadius = 8;
		glow = 0;
		background = null;
		backgroundDim = 0.4F;
		pattern = null;
		patternOpacity = 0.08F;
		stickers.clear();
	}

	private static int color(JsonObject o, String key, int fallback) {
		JsonElement e = o.get(key);
		if (e == null) {
			return fallback;
		}
		String s = e.getAsString().trim();
		if (s.startsWith("#")) {
			s = s.substring(1);
		}
		try {
			if (s.length() == 6) {
				return 0xFF000000 | Integer.parseInt(s, 16);
			}
			if (s.length() == 8) {
				return (int) Long.parseLong(s, 16);
			}
		} catch (NumberFormatException ignored) {
		}
		return fallback;
	}

	private static float number(JsonObject o, String key, float fallback, float min, float max) {
		try {
			return o.has(key) ? Math.max(min, Math.min(max, o.get(key).getAsFloat())) : fallback;
		} catch (Exception e) {
			return fallback;
		}
	}

	private static Picture picture(JsonObject o, String name) {
		if (!o.has("image")) {
			return null;
		}
		try {
			File images = new File(DIR, "images").getCanonicalFile();
			File file = new File(DIR, o.get("image").getAsString()).getCanonicalFile();
			String lower = file.getName().toLowerCase();
			if (!file.getPath().startsWith(images.getPath() + File.separator) || !(lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg"))
				|| !file.isFile() || file.length() > 1000000) {
				return null;
			}
			BufferedImage image = ImageIO.read(file);
			if (image == null || image.getWidth() > 2048 || image.getHeight() > 2048) {
				return null;
			}
			ResourceLocation id = new ResourceLocation("dnzclient", "theme/" + name);
			Minecraft.getMinecraft().getTextureManager().deleteTexture(id);
			Minecraft.getMinecraft().getTextureManager().loadTexture(id, new DynamicTexture(image));
			return new Picture(id, image.getWidth(), image.getHeight());
		} catch (Exception e) {
			return null;
		}
	}

	/** Draws [p] filling (x, y, w, h), cropped to keep its shape, with [alpha]. */
	public static void cover(Picture p, float x, float y, float w, float h, float alpha) {
		float scale = Math.max(w / p.width, h / p.height);
		float uw = w / scale / p.width;
		float vh = h / scale / p.height;
		float u0 = (1 - uw) / 2;
		float v0 = (1 - vh) / 2;
		quad(p, x, y, w, h, u0, v0, u0 + uw, v0 + vh, alpha);
	}

	public static void draw(Picture p, float x, float y, float w, float h) {
		quad(p, x, y, w, h, 0, 0, 1, 1, 1);
	}

	private static void quad(Picture p, float x, float y, float w, float h, float u0, float v0, float u1, float v1, float alpha) {
		Minecraft.getMinecraft().getTextureManager().bindTexture(p.id);
		GlStateManager.enableBlend();
		GlStateManager.enableTexture2D();
		GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
		GlStateManager.color(1, 1, 1, alpha);
		Tessellator tess = Tessellator.getInstance();
		WorldRenderer wr = tess.getWorldRenderer();
		wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
		wr.pos(x, y + h, 0).tex(u0, v1).endVertex();
		wr.pos(x + w, y + h, 0).tex(u1, v1).endVertex();
		wr.pos(x + w, y, 0).tex(u1, v0).endVertex();
		wr.pos(x, y, 0).tex(u0, v0).endVertex();
		tess.draw();
		GlStateManager.color(1, 1, 1, 1);
	}
}
