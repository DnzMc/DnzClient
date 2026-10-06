package com.dnz.client.nvg;

import static org.lwjgl.nanovg.NanoVG.*;

import com.dnz.client.gui.Icon;
import com.dnz.client.gui.Theme;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import org.lwjgl.BufferUtils;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.nanovg.NanoVGGL3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

/**
 * NanoVG menus: draws the recorded DNZ menu (NvgRecorder) on the graphics card, on top of the finished frame,
 * right before it is shown. Minecraft's OpenGL state is saved and put back (GlState).
 */
public final class NvgOverlay {
	private static long vg;
	private static final NVGColor C1 = NVGColor.create();
	private static final NVGColor C2 = NVGColor.create();
	private static final NVGPaint PAINT = NVGPaint.create();
	private static final float[] BOUNDS = new float[4];
	/** NanoVG font size per face that gives the same text width as Minecraft's font (GUI units). */
	private static final Map<String, Float> SIZE = new HashMap<>();
	private static final ByteBuffer[] KEEP = new ByteBuffer[8];

	private NvgOverlay() {
	}

	/** Called right before the frame is shown, with the window's framebuffer size. */
	public static void draw(int w, int h) {
		if (!NvgRecorder.fresh) {
			NvgRecorder.ready = null; // no DNZ menu recorded since the last frame: it was closed
		}
		NvgRecorder.fresh = false;
		List<NvgRecorder.Cmd> cmds = NvgRecorder.ready;
		if (cmds == null || w <= 0 || h <= 0 || Nvg.broken) {
			return;
		}
		GlState saved = GlState.save();
		try {
			if (vg == 0) {
				vg = NanoVGGL3.nvgCreate(NanoVGGL3.NVG_ANTIALIAS | NanoVGGL3.NVG_STENCIL_STROKES);
				if (vg == 0) {
					throw new IllegalStateException("nvgCreate failed");
				}
				loadFonts();
			}
			GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
			GL11.glViewport(0, 0, w, h);
			GL15.glBindBuffer(0x88EC, 0); // pixel unpack buffer
			GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
			GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
			GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
			GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
			for (int i = 0; i < 4; i++) {
				GL33.glBindSampler(i, 0); // Minecraft's sampler objects would override NanoVG's texture filtering
			}
			GL13.glActiveTexture(GL13.GL_TEXTURE0);
			float scale = (float) Minecraft.getInstance().getWindow().getGuiScale();
			nvgBeginFrame(vg, w, h, 1);
			for (NvgRecorder.Cmd c : cmds) {
				if (c instanceof NvgRecorder.Shape s) {
					place(scale, s.m(), s.scissor());
					shape(s);
				} else if (c instanceof NvgRecorder.Image i) {
					place(scale, i.m(), i.scissor());
					image(i);
				} else if (c instanceof NvgRecorder.Text t) {
					place(scale, t.m(), t.scissor());
					text(t);
				}
			}
			nvgEndFrame(vg);
		} catch (Throwable e) {
			Nvg.broken = true; // the normal menus take over from the next frame on
			org.slf4j.LoggerFactory.getLogger("DNZ NanoVG").error("NanoVG menus failed, using the normal menus", e);
		} finally {
			saved.restore();
		}
	}

	/** GUI units -> window pixels, the clip area (screen coordinates), then the element's own transform. */
	private static void place(float scale, float[] m, ScreenRectangle scissor) {
		nvgResetTransform(vg);
		nvgScale(vg, scale, scale);
		if (scissor != null) {
			nvgScissor(vg, scissor.left(), scissor.top(), scissor.width(), scissor.height());
		} else {
			nvgResetScissor(vg);
		}
		nvgTransform(vg, m[0], m[1], m[2], m[3], m[4], m[5]);
	}

	private static NVGColor color(NVGColor c, int argb) {
		return nvgRGBA((byte) (argb >> 16), (byte) (argb >> 8), (byte) argb, (byte) (argb >>> 24), c);
	}

	private static void shape(NvgRecorder.Shape s) {
		float r = Math.max(0, Math.min(s.radius(), Math.min(s.w(), s.h()) / 2));
		if (s.softness() > 0) {
			// Smooth.shadow passes the box already grown by the blur on every side.
			float b = s.softness();
			nvgBoxGradient(vg, s.x() + b, s.y() + b, s.w() - 2 * b, s.h() - 2 * b, Math.max(0, r - b), 2 * b,
				color(C1, s.from()), color(C2, s.from() & 0xFFFFFF), PAINT);
			nvgBeginPath(vg);
			nvgRect(vg, s.x(), s.y(), s.w(), s.h());
			nvgFillPaint(vg, PAINT);
			nvgFill(vg);
			return;
		}
		nvgBeginPath(vg);
		if (r > 0) {
			nvgRoundedRect(vg, s.x(), s.y(), s.w(), s.h(), r);
		} else {
			nvgRect(vg, s.x(), s.y(), s.w(), s.h());
		}
		if (s.from() != s.to()) {
			if (s.horizontal()) {
				nvgLinearGradient(vg, s.x(), s.y(), s.x() + s.w(), s.y(), color(C1, s.from()), color(C2, s.to()), PAINT);
			} else {
				nvgLinearGradient(vg, s.x(), s.y(), s.x(), s.y() + s.h(), color(C1, s.from()), color(C2, s.to()), PAINT);
			}
			nvgFillPaint(vg, PAINT);
		} else {
			nvgFillColor(vg, color(C1, s.from()));
		}
		nvgFill(vg);
	}

