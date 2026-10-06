package com.dnz.client.nvg;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.FontDescription;
import org.joml.Matrix3x2fc;

/**
 * NanoVG menus: while a DNZ menu builds its frame, its shapes and texts are recorded here instead of going to
 * Minecraft's renderer; right before the frame is shown, NanoVG draws them on the graphics card (NvgOverlay).
 * Anything else (item icons, pictures) still goes through Minecraft.
 */
public final class NvgRecorder {
	/** Recording this frame (render thread only). */
	public static boolean recording;
	private static List<Cmd> building = new ArrayList<>();
	/** The last finished frame, drawn by the overlay; null when no NanoVG menu is open. */
	public static volatile List<Cmd> ready;
	/** Set when a frame was recorded since the overlay last drew (a closed menu then disappears at once). */
	public static volatile boolean fresh;

	private NvgRecorder() {
	}

	public static void begin() {
		building = new ArrayList<>();
		recording = true;
	}

	public static void end() {
		recording = false;
		ready = building;
		fresh = true;
	}

	/** One recorded drawing step. Positions are GUI units; m = the 2D transform (a, b, c, d, e, f). */
	public sealed interface Cmd permits Shape, Text, Image {
	}

	/** Rounded box, gradient (from -> to) or soft shadow (softness > 0). */
	public record Shape(float[] m, float x, float y, float w, float h, float radius, float softness, int from, int to,
						boolean horizontal, ScreenRectangle scissor) implements Cmd {
	}

	/** One piece of text in one font and color. */
	public record Run(String text, FontDescription font, int color, boolean bold) {
	}

	/** A line of text; x is where each run starts (GUI units, from Minecraft's own font widths). */
	public record Text(float[] m, List<Run> runs, float[] xs, float y, boolean shadow, ScreenRectangle scissor) implements Cmd {
	}

	/** A picture (skin head, mod icon): texture = Minecraft's texture view, drawn from u0,v0 to u1,v1 (0..1). */
	public record Image(float[] m, Object texture, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1,
						int color, ScreenRectangle scissor) implements Cmd {
	}

	public static float[] matrix(Matrix3x2fc p) {
		return new float[] {p.m00(), p.m01(), p.m10(), p.m11(), p.m20(), p.m21()};
	}

	public static void add(Cmd cmd) {
		building.add(cmd);
	}
}
