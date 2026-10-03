package com.dnz.client.gui;

import com.dnz.client.mixin.GuiGraphicsAccessor;
import com.dnz.client.mixin.RenderPipelinesAccessor;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

/**
 * Smooth interface shapes drawn by DNZ's own shader (assets/dnzclient/shaders/core/rounded): rounded rectangles
 * with soft, anti-aliased corners at any GUI scale, outlines, gradients and blurred shadows. One quad per shape,
 * batched with the rest of the interface, so they cost about as much as a plain rectangle.
 */
public final class Smooth {
	/** Minecraft's interface pipeline with DNZ's shader and a vertex format that carries the shape. */
	public static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelinesAccessor.dnz$guiSnippet())
		.withLocation(Identifier.fromNamespaceAndPath("dnzclient", "pipeline/rounded"))
		.withVertexShader(Identifier.fromNamespaceAndPath("dnzclient", "core/rounded"))
		.withFragmentShader(Identifier.fromNamespaceAndPath("dnzclient", "core/rounded"))
		.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR)
		.build();

	private Smooth() {
	}

	/** A filled rounded rectangle; [radius] in GUI pixels. */
	public static void rect(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int color) {
		gradient(g, x, y, w, h, radius, color, color, false);
	}

	/** Rounded rectangle with a color gradient, top to bottom (or left to right when [horizontal]). */
	public static void gradient(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int from, int to, boolean horizontal) {
		add(g, x, y, w, h, radius, 0, from, to, horizontal);
	}

	/** A soft shadow around a rectangle: [blur] GUI pixels of fade on every side. */
	public static void shadow(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, float blur, int color) {
		add(g, x - blur, y - blur, w + 2 * blur, h + 2 * blur, radius + blur, blur, color, color, false);
	}

	private static void add(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, float softness, int from, int to, boolean horizontal) {
		if (w <= 0 || h <= 0 || ((from | to) >>> 24) == 0) {
			return;
		}
		from = Palette.map(from);
		to = Palette.map(to);
		GuiGraphicsAccessor access = (GuiGraphicsAccessor) g;
		float shorter = Math.min(w, h) / 2.0F;
		int r = Math.round(Math.max(0, Math.min(1, radius / shorter)) * 32767);
		int soft = Math.round(Math.max(0, Math.min(1, softness / shorter)) * 32767);
		ScreenRectangle scissor = access.dnz$scissor().peek();
		access.dnz$state().addGuiElement(new Shape(new Matrix3x2f(g.pose()), x, y, w, h, r, soft, from, to, horizontal, scissor));
	}

	/** One smooth shape as part of the interface's frame. */
	private record Shape(Matrix3x2fc pose, float x, float y, float w, float h, int radius, int softness, int from, int to,
						 boolean horizontal, ScreenRectangle scissorArea, ScreenRectangle bounds) implements GuiElementRenderState {
		Shape(Matrix3x2fc pose, float x, float y, float w, float h, int radius, int softness, int from, int to, boolean horizontal, ScreenRectangle scissor) {
			this(pose, x, y, w, h, radius, softness, from, to, horizontal, scissor, bounds(pose, x, y, w, h, scissor));
		}

		private static ScreenRectangle bounds(Matrix3x2fc pose, float x, float y, float w, float h, ScreenRectangle scissor) {
			ScreenRectangle r = new ScreenRectangle((int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(w) + 1, (int) Math.ceil(h) + 1)
				.transformMaxBounds(pose);
			return scissor != null ? scissor.intersection(r) : r;
		}

		@Override
		public void buildVertices(VertexConsumer v) {
			int topLeft = this.from;
			int topRight = this.horizontal ? this.to : this.from;
			int bottomLeft = this.horizontal ? this.from : this.to;
			int bottomRight = this.to;
			v.addVertexWith2DPose(this.pose, this.x, this.y).setUv(-1, -1).setUv2(this.radius, this.softness).setColor(topLeft);
			v.addVertexWith2DPose(this.pose, this.x, this.y + this.h).setUv(-1, 1).setUv2(this.radius, this.softness).setColor(bottomLeft);
			v.addVertexWith2DPose(this.pose, this.x + this.w, this.y + this.h).setUv(1, 1).setUv2(this.radius, this.softness).setColor(bottomRight);
			v.addVertexWith2DPose(this.pose, this.x + this.w, this.y).setUv(1, -1).setUv2(this.radius, this.softness).setColor(topRight);
		}

		@Override
		public RenderPipeline pipeline() {
			return PIPELINE;
		}

		@Override
		public TextureSetup textureSetup() {
			return TextureSetup.noTexture();
		}
	}
}