	/** NanoVG image handles of Minecraft textures (by OpenGL id). */
	private static final Map<Integer, Integer> IMAGES = new HashMap<>();

	private static void image(NvgRecorder.Image i) {
		if (!(i.texture() instanceof GpuTextureView view) || !(view.texture() instanceof GlTexture tex) || i.u1() == i.u0() || i.v1() == i.v0()) {
			return;
		}
		int w = tex.getWidth(0), h = tex.getHeight(0);
		int img = IMAGES.computeIfAbsent(tex.glId(), id -> {
			// Minecraft samples through sampler objects; NanoVG reads the texture's own filter, so give it one.
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
			return NanoVGGL3.nvglCreateImageFromHandle(vg, id, w, h, NanoVGGL3.NVG_IMAGE_NODELETE);
		});
		float iw = (i.x1() - i.x0()) / (i.u1() - i.u0());
		float ih = (i.y1() - i.y0()) / (i.v1() - i.v0());
		nvgImagePattern(vg, i.x0() - i.u0() * iw, i.y0() - i.v0() * ih, iw, ih, 0, img, 1, PAINT);
		color(C1, i.color());
		PAINT.innerColor(C1);
		PAINT.outerColor(C1);
		nvgBeginPath(vg);
		nvgRect(vg, i.x0(), i.y0(), i.x1() - i.x0(), i.y1() - i.y0());
		nvgFillPaint(vg, PAINT);
		nvgFill(vg);
	}

	private static void text(NvgRecorder.Text t) {
		nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_BASELINE);
		for (int i = 0; i < t.runs().size(); i++) {
			NvgRecorder.Run run = t.runs().get(i);
			String face = face(run.font(), run.bold());
			nvgFontFace(vg, face);
			nvgFontSize(vg, size(face, run.font()));
			float baseline = t.y() + (face.equals("icons") ? 8.0F : 7.0F);
			if (t.shadow()) {
				int c = run.color();
				int dark = (c & 0xFF000000) | ((c >> 2) & 0x3F3F3F);
				nvgFillColor(vg, color(C1, dark));
				nvgText(vg, t.xs()[i] + 1, baseline + 1, run.text());
			}
			nvgFillColor(vg, color(C1, run.color()));
			nvgText(vg, t.xs()[i], baseline, run.text());
		}
	}

	private static String face(FontDescription font, boolean bold) {
		if (font.equals(Icon.FONT)) {
			return "icons";
		}
		if (font.equals(Theme.TITLE_FONT) || font.equals(Theme.BOLD_FONT)) {
			return "bold";
		}
		if (font.equals(Theme.UI_SEMIBOLD)) {
			return "semibold";
		}
		if (font.equals(Theme.UI_MEDIUM)) {
			return bold ? "semibold" : "medium";
		}
		return bold ? "semibold" : "regular";
	}

	/** Font size so NanoVG's text is as wide as Minecraft's (measured once per face). */
	private static float size(String face, FontDescription font) {
		return SIZE.computeIfAbsent(face, f -> {
			String sample = f.equals("icons") ? String.valueOf(Icon.MODS) + Icon.SEARCH + Icon.CLOSE : "The quick brown fox jumps over 1234567890";
			Font mc = Minecraft.getInstance().font;
			FontDescription measured = f.equals("icons") ? Icon.FONT : font.equals(FontDescription.DEFAULT) ? Theme.UI_REGULAR : font;
			float want = mc.width(Component.literal(sample).withStyle(st -> st.withFont(measured)));
			nvgFontSize(vg, 10);
			float got = nvgTextBounds(vg, 0, 0, sample, BOUNDS);
			return got > 0 && want > 0 ? 10 * want / got : 9;
		});
	}

	private static void loadFonts() {
		String[][] fonts = {{"regular", "poppins-regular.ttf"}, {"medium", "poppins-medium.ttf"}, {"semibold", "poppins-semibold.ttf"},
			{"bold", "poppins-bold.ttf"}, {"icons", "icons.ttf"}};
		for (int i = 0; i < fonts.length; i++) {
			try (InputStream in = NvgOverlay.class.getResourceAsStream("/assets/dnzclient/font/" + fonts[i][1])) {
				byte[] b = in.readAllBytes();
				KEEP[i] = BufferUtils.createByteBuffer(b.length).put(b).flip();
				nvgCreateFontMem(vg, fonts[i][0], KEEP[i], false);
			} catch (Exception e) {
				throw new IllegalStateException("font " + fonts[i][1], e);
			}
		}
	}
}
