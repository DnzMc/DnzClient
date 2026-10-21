package com.dnz.client.hud;

import com.dnz.client.DnzConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.awt.Color;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.world.phys.AABB;

/**
 * Module "blockoutline": the frame around the block the player looks at, with its own color (or rainbow),
 * opacity, line width and an optional see-through fill.
 */
public final class BlockOutline {
	private static final float INFLATE = 0.002F;

	private BlockOutline() {
	}

	/** Draws the outline when the module is on; true = done (vanilla's is skipped). */
	public static boolean submit(PoseStack pose, SubmitNodeCollector collector, RenderType type, BlockOutlineRenderState state,
		float width, boolean translucent) {
		HudModule module = DnzHud.module("blockoutline");
		if (module == null || !module.enabled() || state.highContrast()) {
			return false;
		}
		DnzConfig.BlockOutline c = DnzConfig.get().blockOutline;
		int rgb = c.chroma ? Color.HSBtoRGB((System.currentTimeMillis() % 4000L) / 4000.0F, 0.8F, 1.0F) & 0xFFFFFF : c.color & 0xFFFFFF;
		if (c.fill && c.fillOpacity > 0) {
			int fill = Math.round(c.fillOpacity * 2.55F) << 24 | rgb;
			var boxes = state.shape().toAabbs();
			collector.submitCustomGeometry(pose, RenderTypes.debugQuads(), (p, vc) -> {
				for (AABB box : boxes) {
					box(p, vc, box, fill);
				}
			});
		}
		if (c.opacity > 0) {
			int line = Math.round(c.opacity * 2.55F) << 24 | rgb;
			collector.submitShapeOutline(pose, state.shape(), type, line, width * c.thickness / 2.0F, translucent);
		}
		return true;
	}

	private static void box(PoseStack.Pose p, VertexConsumer vc, AABB b, int color) {
		float x0 = (float) b.minX - INFLATE, y0 = (float) b.minY - INFLATE, z0 = (float) b.minZ - INFLATE;
		float x1 = (float) b.maxX + INFLATE, y1 = (float) b.maxY + INFLATE, z1 = (float) b.maxZ + INFLATE;
		quad(p, vc, color, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1); // bottom
		quad(p, vc, color, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0); // top
		quad(p, vc, color, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0); // north
		quad(p, vc, color, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1); // south
		quad(p, vc, color, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0); // west
		quad(p, vc, color, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1); // east
	}

	private static void quad(PoseStack.Pose p, VertexConsumer vc, int color, float ax, float ay, float az, float bx, float by, float bz,
		float cx, float cy, float cz, float dx, float dy, float dz) {
		vc.addVertex(p, ax, ay, az).setColor(color);
		vc.addVertex(p, bx, by, bz).setColor(color);
		vc.addVertex(p, cx, cy, cz).setColor(color);
		vc.addVertex(p, dx, dy, dz).setColor(color);
	}
}
