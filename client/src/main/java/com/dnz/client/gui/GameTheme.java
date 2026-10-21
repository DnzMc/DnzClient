package com.dnz.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * The applied DNZ theme for the in-game DNZ menu (see docs/dnz-theme.md): colors, roundness, glow and
 * decorations (background picture, pattern, stickers).
 */
public final class GameTheme {
	private static final Path DIR = Path.of(System.getProperty("user.home"), ".dnzlauncher", "theme");
	private static final int MAX_PICTURE = 1_000_000;
	private static final int MAX_SIDE = 2048;

	// Colors (ARGB). Defaults = DNZ's own menu.
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
	public static final List<Sticker> stickers = new ArrayList<>();

	private static long loadedModified = -1;

	/** A picture of the theme, loaded as a texture. */
	public record Picture(Identifier id, int width, int height) {
	}

	/** A sticker around the panel. */
	public record Sticker(Picture picture, String anchor, float x, float y, float size, boolean floating, float spin) {
	}

	private GameTheme() {
	}

	/** The accent color: the theme's, or the menu's usual one. */
	public static int accent(int usual) {
		return accent != 0 ? accent : usual;
	}

	/** Reads the theme again if it changed (cheap when it didn't). */
	public static void refresh() {
		Path file = DIR.resolve("theme.json");
		long modified;
		try {
			modified = Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() : 0;
		} catch (Exception e) {
			modified = 0;
		}
		if (modified == loadedModified) {
			return;
		}
		loadedModified = modified;
		reset();
		if (modified == 0) {
			return;
		}
		try {
			JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
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
				background = picture(b, "image", "bg");
				backgroundDim = number(b, "dim", backgroundDim, 0, 1);
			}
			if (game.has("pattern")) {
				JsonObject p = game.getAsJsonObject("pattern");
				pattern = picture(p, "image", "pattern");
				patternOpacity = number(p, "opacity", patternOpacity, 0, 0.5F);
			}
			if (game.has("decorations")) {
				JsonArray list = game.getAsJsonArray("decorations");
				for (int i = 0; i < Math.min(8, list.size()); i++) {
					JsonObject d = list.get(i).getAsJsonObject();
					Picture pic = picture(d, "image", "sticker" + i);
					if (pic == null) {
						continue;
					}
					String anchor = d.has("anchor") ? d.get("anchor").getAsString() : "top-right";
					stickers.add(new Sticker(pic, anchor, number(d, "x", 0, -200, 200), number(d, "y", 0, -200, 200),
						number(d, "size", 32, 8, 128), d.has("float") && d.get("float").getAsBoolean(), number(d, "spin", 0, -360, 360)));
				}
			}
		} catch (Exception e) {
			reset(); // a broken theme: DNZ's own look
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

	/** A picture from the theme's images folder (only there, PNG or JPG, size limits), or null. */
	private static Picture picture(JsonObject o, String key, String name) {
		if (!o.has(key)) {
			return null;
		}
		try {
			Path file = DIR.resolve(o.get(key).getAsString()).normalize();
			String lower = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
			if (!file.startsWith(DIR.resolve("images")) || !(lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg"))
				|| !Files.isRegularFile(file) || Files.size(file) > MAX_PICTURE) {
				return null;
			}
			NativeImage image = NativeImage.read(Files.readAllBytes(file));
			if (image.getWidth() > MAX_SIDE || image.getHeight() > MAX_SIDE) {
				image.close();
				return null;
			}
			Identifier id = Identifier.fromNamespaceAndPath("dnzclient", "theme/" + name);
			Minecraft mc = Minecraft.getInstance();
			mc.getTextureManager().release(id);
			mc.getTextureManager().register(id, new DynamicTexture(() -> "DNZ theme " + name, image));
			return new Picture(id, image.getWidth(), image.getHeight());
		} catch (Exception e) {
			return null;
		}
	}

	/** Draws [p] to fill (x, y, w, h), cropping to keep its shape, with [alpha] (0..1). */
	public static void cover(GuiGraphicsExtractor g, Picture p, int x, int y, int w, int h, float alpha) {
		float scale = Math.max(w / (float) p.width(), h / (float) p.height());
		int uw = Math.round(w / scale);
		int vh = Math.round(h / scale);
		int u = (p.width() - uw) / 2;
		int v = (p.height() - vh) / 2;
		g.blit(RenderPipelines.GUI_TEXTURED, p.id(), x, y, u, v, w, h, uw, vh, p.width(), p.height(), Math.round(alpha * 255) << 24 | 0xFFFFFF);
	}

	/** Draws [p] stretched into (x, y, w, h). */
	public static void draw(GuiGraphicsExtractor g, Picture p, int x, int y, int w, int h) {
		g.blit(RenderPipelines.GUI_TEXTURED, p.id(), x, y, 0, 0, w, h, p.width(), p.height(), p.width(), p.height(), 0xFFFFFFFF);
	}
}
