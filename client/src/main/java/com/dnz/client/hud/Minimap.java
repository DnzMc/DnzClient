package com.dnz.client.hud;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * Minimap (module "minimap"): the land around the player seen from above in map colors, redrawn twice a second
 * (cheap: one block lookup per pixel), with the player arrow, other players and monsters as dots and the
 * direction letters. North is up.
 */
final class Minimap extends HudModule {
	private static final int SIZE = 64; // blocks across = texture pixels
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("dnzclient", "minimap");
	private DynamicTexture texture;
	private long updatedAt;
	private int centerX;
	private int centerZ;

	Minimap() {
		super("minimap", Category.INFO, 0.86F, 0.01F, false);
	}

	@Override
	public java.util.List<String> options() {
		return java.util.List.of("entities", "coords");
	}

	@Override
	public int[] render(HudRender r) {
		int box = 80;
		boolean coords = this.opt("coords");
		int h = box + (coords ? 11 : 0);
		if (r.measure) {
			return new int[] {box, h};
		}
		Minecraft mc = r.mc;
		if (mc.level == null || mc.player == null) {
			r.panel(box, h);
			r.centered("Minimap", box / 2, box / 2 - 4, 0xFFAAB0C0);
			return new int[] {box, h};
		}
		this.update(mc);
		r.panel(box, h);
		int m = 3, inner = box - 2 * m;
		r.g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, m, m, 0, 0, inner, inner, SIZE, SIZE, SIZE, SIZE);
		float perBlock = inner / (float) SIZE;
		double px = mc.player.getX(), pz = mc.player.getZ();
		if (this.opt("entities")) {
			for (Entity e : mc.level.entitiesForRendering()) {
				if (e == mc.player || !(e instanceof Player || e instanceof Enemy)) {
					continue;
				}
				float dx = (float) (e.getX() - (this.centerX + 0.5)) * perBlock, dz = (float) (e.getZ() - (this.centerZ + 0.5)) * perBlock;
				if (Math.abs(dx) > inner / 2.0F - 2 || Math.abs(dz) > inner / 2.0F - 2) {
					continue;
				}
				int x = Math.round(m + inner / 2.0F + dx), y = Math.round(m + inner / 2.0F + dz);
				r.fill(x - 1, y - 1, x + 1, y + 1, e instanceof Player ? 0xFFFFFFFF : 0xFFFF5555);
			}
		}
		// The player: a small arrow pointing where they look.
		float cx = m + inner / 2.0F + (float) (px - (this.centerX + 0.5)) * perBlock;
		float cy = m + inner / 2.0F + (float) (pz - (this.centerZ + 0.5)) * perBlock;
		r.g.pose().pushMatrix();
		r.g.pose().translate(cx, cy);
		r.g.pose().rotate((float) Math.toRadians(mc.player.getYRot() + 180));
		r.fill(-1, -3, 1, 3, 0xFF000000);
		r.fill(-2, -1, 2, 1, 0xFF000000);
		r.fill(0, -3, 1, 2, 0xFFFFD24A);
		r.fill(-1, -2, 0, 2, 0xFFFFD24A);
		r.g.pose().popMatrix();
		r.text("N", box / 2 - 2, m + 1, 0xFFFFFFFF);
		if (coords) {
			BlockPos p = mc.player.blockPosition();
			r.centered(p.getX() + " " + p.getY() + " " + p.getZ(), box / 2, box + 1, 0xFFFFFFFF);
		}
		return new int[] {box, h};
	}

	/** Redraws the map picture around the player (at most twice a second). */
	private void update(Minecraft mc) {
		long now = System.currentTimeMillis();
		if (this.texture == null) {
			this.texture = new DynamicTexture(() -> "dnz minimap", SIZE, SIZE, false);
			mc.getTextureManager().register(TEXTURE, this.texture);
		} else if (now - this.updatedAt < 500) {
			return;
		}
		this.updatedAt = now;
		Level level = mc.level;
		this.centerX = mc.player.getBlockX();
		this.centerZ = mc.player.getBlockZ();
		NativeImage img = this.texture.getPixels();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		boolean cave = !level.dimensionType().hasCeiling() ? false : true;
		int playerY = mc.player.getBlockY();
		for (int z = 0; z < SIZE; z++) {
			for (int x = 0; x < SIZE; x++) {
				int wx = this.centerX - SIZE / 2 + x, wz = this.centerZ - SIZE / 2 + z;
				int color = 0xFF000000;
				if (level.hasChunk(wx >> 4, wz >> 4)) {
					int y = cave ? playerY + 1 : level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
					pos.set(wx, y, wz);
					BlockState state = level.getBlockState(pos);
					// In the Nether (a roof) look down from the player's height instead of the top.
					if (cave) {
						for (int i = 0; i < 24 && state.isAir(); i++) {
							pos.move(0, -1, 0);
							state = level.getBlockState(pos);
						}
					}
					MapColor mapColor = state.getMapColor(level, pos);
					if (mapColor != MapColor.NONE) {
						int north = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz - 1) - 1;
						MapColor.Brightness b = cave || north == y ? MapColor.Brightness.NORMAL : north < y ? MapColor.Brightness.HIGH : MapColor.Brightness.LOW;
						color = mapColor.calculateARGBColor(b);
					}
				}
				img.setPixel(x, z, color);
			}
		}
		this.texture.upload();
	}
}
