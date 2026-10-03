package com.dnz.client.gui;

import com.dnz.client.mixin.MinecraftAccessor;
import com.mojang.blaze3d.font.GlyphProvider;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

/**
 * Keeps DNZ text and icons sharp and smooth at any size. Minecraft samples font atlases pixel by pixel ("nearest"),
 * which makes scaled text and icons look blocky; our fonts are drawn with smooth (linear) sampling instead, and their
 * glyphs are rendered for the screen's real pixel size, so they are never stretched up (blurry) or squeezed down.
 * <p>
 * The pixel size changes when the window is resized, the GUI scale changes or the DNZ menu opens (it has its own
 * scale, see {@link MenuScale}); then our glyphs are rendered again for the new size. That only re-renders the
 * letters on screen (a few milliseconds, once), nothing is loaded from disk.
 */
public final class SharpFonts {
	/** One of our TrueType fonts (see TrueTypeProviderMixin). */
	public interface Sharp {
		/** Renders the glyphs again at [oversample] texture pixels per GUI unit. */
		void dnz$setOversample(float oversample);
	}

	/** Font atlas textures of our fonts (weak, so reloaded fonts can go away). */
	private static final Set<Object> SMOOTH_ATLASES = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
	/** Our TrueType fonts and the biggest scale each is drawn at (weak, so reloaded fonts can go away). */
	private static final Map<Sharp, Float> FONTS = Collections.synchronizedMap(new WeakHashMap<>());
	/** Screen pixels per GUI unit our glyphs are rendered for now (0 = not decided yet). */
	private static volatile float density;

	private SharpFonts() {
	}

	public static boolean isOurs(Identifier location) {
		return location.getNamespace().equals("dnzclient");
	}

	/**
	 * How big each of our fonts is drawn at most: the title screen logo up to about 3.2 times the text size, icons
	 * up to 2.2 (module cards), other text up to 1.35 (titles).
	 */
	private static float biggestScale(Identifier location) {
		String path = location.getPath();
		if (path.contains("title")) {
			return 3.2F;
		}
		return path.contains("icons") ? 2.2F : 1.35F;
	}

	/** Glyph detail of a font when it is loaded: the current pixel size times the biggest size we draw it at. */
	public static float oversample(Identifier location, float fromFile) {
		if (!isOurs(location)) {
			return fromFile;
		}
		float pixels = density;
		if (pixels <= 0) {
			pixels = 4.0F; // window not ready yet: a typical Full HD scale, corrected on the first tick
			try {
				int guiScale = Minecraft.getInstance().getWindow().getGuiScale();
				if (guiScale > 0) {
					pixels = guiScale;
				}
			} catch (RuntimeException ignored) {
			}
			density = pixels;
		}
		return Math.max(1.0F, pixels * biggestScale(location));
	}

	/** Remembers a loaded font of ours, so it can be rendered again when the pixel size changes. */
	public static void register(Identifier location, GlyphProvider provider) {
		if (isOurs(location) && provider instanceof Sharp sharp) {
			FONTS.put(sharp, biggestScale(location));
		}
	}

	/** Screen pixels per GUI unit of what [screen] draws: the DNZ menu has its own scale, everything else the GUI scale. */
	private static float wanted(Minecraft mc, Screen screen) {
		if (screen instanceof DnzMenuScreen && Theme.dnzStyle()) {
			return MenuScale.pixels(mc.getWindow());
		}
		return mc.getWindow().getGuiScale();
	}

	/**
	 * Renders our glyphs again when what is on screen needs a different pixel size (more than 12% off, so small
	 * window drags don't re-render every time). Call between frames only (ticks, screen setup), never while drawing.
	 */
	public static void update(Minecraft mc, Screen screen) {
		float want = wanted(mc, screen);
		float now = density;
		if (want <= 0 || FONTS.isEmpty() || now > 0 && Math.abs(want / now - 1.0F) < 0.12F) {
			return;
		}
		density = want;
		synchronized (FONTS) {
			FONTS.forEach((font, scale) -> font.dnz$setOversample(want * scale));
		}
		// Drops the glyphs already in the font atlases (like switching "Force Unicode Font"), so they are made again.
		((MinecraftAccessor) mc).dnz$fontManager().updateOptions(mc.options);
	}

	public static void registerAtlas(String label, Object textureView) {
		if (label != null && label.startsWith("dnzclient:") && textureView != null) {
			if (SMOOTH_ATLASES.isEmpty()) {
				org.slf4j.LoggerFactory.getLogger("DNZ Client").info("Smooth font sampling on (first atlas: {})", label);
			}
			SMOOTH_ATLASES.add(textureView);
		}
	}

	public static boolean smooth(Object textureView) {
		return textureView != null && SMOOTH_ATLASES.contains(textureView);
	}
}
