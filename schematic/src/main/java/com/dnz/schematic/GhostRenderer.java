package com.dnz.schematic;

import com.dnz.schematic.litematic.Litematic;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

/**
 * Draws the schematic into the world: missing blocks as see-through blocks, mistakes as colored boxes
 * (red = wrong block, yellow = wrong direction/state, orange = extra block).
 * What to draw is worked out a few times per second; every frame only sends the ready list to the GPU.
 */
public final class GhostRenderer {
	private static final int MAX_GHOSTS = 8000;
	private static final int MAX_BOXES = 6000;
	private static final Direction[] DIRECTIONS = Direction.values();

	public static final int RED = 0xFF3B30;
	public static final int YELLOW = 0xFFD60A;
	public static final int ORANGE = 0xFF8C1A;
	public static final int BLUE = 0x4FA3FF;

	/** What the last rebuild found; replaced as a whole, never changed, so a frame always sees one consistent list. */
	private record Snapshot(int ghosts, int[] gx, int[] gy, int[] gz, BlockState[] gState, byte[] gCull,
							int boxes, int[] bx, int[] by, int[] bz, int[] bColor) {
	}

	private static volatile Snapshot current;
	private static int lastVersion = -1;
	private static int ticks;

	/** Model faces per block state: index 0-5 = the six sides (hidden when covered), 6 = always drawn. */
	private static final Map<BlockState, BakedQuad[][]> QUADS = new HashMap<>();
	private static BlockStateModelSet quadsFrom;

	private GhostRenderer() {
	}

