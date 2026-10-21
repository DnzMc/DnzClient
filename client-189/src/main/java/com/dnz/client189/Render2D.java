package com.dnz.client189;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/**
 * DNZ shapes for 1.8.9, drawn by a small GPU program (a rounded-rectangle distance shader): every pixel works
 * out how far it is from the shape's edge, so corners are perfectly smooth at any size, with no seams or
 * polygon lines, and soft shadows come from the same program.
 */
public final class Render2D {
	/** DNZ colors, the same as the 26.x menu. */
	public static final int ORANGE = 0xFFFF8A3D;
	public static final int BG = 0xF00A0D12;
	public static final int CARD = 0xFF141A24;
	public static final int CARD_HOVER = 0xFF1B2330;
	public static final int BORDER = 0xFF1B212C;
	public static final int TEXT = 0xFFE6EAF2;
	public static final int MUTED = 0xFF8A93A6;

	private static final String VERTEX =
		"#version 120\n"
			+ "varying vec2 local;\n"
			+ "void main() {\n"
			+ "  local = gl_MultiTexCoord0.xy;\n"
			+ "  gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n"
			+ "}\n";

	/** local = position inside the quad in GUI units; the quad is larger than the shape by [soft] on every side. */
	private static final String FRAGMENT =
		"#version 120\n"
			+ "varying vec2 local;\n"
			+ "uniform vec2 size;\n"
			+ "uniform float radius;\n"
			+ "uniform float soft;\n"
			+ "uniform float pixel;\n"
			+ "uniform vec4 color;\n"
			+ "void main() {\n"
			+ "  vec2 half = size * 0.5;\n"
			+ "  vec2 p = local - half;\n"
			+ "  vec2 q = abs(p) - half + vec2(radius);\n"
			+ "  float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;\n"
			+ "  float edge = max(soft, pixel);\n"
			+ "  float a = 1.0 - smoothstep(-edge * 0.5, edge * 0.5, d);\n"
			+ "  gl_FragColor = vec4(color.rgb, color.a * a);\n"
			+ "}\n";

	private static int program = -1;
	private static int uSize;
	private static int uRadius;
	private static int uSoft;
	private static int uPixel;
	private static int uColor;

	private Render2D() {
	}

	private static boolean shader() {
		if (program == -1) {
			program = 0;
			if (!OpenGlHelper.shadersSupported) {
				return false;
			}
			try {
				int vs = compile(GL20.GL_VERTEX_SHADER, VERTEX);
				int fs = compile(GL20.GL_FRAGMENT_SHADER, FRAGMENT);
				int p = GL20.glCreateProgram();
				GL20.glAttachShader(p, vs);
				GL20.glAttachShader(p, fs);
				GL20.glLinkProgram(p);
				if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
					System.err.println("DNZ shape shader: " + GL20.glGetProgramInfoLog(p, 1000));
					return false;
				}
				uSize = GL20.glGetUniformLocation(p, "size");
				uRadius = GL20.glGetUniformLocation(p, "radius");
				uSoft = GL20.glGetUniformLocation(p, "soft");
				uPixel = GL20.glGetUniformLocation(p, "pixel");
				uColor = GL20.glGetUniformLocation(p, "color");
				program = p;
			} catch (Exception e) {
				System.err.println("DNZ shape shader: " + e);
			}
		}
		return program > 0;
	}

	private static int compile(int type, String source) {
		int s = GL20.glCreateShader(type);
		GL20.glShaderSource(s, source);
		GL20.glCompileShader(s);
		if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
			throw new IllegalStateException(GL20.glGetShaderInfoLog(s, 1000));
		}
		return s;
	}

	private static float pixel() {
		java.nio.FloatBuffer m = org.lwjgl.BufferUtils.createFloatBuffer(16);
		GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, m);
		float scale = Math.abs(m.get(0));
		float gui = new net.minecraft.client.gui.ScaledResolution(net.minecraft.client.Minecraft.getMinecraft()).getScaleFactor();
		return 1.0F / Math.max(0.01F, scale * gui);
	}

	public static void roundedRect(float x, float y, float w, float h, float r, int color) {
		shape(x, y, w, h, r, 0, color);
	}

	/** A soft shadow: the shape blurred over [blur] units. */
	public static void shadow(float x, float y, float w, float h, float r, float blur, int color) {
		shape(x, y, w, h, r, blur, color);
	}

	/** A rounded outline: an outer card in [border] with an inner card in [fill]. */
	public static void card(float x, float y, float w, float h, float r, int fill, int border) {
		roundedRect(x - 1, y - 1, w + 2, h + 2, r + 1, border);
		roundedRect(x, y, w, h, r, fill);
	}

	public static void circle(float cx, float cy, float r, int color) {
		roundedRect(cx - r, cy - r, r * 2, r * 2, r, color);
	}

	private static void shape(float x, float y, float w, float h, float r, float soft, int color) {
		if (w <= 0 || h <= 0 || (color >>> 24) == 0) {
			return;
		}
		r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
		GlStateManager.enableBlend();
		GlStateManager.disableTexture2D();
		GlStateManager.disableCull();
		GlStateManager.disableAlpha();
		GlStateManager.tryBlendFuncSeparate(770, 771, 1, 771);
		float pad = soft + 1;
		Tessellator tess = Tessellator.getInstance();
		WorldRenderer wr = tess.getWorldRenderer();
		if (shader()) {
			GL20.glUseProgram(program);
			GL20.glUniform2f(uSize, w, h);
			GL20.glUniform1f(uRadius, r);
			GL20.glUniform1f(uSoft, soft);
			GL20.glUniform1f(uPixel, pixel());
			GL20.glUniform4f(uColor, (color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F, (color >>> 24) / 255F);
			wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
			wr.pos(x - pad, y + h + pad, 0).tex(-pad, h + pad).endVertex();
			wr.pos(x + w + pad, y + h + pad, 0).tex(w + pad, h + pad).endVertex();
			wr.pos(x + w + pad, y - pad, 0).tex(w + pad, -pad).endVertex();
			wr.pos(x - pad, y - pad, 0).tex(-pad, -pad).endVertex();
			tess.draw();
			GL20.glUseProgram(0);
		} else {
			GlStateManager.color((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F, (color >>> 24) / 255F);
			wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
			wr.pos(x, y + h, 0).endVertex();
			wr.pos(x + w, y + h, 0).endVertex();
			wr.pos(x + w, y, 0).endVertex();
			wr.pos(x, y, 0).endVertex();
			tess.draw();
		}
		GlStateManager.enableAlpha();
		GlStateManager.enableTexture2D();
		GlStateManager.enableCull();
		GlStateManager.disableBlend();
		GlStateManager.color(1, 1, 1, 1);
	}

	/** Mixes two colors; t = 0 gives [a], 1 gives [b]. */
	public static int lerp(int a, int b, float t) {
		t = Math.max(0, Math.min(1, t));
		int out = 0;
		for (int shift = 0; shift <= 24; shift += 8) {
			int ca = a >>> shift & 255;
			int cb = b >>> shift & 255;
			out |= Math.round(ca + (cb - ca) * t) << shift;
		}
		return out;
	}
}
