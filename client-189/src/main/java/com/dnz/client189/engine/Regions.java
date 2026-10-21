package com.dnz.client189.engine;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumWorldBlockLayer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GLContext;

/**
 * DNZ Engine, region rendering (the big one): Minecraft 1.8.9 draws every 16x16x16 chunk piece on its own
 * (bind, set up, move, draw) - thousands of separate draws per frame that keep the processor busy.
 */
public final class Regions {
	private static final int STRIDE = 28;
	private static final int PACKED = 20;
	private static ByteBuffer packed = BufferUtils.createByteBuffer(1 << 20);
	private static final int AREA_SHIFT = 7; // 128 blocks = 8 chunks
	private static final int LAYERS = EnumWorldBlockLayer.values().length;

	/** True while the world's chunks go through DNZ's buffers (decided when the renderers are (re)loaded). */
	public static boolean active;

	private static final Map<Long, Region> REGIONS = new HashMap<Long, Region>();
	private static final Map<RenderChunk, Slot[]> SLOTS = new IdentityHashMap<RenderChunk, Slot[]>();
	private static ByteBuffer scratch = BufferUtils.createByteBuffer(1 << 20);
	private static IntBuffer firsts = BufferUtils.createIntBuffer(4096);
	private static IntBuffer counts = BufferUtils.createIntBuffer(4096);

	private static final class Slot {
		final Region region;
		final int layer;
		final int first;
		final int size;
		/** Start of each face group inside the slot (vertices, relative), plus the end; see FACES. */
		final int[] faces;
		final float minX;
		final float minY;
		final float minZ;
		/** Animated textures used by this piece (see AnimatedTextures). */
		long animated;

		Slot(Region region, int layer, int first, int size, int[] faces, float minX, float minY, float minZ) {
			this.region = region;
			this.layer = layer;
			this.first = first;
			this.size = size;
			this.faces = faces;
			this.minX = minX;
			this.minY = minY;
			this.minZ = minZ;
		}
	}

	private static final int FACES = 7;
	private static final int ANY = 0;
	private static final int DOWN = 1;
	private static final int UP = 2;
	private static final int NORTH = 3;
	private static final int SOUTH = 4;
	private static final int WEST = 5;
	private static final int EAST = 6;
	private static ByteBuffer sorted = BufferUtils.createByteBuffer(1 << 20);

	private static final class Region {
		final int originX;
		final int originZ;
		final Arena[] arenas = new Arena[LAYERS];
		int pieces;

		Region(int originX, int originZ) {
			this.originX = originX;
			this.originZ = originZ;
		}

		Arena arena(int layer) {
			if (this.arenas[layer] == null) {
				this.arenas[layer] = new Arena();
			}
			return this.arenas[layer];
		}
	}

	private static final class Arena {
		int vbo = GL15.glGenBuffers();
		int capacity;
		/** Free ranges: start -> length. */
		final TreeMap<Integer, Integer> free = new TreeMap<Integer, Integer>();