	public static void register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(GhostRenderer::submit);
	}

	public static void tick(Minecraft mc) {
		Placement p = Schematics.placement();
		Checker checker = Schematics.checker();
		SchematicConfig config = SchematicConfig.get();
		if (p == null || checker == null || mc.level == null || !config.visible) {
			current = null;
			return;
		}
		// At once after moving/turning the schematic; otherwise only when a block's result changed or the camera
		// moved a couple of blocks, and at most 4 times a second. Nothing to draw = no work at all.
		boolean moved = p.version() != lastVersion;
		++ticks;
		BlockPos camBlock = mc.gameRenderer.mainCamera().blockPosition();
		boolean changed = checker.changes() != lastChanges || camBlock.distManhattan(lastCam) >= 3
			|| config.layerMode != lastLayerMode || config.layer != lastLayer;
		if (!moved && (!changed || ticks - lastBuild < 5)) {
			return;
		}
		lastVersion = p.version();
		lastChanges = checker.changes();
		lastCam = camBlock;
		lastLayerMode = config.layerMode;
		lastLayer = config.layer;
		lastBuild = ticks;
		current = checker.hasErrors() ? build(mc, p, checker, config) : null;
	}

	private static int lastChanges = -1;
	private static BlockPos lastCam = BlockPos.ZERO;
	private static int lastLayerMode = -1;
	private static int lastLayer = -1;
	private static int lastBuild;

	private static Snapshot build(Minecraft mc, Placement p, Checker checker, SchematicConfig config) {
		Litematic schem = p.schematic;
		Vec3 cam = mc.gameRenderer.mainCamera().position();
		int cx = (int) Math.floor(cam.x), cy = (int) Math.floor(cam.y), cz = (int) Math.floor(cam.z);
		int r = Math.clamp(config.range, 8, 96);
		BlockPos min = p.min(), max = p.max();
		int x0 = Math.max(min.getX(), cx - r), x1 = Math.min(max.getX(), cx + r);
		int y0 = Math.max(min.getY(), cy - r), y1 = Math.min(max.getY(), cy + r);
		int z0 = Math.max(min.getZ(), cz - r), z1 = Math.min(max.getZ(), cz + r);

		// Sort key: distance, kind (0 ghost, 1-3 box colors), index.
		LongArrayList found = new LongArrayList();
		for (int y = y0; y <= y1; y++) {
			if (!Schematics.layerVisible(y - min.getY())) {
				continue;
			}
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					int i = p.toIndex(x, y, z);
					if (i < 0) {
						continue;
					}
					int kind = switch (checker.status(i)) {
						case Checker.MISSING -> config.showMissing ? 0 : -1;
						case Checker.WRONG_BLOCK -> config.showWrong ? 1 : -1;
						case Checker.WRONG_STATE -> config.showWrong ? 2 : -1;
						case Checker.EXTRA -> config.showExtra ? 3 : -1;
						default -> -1;
					};
					if (kind < 0) {
						continue;
					}
					long dx = x - cx, dy = y - cy, dz = z - cz;
					long d = Math.min(dx * dx + dy * dy + dz * dz, 0xFFFFFL);
					found.add(d << 32 | (long) kind << 28 | i);
				}
			}
		}
		if (found.size() > MAX_GHOSTS) {
			found.sort(null);
		}

		int[] gx = new int[Math.min(found.size(), MAX_GHOSTS)], gy = new int[gx.length], gz = new int[gx.length];
		BlockState[] gState = new BlockState[gx.length];
		byte[] gCull = new byte[gx.length];
		int[] bx = new int[Math.min(found.size(), MAX_BOXES)], by = new int[bx.length], bz = new int[bx.length], bColor = new int[bx.length];
		int ghosts = 0, boxes = 0;
		int[] xz = new int[2];
		BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
		for (int k = 0; k < found.size(); k++) {
			long key = found.getLong(k);
			int kind = (int) (key >>> 28) & 0xF;
			int i = (int) (key & 0xFFFFFFF);
			p.toWorldXZ(schem.xOf(i), schem.zOf(i), xz);
			int wy = p.worldY(i);
			if (kind == 0) {
				if (ghosts >= gx.length) {
					continue;
				}
				BlockState state = p.placedState(schem.paletteAt(i));
				gx[ghosts] = xz[0];
				gy[ghosts] = wy;
				gz[ghosts] = xz[1];
				gState[ghosts] = state;
				// Sides touching a solid block (real or also missing) can't be seen: skip them.
				byte cull = 0;
				for (Direction d : DIRECTIONS) {
					n.set(xz[0] + d.getStepX(), wy + d.getStepY(), xz[1] + d.getStepZ());
					boolean hidden = mc.level.getBlockState(n).isSolidRender();
					if (!hidden) {
						int ni = p.toIndex(n.getX(), n.getY(), n.getZ());
						hidden = ni >= 0 && checker.status(ni) == Checker.MISSING && Schematics.layerVisible(n.getY() - p.min().getY())
							&& p.placedState(schem.paletteAt(ni)).isSolidRender();
					}
					if (hidden) {
						cull |= (byte) (1 << d.ordinal());
					}
				}
				gCull[ghosts++] = cull;
			} else {
				if (boxes >= bx.length) {
					continue;
				}
				bx[boxes] = xz[0];
				by[boxes] = wy;
				bz[boxes] = xz[1];
				bColor[boxes++] = kind == 1 ? RED : kind == 2 ? YELLOW : ORANGE;
			}
		}
		return new Snapshot(ghosts, gx, gy, gz, gState, gCull, boxes, bx, by, bz, bColor);
	}

	private static void submit(LevelRenderContext ctx) {
		Snapshot s = current;
		if (s == null || (s.ghosts == 0 && s.boxes == 0 && Schematics.nearestError() == null)) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Vec3 cam = ctx.levelState().cameraRenderState.pos;
		PoseStack poseStack = ctx.poseStack();
		SchematicConfig config = SchematicConfig.get();

		if (s.ghosts > 0) {
			int alpha = Math.clamp(config.ghostAlpha, 10, 100) * 255 / 100;
			BlockStateModelSet models = mc.getModelManager().getBlockStateModelSet();
			if (models != quadsFrom) {
				QUADS.clear(); // resource packs changed
				quadsFrom = models;
			}
			ctx.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.translucentMovingBlock(), (pose, vc) -> {
				for (int k = 0; k < s.ghosts; k++) {
					BlockState state = s.gState[k];
					BakedQuad[][] quads = quads(models, state);
					float ox = (float) (s.gx[k] - cam.x), oy = (float) (s.gy[k] - cam.y), oz = (float) (s.gz[k] - cam.z);
					for (int side = 0; side < 7; side++) {
						if (side < 6 && (s.gCull[k] & (1 << side)) != 0) {
							continue;
						}
						for (BakedQuad quad : quads[side]) {
							ghostQuad(pose, vc, mc, state, quad, ox, oy, oz, alpha);
						}
					}
				}
			});
		}

		BlockPos nearest = Schematics.nearestError();
		boolean hasBoxes = s.boxes > 0 || hasModellessGhosts(s, mc);
		if (hasBoxes || nearest != null) {
			ctx.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, vc) -> {
				for (int k = 0; k < s.boxes; k++) {
					box(pose, vc, s.bx[k] - cam.x, s.by[k] - cam.y, s.bz[k] - cam.z, 0.004, s.bColor[k], 0x70);
				}
				// Blocks drawn by special renderers (chests, signs, water...) have no model: show a blue box instead.
				BlockStateModelSet models = mc.getModelManager().getBlockStateModelSet();
				for (int k = 0; k < s.ghosts; k++) {
					if (isEmpty(quads(models, s.gState[k]))) {
						box(pose, vc, s.gx[k] - cam.x, s.gy[k] - cam.y, s.gz[k] - cam.z, 0.004, BLUE, 0x50);
					}
				}
				if (nearest != null) {
					// The closest mistake pulses, so it is easy to find.
					int pulse = 0x40 + (int) (0x50 * (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 180.0)));
					box(pose, vc, nearest.getX() - cam.x, nearest.getY() - cam.y, nearest.getZ() - cam.z, 0.03, 0xFFFFFF, pulse);
				}
			});
		}
	}

	private static boolean hasModellessGhosts(Snapshot s, Minecraft mc) {
		BlockStateModelSet models = mc.getModelManager().getBlockStateModelSet();
		for (int k = 0; k < s.ghosts; k++) {
			if (isEmpty(quads(models, s.gState[k]))) {
				return true;
			}
		}
		return false;
	}

	private static boolean isEmpty(BakedQuad[][] quads) {
		for (BakedQuad[] side : quads) {
			if (side.length > 0) {
				return false;
			}
		}
		return true;
	}

	private static BakedQuad[][] quads(BlockStateModelSet models, BlockState state) {
		return QUADS.computeIfAbsent(state, st -> {
			List<BlockStateModelPart> parts = new ArrayList<>();
			models.get(st).collectParts(RandomSource.create(42L), parts);
			BakedQuad[][] out = new BakedQuad[7][];
			for (int side = 0; side < 7; side++) {
				Direction dir = side < 6 ? DIRECTIONS[side] : null;
				List<BakedQuad> list = new ArrayList<>();
				for (BlockStateModelPart part : parts) {
					list.addAll(part.getQuads(dir));
				}
				out[side] = list.toArray(new BakedQuad[0]);
			}
			return out;
		});
	}

	private static void ghostQuad(PoseStack.Pose pose, VertexConsumer vc, Minecraft mc, BlockState state, BakedQuad quad,
								  float ox, float oy, float oz, int alpha) {
		int rgb = 0xFFFFFF;
		if (quad.materialInfo().isTinted()) {
			BlockTintSource tint = mc.getBlockColors().getTintSource(state, quad.materialInfo().tintIndex());
			if (tint != null) {
				rgb = tint.color(state) & 0xFFFFFF;
			}
		}
		Direction dir = quad.direction();
		// Simple side shading so the shape is readable, and a light blue touch so ghosts never look like real blocks.
		float shade = switch (dir) {
			case DOWN -> 0.55F;
			case NORTH, SOUTH -> 0.8F;
			case EAST, WEST -> 0.65F;
			default -> 1.0F;
		};
		int r = (int) (((rgb >> 16) & 0xFF) * shade * 0.85F);
		int g = (int) (((rgb >> 8) & 0xFF) * shade * 0.92F);
		int b = (int) ((rgb & 0xFF) * shade);
		int color = alpha << 24 | r << 16 | g << 8 | b;
		float nx = dir.getStepX(), ny = dir.getStepY(), nz = dir.getStepZ();
		for (int v = 0; v < 4; v++) {
			Vector3fc p = quad.position(v);
			long uv = quad.packedUV(v);
			vc.addVertex(pose, ox + p.x(), oy + p.y(), oz + p.z())
				.setColor(color)
				.setUv(UVPair.unpackU(uv), UVPair.unpackV(uv))
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LightCoordsUtil.FULL_BRIGHT)
				.setNormal(pose, nx, ny, nz);
		}
	}

	/** A see-through colored cube around one block. */
	private static void box(PoseStack.Pose pose, VertexConsumer vc, double x, double y, double z, double grow, int rgb, int alpha) {
		float x0 = (float) (x - grow), y0 = (float) (y - grow), z0 = (float) (z - grow);
		float x1 = (float) (x + 1 + grow), y1 = (float) (y + 1 + grow), z1 = (float) (z + 1 + grow);
		int c = alpha << 24 | (rgb & 0xFFFFFF);
		// down, up, north, south, west, east
		quad(pose, vc, c, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
		quad(pose, vc, c, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
		quad(pose, vc, c, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
		quad(pose, vc, c, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
		quad(pose, vc, c, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
		quad(pose, vc, c, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer vc, int color, float ax, float ay, float az, float bx, float by, float bz,
							 float cx, float cy, float cz, float dx, float dy, float dz) {
		vc.addVertex(pose, ax, ay, az).setColor(color);
		vc.addVertex(pose, bx, by, bz).setColor(color);
		vc.addVertex(pose, cx, cy, cz).setColor(color);
		vc.addVertex(pose, dx, dy, dz).setColor(color);
	}
}
