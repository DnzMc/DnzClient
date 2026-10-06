package com.dnz.client.skia;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.function.Consumer;
import org.jetbrains.skia.BackendRenderTarget;
import org.jetbrains.skia.Canvas;
import org.jetbrains.skia.ColorSpace;
import org.jetbrains.skia.DirectContext;
import org.jetbrains.skia.FramebufferFormat;
import org.jetbrains.skia.Surface;
import org.jetbrains.skia.SurfaceColorFormat;
import org.jetbrains.skia.SurfaceOrigin;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

/**
 * Experiment: Skia (the engine browsers use) draws on top of the finished frame, on the graphics card, in Minecraft's
 * own OpenGL window, right before the frame is shown. Minecraft's OpenGL state is saved before and put back after,
 * so the game does not notice. OpenGL only (Minecraft's Vulkan mode keeps the normal menus).
 */
public final class SkiaOverlay {
	/** What to draw this frame (set by a screen while it is open), or null. Called on the render thread. */
	public static volatile Consumer<Canvas> painter;
	/** NanoVG comparison: draws with the NanoVG context handle this frame, or null. */
	public static volatile java.util.function.LongConsumer nanoPainter;
	private static long nvg;
	/** Time Skia took in the last frame (CPU side, nanoseconds), for the benchmark. */
	public static volatile long lastNanos;

	private static DirectContext context;
	private static BackendRenderTarget target;
	private static Surface surface;
	private static int width;
	private static int height;
	private static boolean failed;

	private SkiaOverlay() {
	}

	/** Called right before the frame is shown, with the window's framebuffer size. */
	public static void draw(int w, int h) {
		drawNano(w, h);
		Consumer<Canvas> p = painter;
		if (p == null || failed || w <= 0 || h <= 0) {
			return;
		}
		long t0 = System.nanoTime();
		State saved = State.save();
		try {
			if (context == null) {
				context = DirectContext.Companion.makeGL();
			}
			if (surface == null || w != width || h != height) {
				if (surface != null) {
					surface.close();
					target.close();
				}
				width = w;
				height = h;
				target = BackendRenderTarget.Companion.makeGL(w, h, 0, 8, 0, FramebufferFormat.GR_GL_RGBA8);
				surface = Surface.Companion.makeFromBackendRenderTarget(context, target, SurfaceOrigin.BOTTOM_LEFT,
					SurfaceColorFormat.RGBA_8888, ColorSpace.Companion.getSRGB(), null);
			}
			GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
			context.resetGLAll();
			Canvas canvas = surface.getCanvas();
			int restore = canvas.save();
			p.accept(canvas);
			canvas.restoreToCount(restore);
			context.flush(surface);
			context.submit(false);
		} catch (Throwable e) {
			failed = true; // never again this session; the normal menus keep working
			org.slf4j.LoggerFactory.getLogger("DNZ Skia").error("Skia overlay failed, turned off", e);
		} finally {
			saved.restore();
			lastNanos = System.nanoTime() - t0;
		}
	}

	private static void drawNano(int w, int h) {
		java.util.function.LongConsumer p = nanoPainter;
		if (p == null || w <= 0 || h <= 0) {
			return;
		}
		long t0 = System.nanoTime();
		State saved = State.save();
		try {
			if (nvg == 0) {
				nvg = org.lwjgl.nanovg.NanoVGGL3.nvgCreate(org.lwjgl.nanovg.NanoVGGL3.NVG_ANTIALIAS | org.lwjgl.nanovg.NanoVGGL3.NVG_STENCIL_STROKES);
			}
			GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
			GL11.glViewport(0, 0, w, h);
			// Minecraft leaves its own pixel upload settings; NanoVG's font texture needs the defaults.
			GL15.glBindBuffer(0x88EC, 0);
			GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
			GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
			GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
			GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
			// Minecraft binds sampler objects (they override texture filtering); NanoVG expects none.
			for (int i = 0; i < 4; i++) {
				GL33.glBindSampler(i, 0);
			}
			GL13.glActiveTexture(GL13.GL_TEXTURE0);
			org.lwjgl.nanovg.NanoVG.nvgBeginFrame(nvg, w, h, 1);
			p.accept(nvg);
			org.lwjgl.nanovg.NanoVG.nvgEndFrame(nvg);
		} catch (Throwable e) {
			nanoPainter = null;
			org.slf4j.LoggerFactory.getLogger("DNZ NanoVG").error("NanoVG overlay failed", e);
		} finally {
			saved.restore();
			lastNanos = System.nanoTime() - t0;
		}
	}

