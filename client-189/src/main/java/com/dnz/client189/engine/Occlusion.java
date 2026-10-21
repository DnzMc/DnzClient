package com.dnz.client189.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityBanner;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.tileentity.TileEntitySkull;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;

/**
 * DNZ Engine, entity culling: mobs, dropped items, armor stands, chests, signs, heads and banners hidden
 * behind solid blocks are not drawn.
 */
public final class Occlusion {
	private static final double RANGE = 128;

	private static volatile Set<Integer> hiddenEntities = Collections.emptySet();
	private static volatile Set<Long> hiddenTiles = Collections.emptySet();

	private static volatile Snapshot latest;
	private static Thread thread;
	public static volatile int lastHiddenEntities;
	public static volatile int lastHiddenTiles;

	private static final class Target {
		final int id;
		final long pos;
		final double minX, minY, minZ, maxX, maxY, maxZ;

		Target(int id, long pos, AxisAlignedBB box) {
			this.id = id;
			this.pos = pos;
			this.minX = box.minX - 0.05;
			this.minY = box.minY - 0.05;
			this.minZ = box.minZ - 0.05;
			this.maxX = box.maxX + 0.05;
			this.maxY = box.maxY + 0.05;
			this.maxZ = box.maxZ + 0.05;
		}
	}

	private static final class Snapshot {
		World world;
		double x, y, z;
		List<Target> entities = new ArrayList<Target>();
		List<Target> tiles = new ArrayList<Target>();
	}

	private Occlusion() {
	}

	public static boolean hidden(Entity entity) {
		return Engine.enabled() && hiddenEntities.contains(entity.getEntityId());
	}

	public static boolean hidden(TileEntity te) {
		return Engine.enabled() && hiddenTiles.contains(te.getPos().toLong());
	}

	/** Game thread, every client tick: what the camera sees now, handed to the culling thread. */
	public static void tick(Minecraft mc) {
		Entity camera = mc.getRenderViewEntity();
		if (!Engine.enabled() || mc.theWorld == null || camera == null) {
			latest = null;
			hiddenEntities = Collections.emptySet();
			hiddenTiles = Collections.emptySet();
			return;
		}
		Snapshot s = new Snapshot();
		s.world = mc.theWorld;
		s.x = camera.posX;
		s.y = camera.posY + camera.getEyeHeight();
		s.z = camera.posZ;
		for (Entity e : mc.theWorld.loadedEntityList) {
			if (e == camera || e instanceof EntityPlayer || e.riddenByEntity != null || e.ridingEntity != null) {
				continue;
			}
			if (e.getDistanceSq(s.x, s.y, s.z) < RANGE * RANGE) {
				s.entities.add(new Target(e.getEntityId(), 0, e.getEntityBoundingBox()));
			}
		}
		for (TileEntity te : mc.theWorld.loadedTileEntityList) {
			if (te instanceof TileEntityChest || te instanceof TileEntityEnderChest || te instanceof TileEntitySign
				|| te instanceof TileEntitySkull || te instanceof TileEntityBanner) {
				BlockPos p = te.getPos();
				if (p.distanceSqToCenter(s.x, s.y, s.z) < RANGE * RANGE) {
					s.tiles.add(new Target(0, p.toLong(), new AxisAlignedBB(p, p.add(1, 1, 1))));
				}
			}
		}
		latest = s;
		if (thread == null) {
			thread = new Thread(Occlusion::loop, "DNZ Engine culling");
			thread.setDaemon(true);
			thread.setPriority(Thread.NORM_PRIORITY - 1);
			thread.start();
		}
	}

	private static void loop() {
		while (true) {
			Snapshot s = latest;
			if (s != null) {
				try {
					Set<Integer> entities = new HashSet<Integer>();
					for (Target t : s.entities) {
						if (!visible(s, t)) {
							entities.add(t.id);
						}
					}
					Set<Long> tiles = new HashSet<Long>();
					for (Target t : s.tiles) {
						if (!visible(s, t)) {
							tiles.add(t.pos);
						}
					}
					hiddenEntities = entities;
					hiddenTiles = tiles;
					lastHiddenEntities = entities.size();
					lastHiddenTiles = tiles.size();
				} catch (Exception ignored) {
					// the world changed under us (dimension change, chunk unloaded): next round
				}
			}
			try {
				Thread.sleep(25);
			} catch (InterruptedException e) {
				return;
			}
		}
	}

	private static boolean visible(Snapshot s, Target t) {
		if (s.x >= t.minX && s.x <= t.maxX && s.y >= t.minY && s.y <= t.maxY && s.z >= t.minZ && s.z <= t.maxZ) {
			return true;
		}
		if (clear(s, (t.minX + t.maxX) / 2, (t.minY + t.maxY) / 2, (t.minZ + t.maxZ) / 2)) {
			return true;
		}
		for (int i = 0; i < 8; i++) {
			double x = (i & 1) == 0 ? t.minX : t.maxX;
			double y = (i & 2) == 0 ? t.minY : t.maxY;
			double z = (i & 4) == 0 ? t.minZ : t.maxZ;
			if (clear(s, x, y, z)) {
				return true;
			}
		}
		return false;
	}

	private static boolean clear(Snapshot s, double tx, double ty, double tz) {
		double dx = tx - s.x;
		double dy = ty - s.y;
		double dz = tz - s.z;
		int x = floor(s.x);
		int y = floor(s.y);
		int z = floor(s.z);
		int endX = floor(tx);
		int endY = floor(ty);
		int endZ = floor(tz);
		int stepX = dx > 0 ? 1 : -1;
		int stepY = dy > 0 ? 1 : -1;
		int stepZ = dz > 0 ? 1 : -1;
		double deltaX = dx == 0 ? Double.MAX_VALUE : Math.abs(1 / dx);
		double deltaY = dy == 0 ? Double.MAX_VALUE : Math.abs(1 / dy);
		double deltaZ = dz == 0 ? Double.MAX_VALUE : Math.abs(1 / dz);
		double maxX = dx == 0 ? Double.MAX_VALUE : deltaX * (dx > 0 ? (x + 1 - s.x) : (s.x - x));
		double maxY = dy == 0 ? Double.MAX_VALUE : deltaY * (dy > 0 ? (y + 1 - s.y) : (s.y - y));
		double maxZ = dz == 0 ? Double.MAX_VALUE : deltaZ * (dz > 0 ? (z + 1 - s.z) : (s.z - z));
		for (int steps = 0; steps < 300; steps++) {
			// the next block border is past the target: nothing solid on the way
			if (Math.min(Math.min(maxX, maxY), maxZ) > 1) {
				return true;
			}
			if (maxX < maxY && maxX < maxZ) {
				x += stepX;
				maxX += deltaX;
			} else if (maxY < maxZ) {
				y += stepY;
				maxY += deltaY;
			} else {
				z += stepZ;
				maxZ += deltaZ;
			}
			if (x == endX && y == endY && z == endZ) {
				return true;
			}
			Block block = s.world.getBlockState(new BlockPos(x, y, z)).getBlock();
			if (block.isOpaqueCube()) {
				return false;
			}
		}
		return true;
	}

	private static int floor(double v) {
		int i = (int) v;
		return v < i ? i - 1 : i;
	}
}
