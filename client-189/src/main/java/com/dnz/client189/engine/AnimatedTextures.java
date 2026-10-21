package com.dnz.client189.engine;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import com.dnz.client189.mixin.TextureMapAccessor;

/**
 * DNZ Engine, animated textures: Minecraft 1.8.9 sends every animated block texture (water, lava, portal...)
 * to the graphics card 20 times a second, also when none is on screen.
 */
public final class AnimatedTextures {
	private static TextureAtlasSprite[] tracked;
	private static float[] rects;
	private static long seen;

	private AnimatedTextures() {
	}

	public static void reset() {
		tracked = null;
		seen = 0;
	}

	private static void init() {
		if (tracked != null) {
			return;
		}
		List<TextureAtlasSprite> all = ((TextureMapAccessor) Minecraft.getMinecraft().getTextureMapBlocks()).dnz$animated();
		java.util.ArrayList<TextureAtlasSprite> list = new java.util.ArrayList<TextureAtlasSprite>();
		for (TextureAtlasSprite s : all) {
			// Block textures only (the clock and compass are animated item textures, shown in the hand)
			if (list.size() < 64 && s.getIconName().contains("blocks/") && !s.getIconName().contains("fire")) {
				list.add(s);
			}
		}
		tracked = list.toArray(new TextureAtlasSprite[0]);
		rects = new float[tracked.length * 4];
		for (int i = 0; i < tracked.length; i++) {
			rects[i * 4] = tracked[i].getMinU();
			rects[i * 4 + 1] = tracked[i].getMaxU();
			rects[i * 4 + 2] = tracked[i].getMinV();
			rects[i * 4 + 3] = tracked[i].getMaxV();
		}
	}

	/** Which tracked animated textures a quad with texture coordinate (u, v) uses (a bit mask, 0 = none). */
	public static long maskOf(float u, float v) {
		init();
		for (int i = 0; i < tracked.length; i++) {
			if (u >= rects[i * 4] && u <= rects[i * 4 + 1] && v >= rects[i * 4 + 2] && v <= rects[i * 4 + 3]) {
				return 1L << i;
			}
		}
		return 0;
	}

	public static void drawn(long mask) {
		seen |= mask;
	}

	/** True if this texture should be animated now. */
	public static boolean animate(TextureAtlasSprite sprite) {
		if (!Regions.active || tracked == null) {
			return true;
		}
		for (int i = 0; i < tracked.length; i++) {
			if (tracked[i] == sprite) {
				return (seen & (1L << i)) != 0;
			}
		}
		return true;
	}

	/** After an animation update: start collecting again. */
	public static void updated() {
		seen = 0;
	}
}