	/** Reads the drawn frame (for the test screenshot), RGBA, bottom row first. */
	public static ByteBuffer readFrame(int w, int h) {
		ByteBuffer pixels = BufferUtils.createByteBuffer(w * h * 4);
		GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
		GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
		return pixels;
	}

	/** The OpenGL state Minecraft expects to find unchanged. */
	private record State(int program, int vao, int arrayBuffer, int drawFbo, int readFbo, int activeTexture, int[] textures,
			int[] samplers, int[] viewport, int[] scissorBox, boolean blend, int[] blendFunc, int blendEq, boolean depth,
			boolean depthMask, boolean cull, boolean scissor, boolean stencil, int[] colorMask, int unpackAlign,
			int unpackRow, int pixelUnpackBuffer) {
		static State save() {
			int units = 4;
			int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
			int[] tex = new int[units];
			int[] smp = new int[units];
			for (int i = 0; i < units; i++) {
				GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
				tex[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
				smp[i] = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
			}
			GL13.glActiveTexture(active);
			IntBuffer b = BufferUtils.createIntBuffer(16);
			return new State(
				GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),
				GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING), GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
				GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING), active, tex, smp, get4(b, GL11.GL_VIEWPORT),
				get4(b, GL11.GL_SCISSOR_BOX), GL11.glIsEnabled(GL11.GL_BLEND),
				new int[] {GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
					GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)},
				GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB), GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
				GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK), GL11.glIsEnabled(GL11.GL_CULL_FACE),
				GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), GL11.glIsEnabled(GL11.GL_STENCIL_TEST), readColorMask(),
				GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT), GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH),
				GL11.glGetInteger(GL21_PIXEL_UNPACK_BUFFER_BINDING));
		}

		private static final int GL21_PIXEL_UNPACK_BUFFER_BINDING = 0x88EF;

		private static int[] get4(IntBuffer b, int name) {
			b.clear();
			GL11.glGetIntegerv(name, b);
			return new int[] {b.get(0), b.get(1), b.get(2), b.get(3)};
		}

		private static int[] readColorMask() {
			ByteBuffer m = BufferUtils.createByteBuffer(16);
			GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, m);
			return new int[] {m.get(0), m.get(1), m.get(2), m.get(3)};
		}

		void restore() {
			GL20.glUseProgram(this.program);
			GL30.glBindVertexArray(this.vao);
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.arrayBuffer);
			GL15.glBindBuffer(0x88EC, this.pixelUnpackBuffer); // GL_PIXEL_UNPACK_BUFFER
			GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, this.drawFbo);
			GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, this.readFbo);
			for (int i = 0; i < this.textures.length; i++) {
				GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
				GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.textures[i]);
				GL33.glBindSampler(i, this.samplers[i]);
			}
			GL13.glActiveTexture(this.activeTexture);
			GL11.glViewport(this.viewport[0], this.viewport[1], this.viewport[2], this.viewport[3]);
			GL11.glScissor(this.scissorBox[0], this.scissorBox[1], this.scissorBox[2], this.scissorBox[3]);
			set(GL11.GL_BLEND, this.blend);
			GL14.glBlendFuncSeparate(this.blendFunc[0], this.blendFunc[1], this.blendFunc[2], this.blendFunc[3]);
			GL20.glBlendEquationSeparate(this.blendEq, this.blendEq);
			set(GL11.GL_DEPTH_TEST, this.depth);
			GL11.glDepthMask(this.depthMask);
			set(GL11.GL_CULL_FACE, this.cull);
			set(GL11.GL_SCISSOR_TEST, this.scissor);
			set(GL11.GL_STENCIL_TEST, this.stencil);
			GL11.glColorMask(this.colorMask[0] != 0, this.colorMask[1] != 0, this.colorMask[2] != 0, this.colorMask[3] != 0);
			GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, this.unpackAlign);
			GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, this.unpackRow);
		}

		private static void set(int cap, boolean on) {
			if (on) {
				GL11.glEnable(cap);
			} else {
				GL11.glDisable(cap);
			}
		}
	}
}