		int allocate(int size) {
			for (Map.Entry<Integer, Integer> e : this.free.entrySet()) {
				if (e.getValue() >= size) {
					int start = e.getKey();
					int left = e.getValue() - size;
					this.free.remove(start);
					if (left > 0) {
						this.free.put(start + size, left);
					}
					return start;
				}
			}
			int old = this.capacity;
			int grown = Math.max(Math.max(4096, old * 2), old + size);
			int nvbo = GL15.glGenBuffers();
			GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, nvbo);
			GL15.glBufferData(GL31.GL_COPY_WRITE_BUFFER, (long) grown * PACKED, GL15.GL_STATIC_DRAW);
			if (old > 0) {
				GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, this.vbo);
				GL31.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_COPY_WRITE_BUFFER, 0, 0, (long) old * PACKED);
				GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, 0);
			}
			GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, 0);
			GL15.glDeleteBuffers(this.vbo);
			this.vbo = nvbo;
			this.capacity = grown;
			// the new space after the old end, merged with a free range that ended there
			int start = old;
			Map.Entry<Integer, Integer> last = this.free.lastEntry();
			if (last != null && last.getKey() + last.getValue() == old) {
				start = last.getKey();
				this.free.remove(start);
			}
			int end = start + size;
			if (end < grown) {
				this.free.put(end, grown - end);
			}
			return start;
		}

		void release(int start, int size) {
			Map.Entry<Integer, Integer> before = this.free.floorEntry(start);
			if (before != null && before.getKey() + before.getValue() == start) {
				start = before.getKey();
				size += before.getValue();
				this.free.remove(start);
			}
			Integer after = this.free.get(start + size);
			if (after != null) {
				this.free.remove(start + size);
				size += after;
			}
			this.free.put(start, size);
		}

		void delete() {
			GL15.glDeleteBuffers(this.vbo);
		}
	}

	private Regions() {
	}

	/** The renderers are (re)loaded: start over, and choose DNZ's path or Minecraft's. */
	public static void reset() {
		for (Region r : REGIONS.values()) {
			for (Arena a : r.arenas) {
				if (a != null) {
					a.delete();
				}
			}
		}
		REGIONS.clear();
		SLOTS.clear();
		AnimatedTextures.reset();
		boolean on = Engine.enabled() && OpenGlHelper.useVbo() && !Bench.vanillaRenderer();
		if (on) {
			ContextCapabilities caps = GLContext.getCapabilities();
			on = caps.OpenGL31 || caps.GL_ARB_copy_buffer;
		}
		active = on;
	}

	public static int regionCount() {
		return REGIONS.size();
	}

	/** A chunk piece's new blocks for one layer (game thread). */
	public static void upload(EnumWorldBlockLayer layer, WorldRenderer wr, RenderChunk chunk) {
		int l = layer.ordinal();
		free(chunk, l);
		int count = wr.getVertexCount();
		if (count == 0 || wr.getVertexFormat().getNextOffset() != STRIDE) {
			return;
		}
		BlockPos pos = chunk.getPosition();
		int ox = pos.getX() >> AREA_SHIFT << AREA_SHIFT;
		int oz = pos.getZ() >> AREA_SHIFT << AREA_SHIFT;
		long key = ((long) (ox >> AREA_SHIFT) << 32) ^ ((oz >> AREA_SHIFT) & 0xFFFFFFFFL);
		Region region = REGIONS.get(key);
		if (region == null) {
			region = new Region(ox, oz);
			REGIONS.put(key, region);
		}

		// copy the vertices and move them from the piece's corner to the area's corner
		int bytes = count * STRIDE;
		if (scratch.capacity() < bytes) {
			scratch = BufferUtils.createByteBuffer(Integer.highestOneBit(bytes) << 1);
		}
		ByteBuffer src = wr.getByteBuffer().duplicate().order(ByteOrder.nativeOrder());
		src.position(0).limit(bytes);
		scratch.clear();
		scratch.put(src);
		scratch.flip();
		float dx = pos.getX() - ox;
		float dy = pos.getY();
		float dz = pos.getZ() - oz;
		for (int i = 0; i < bytes; i += STRIDE) {
			scratch.putFloat(i, scratch.getFloat(i) + dx);
			scratch.putFloat(i + 4, scratch.getFloat(i + 4) + dy);
			scratch.putFloat(i + 8, scratch.getFloat(i + 8) + dz);
		}

		// the animated textures (water, lava...) this piece uses
		long animated = 0;
		for (int i = 0; i < bytes; i += STRIDE * 4) {
			animated |= AnimatedTextures.maskOf(scratch.getFloat(i + 16), scratch.getFloat(i + 20));
		}

		// Opaque layers: quads grouped by the way they face, so groups facing away from the camera can be skipped.
		// Translucent quads keep Minecraft's back-to-front order (one group).
		int[] faces = new int[FACES + 1];
		ByteBuffer data = scratch;
		if (layer != EnumWorldBlockLayer.TRANSLUCENT && count % 4 == 0) {
			data = groupByFace(count, faces);
		} else {
			for (int f = 1; f <= FACES; f++) {
				faces[f] = count;
			}
		}

		Arena arena = region.arena(l);
		int first = arena.allocate(count);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arena.vbo);
		GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, (long) first * PACKED, pack(data, count));
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

		Slot[] slots = SLOTS.get(chunk);
		if (slots == null) {
			slots = new Slot[LAYERS];
			SLOTS.put(chunk, slots);
		}
		Slot slot = new Slot(region, l, first, count, faces, dx, dy, dz);
		slot.animated = animated;
		slots[l] = slot;
		region.pieces++;
	}

	private static ByteBuffer groupByFace(int count, int[] faces) {
		int quads = count / 4;
		int quadBytes = STRIDE * 4;
		byte[] kind = new byte[quads];
		int[] sizes = new int[FACES];
		for (int q = 0; q < quads; q++) {
			int b = q * quadBytes;
			float x0 = scratch.getFloat(b), y0 = scratch.getFloat(b + 4), z0 = scratch.getFloat(b + 8);
			float x1 = scratch.getFloat(b + STRIDE), y1 = scratch.getFloat(b + STRIDE + 4), z1 = scratch.getFloat(b + STRIDE + 8);
			float x2 = scratch.getFloat(b + 2 * STRIDE), y2 = scratch.getFloat(b + 2 * STRIDE + 4), z2 = scratch.getFloat(b + 2 * STRIDE + 8);
			float ax = x1 - x0, ay = y1 - y0, az = z1 - z0;
			float bx = x2 - x0, by = y2 - y0, bz = z2 - z0;
			float nx = ay * bz - az * by;
			float ny = az * bx - ax * bz;
			float nz = ax * by - ay * bx;
			float ax2 = Math.abs(nx), ay2 = Math.abs(ny), az2 = Math.abs(nz);
			float eps = (ax2 + ay2 + az2) * 0.001F;
			int k = ANY;
			if (ay2 > eps && ax2 <= eps && az2 <= eps) {
				k = ny > 0 ? UP : DOWN;
			} else if (az2 > eps && ax2 <= eps && ay2 <= eps) {
				k = nz > 0 ? SOUTH : NORTH;
			} else if (ax2 > eps && ay2 <= eps && az2 <= eps) {
				k = nx > 0 ? EAST : WEST;
			}
			kind[q] = (byte) k;
			sizes[k] += 4;
		}
		faces[0] = 0;
		for (int f = 0; f < FACES; f++) {
			faces[f + 1] = faces[f] + sizes[f];
		}
		int bytes = count * STRIDE;
		if (sorted.capacity() < bytes) {
			sorted = BufferUtils.createByteBuffer(Integer.highestOneBit(bytes) << 1);
		}
		sorted.clear();
		sorted.limit(bytes);
		int[] next = new int[FACES];
		for (int f = 0; f < FACES; f++) {
			next[f] = faces[f] * STRIDE;
		}
		ByteBuffer src = scratch.duplicate();
		for (int q = 0; q < quads; q++) {
			int k = kind[q];
			src.limit(q * quadBytes + quadBytes).position(q * quadBytes);
			sorted.position(next[k]);
			sorted.put(src);
			next[k] += quadBytes;
		}
		sorted.position(0).limit(bytes);
		return sorted;
	}

	private static ByteBuffer pack(ByteBuffer src, int count) {
		int bytes = count * PACKED;
		if (packed.capacity() < bytes) {
			packed = BufferUtils.createByteBuffer(Integer.highestOneBit(bytes) << 1);
		}
		packed.clear();
		for (int v = 0; v < count; v++) {
			int i = v * STRIDE;
			packed.putShort(clamp((src.getFloat(i) - 64) * 128));
			packed.putShort(clamp((src.getFloat(i + 4) - 128) * 128));
			packed.putShort(clamp((src.getFloat(i + 8) - 64) * 128));
			packed.putShort((short) 0);
			packed.putInt(src.getInt(i + 12));
			packed.putShort(clamp(src.getFloat(i + 16) * 16384));
			packed.putShort(clamp(src.getFloat(i + 20) * 16384));
			packed.putInt(src.getInt(i + 24));
		}
		packed.flip();
		return packed;
	}

	private static short clamp(float v) {
		return (short) Math.max(-32768, Math.min(32767, Math.round(v)));
	}

	/** The piece moved to another place or was deleted: its old blocks are gone. */
	public static void free(RenderChunk chunk) {
		for (int l = 0; l < LAYERS; l++) {
			free(chunk, l);
		}
		SLOTS.remove(chunk);
	}

	private static void free(RenderChunk chunk, int layer) {
		Slot[] slots = SLOTS.get(chunk);
		if (slots != null && slots[layer] != null) {
			Slot s = slots[layer];
			s.region.arena(layer).release(s.first, s.size);
			s.region.pieces--;
			slots[layer] = null;
		}
	}

	public static void draw(List<RenderChunk> chunks, EnumWorldBlockLayer layer, double viewX, double viewY, double viewZ) {
		int l = layer.ordinal();
		double fog = net.minecraft.client.Minecraft.getMinecraft().gameSettings.renderDistanceChunks * 16.0 + 8;
		double fogSq = fog * fog;
		List<List<Slot>> batches = new ArrayList<List<Slot>>();
		if (layer == EnumWorldBlockLayer.TRANSLUCENT) {
			List<Slot> run = null;
			for (RenderChunk c : chunks) {
				Slot s = slot(c, l);
				if (s == null) {
					continue;
				}
				if (run == null || run.get(0).region != s.region) {
					run = new ArrayList<Slot>();
					batches.add(run);
				}
				run.add(s);
			}
		} else {
			Map<Region, List<Slot>> byRegion = new LinkedHashMap<Region, List<Slot>>();
			for (RenderChunk c : chunks) {
				Slot s = slot(c, l);
				if (s == null) {
					continue;
				}
				List<Slot> list = byRegion.get(s.region);
				if (list == null) {
					list = new ArrayList<Slot>();
					byRegion.put(s.region, list);
				}
				list.add(s);
			}
			batches.addAll(byRegion.values());
		}

		// Texture coordinates come as shorts in 1/16384 (see pack)
		GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
		GlStateManager.matrixMode(GL11.GL_TEXTURE);
		GlStateManager.pushMatrix();
		GlStateManager.scale(1 / 16384F, 1 / 16384F, 1);
		GlStateManager.matrixMode(GL11.GL_MODELVIEW);
		for (List<Slot> batch : batches) {
			Region region = batch.get(0).region;
			if (firsts.capacity() < batch.size() * FACES) {
				firsts = BufferUtils.createIntBuffer(batch.size() * FACES * 2);
				counts = BufferUtils.createIntBuffer(batch.size() * FACES * 2);
			}
			firsts.clear();
			counts.clear();
			// the camera in the area's coordinates
			double cx = viewX - region.originX;
			double cy = viewY;
			double cz = viewZ - region.originZ;
			for (Slot s : batch) {
				// Fog: a piece entirely past the render distance (the corners of the square) is fully fogged
				double ex = Math.max(0, Math.max(s.minX - cx, cx - (s.minX + 16)));
				double ez = Math.max(0, Math.max(s.minZ - cz, cz - (s.minZ + 16)));
				if (ex * ex + ez * ez > fogSq) {
					continue;
				}
				// A face group is skipped when the camera is behind all of its faces (one block of room for models
				// that reach out of their block).
				boolean[] show = {
					true,
					cy < s.minY + 17,
					cy > s.minY - 1,
					cz < s.minZ + 17,
					cz > s.minZ - 1,
					cx < s.minX + 17,
					cx > s.minX - 1,
				};
				int runStart = -1;
				int runEnd = -1;
				for (int f = 0; f < FACES; f++) {
					int start = s.faces[f];
					int end = s.faces[f + 1];
					if (end == start || !show[f]) {
						continue;
					}
					if (runStart >= 0 && runEnd == start) {
						runEnd = end; // neighbouring groups: one range
					} else {
						if (runStart >= 0) {
							firsts.put(s.first + runStart);
							counts.put(runEnd - runStart);
						}
						runStart = start;
						runEnd = end;
					}
				}
				if (runStart >= 0) {
					firsts.put(s.first + runStart);
					counts.put(runEnd - runStart);
					AnimatedTextures.drawn(s.animated);
				}
			}
			firsts.flip();
			counts.flip();
			if (!firsts.hasRemaining()) {
				continue;
			}
			GlStateManager.pushMatrix();
			GlStateManager.translate((float) (region.originX - viewX), (float) (-viewY), (float) (region.originZ - viewZ));
			OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER, region.arena(l).vbo);
			// Packed format (see pack): positions are shorts in 1/128 block, texture coordinates shorts in 1/16384
			GlStateManager.translate(64, 128, 64);
			GlStateManager.scale(1 / 128F, 1 / 128F, 1 / 128F);
			GL11.glVertexPointer(3, GL11.GL_SHORT, PACKED, 0L);
			GL11.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, PACKED, 8L);
			GL11.glTexCoordPointer(2, GL11.GL_SHORT, PACKED, 12L);
			OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
			GL11.glTexCoordPointer(2, GL11.GL_SHORT, PACKED, 16L);
			OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
			GL14.glMultiDrawArrays(GL11.GL_QUADS, firsts, counts);
			GlStateManager.popMatrix();
		}
		GlStateManager.matrixMode(GL11.GL_TEXTURE);
		GlStateManager.popMatrix();
		GlStateManager.matrixMode(GL11.GL_MODELVIEW);
		OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER, 0);
		GlStateManager.resetColor();
	}

	private static Slot slot(RenderChunk chunk, int layer) {
		Slot[] slots = SLOTS.get(chunk);
		return slots == null ? null : slots[layer];
	}
}
