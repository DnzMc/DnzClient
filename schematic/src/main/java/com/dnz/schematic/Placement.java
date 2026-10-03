package com.dnz.schematic;

import com.dnz.schematic.litematic.Litematic;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Where a schematic stands in the world: its corner, turn and mirror.
 * Positions inside the schematic ("relative", 0..size-1) are mirrored first, then turned, then moved to the corner.
 */
public final class Placement {
	public final Litematic schematic;
	private BlockPos origin;
	private Rotation rotation = Rotation.NONE;
	private Mirror mirror = Mirror.NONE;
	/** Palette with the turn and mirror applied (stairs, doors etc. face the right way). */
	private BlockState[] placedPalette;
	/** Bumped on every change, so the checker and renderer know their results are stale. */
	private int version;

	public Placement(Litematic schematic, BlockPos origin) {
		this.schematic = schematic;
		this.origin = origin.immutable();
		this.rebuildPalette();
	}

	public BlockPos origin() {
		return this.origin;
	}

	public Rotation rotation() {
		return this.rotation;
	}

	public Mirror mirror() {
		return this.mirror;
	}

	public int version() {
		return this.version;
	}

	public void setOrigin(BlockPos origin) {
		this.origin = origin.immutable();
		this.version++;
	}

	public void setRotation(Rotation rotation) {
		this.rotation = rotation;
		this.rebuildPalette();
	}

	public void setMirror(Mirror mirror) {
		this.mirror = mirror;
		this.rebuildPalette();
	}

	private void rebuildPalette() {
		BlockState[] p = new BlockState[this.schematic.paletteSize()];
		for (int i = 0; i < p.length; i++) {
			p[i] = this.schematic.state(i).mirror(this.mirror).rotate(this.rotation);
		}
		this.placedPalette = p;
		this.version++;
	}

	/** The block that should be at a relative index, turned the right way. */
	public BlockState placedState(int paletteIndex) {
		return this.placedPalette[paletteIndex];
	}

	/** Size along world X and Z after turning (a quarter turn swaps them). */
	public int worldSizeX() {
		return this.quarterTurn() ? this.schematic.sizeZ : this.schematic.sizeX;
	}

	public int worldSizeZ() {
		return this.quarterTurn() ? this.schematic.sizeX : this.schematic.sizeZ;
	}

	private boolean quarterTurn() {
		return this.rotation == Rotation.CLOCKWISE_90 || this.rotation == Rotation.COUNTERCLOCKWISE_90;
	}

	public BlockPos min() {
		return this.origin;
	}

	public BlockPos max() {
		return this.origin.offset(this.worldSizeX() - 1, this.schematic.sizeY - 1, this.worldSizeZ() - 1);
	}

	/** World X of a relative position (writes world x,z into out[0], out[1]). */
	public void toWorldXZ(int x, int z, int[] out) {
		int sx = this.schematic.sizeX, sz = this.schematic.sizeZ;
		if (this.mirror == Mirror.FRONT_BACK) {
			x = sx - 1 - x;
		} else if (this.mirror == Mirror.LEFT_RIGHT) {
			z = sz - 1 - z;
		}
		int wx, wz;
		switch (this.rotation) {
			case CLOCKWISE_90 -> {
				wx = sz - 1 - z;
				wz = x;
			}
			case CLOCKWISE_180 -> {
				wx = sx - 1 - x;
				wz = sz - 1 - z;
			}
			case COUNTERCLOCKWISE_90 -> {
				wx = z;
				wz = sx - 1 - x;
			}
			default -> {
				wx = x;
				wz = z;
			}
		}
		out[0] = this.origin.getX() + wx;
		out[1] = this.origin.getZ() + wz;
	}

	/** Relative index of a world position, or -1 if it is outside the schematic. */
	public int toIndex(int worldX, int worldY, int worldZ) {
		int wx = worldX - this.origin.getX(), y = worldY - this.origin.getY(), wz = worldZ - this.origin.getZ();
		if (wx < 0 || y < 0 || wz < 0 || wx >= this.worldSizeX() || y >= this.schematic.sizeY || wz >= this.worldSizeZ()) {
			return -1;
		}
		int sx = this.schematic.sizeX, sz = this.schematic.sizeZ;
		int x, z;
		switch (this.rotation) {
			case CLOCKWISE_90 -> {
				x = wz;
				z = sz - 1 - wx;
			}
			case CLOCKWISE_180 -> {
				x = sx - 1 - wx;
				z = sz - 1 - wz;
			}
			case COUNTERCLOCKWISE_90 -> {
				x = sx - 1 - wz;
				z = wx;
			}
			default -> {
				x = wx;
				z = wz;
			}
		}
		if (this.mirror == Mirror.FRONT_BACK) {
			x = sx - 1 - x;
		} else if (this.mirror == Mirror.LEFT_RIGHT) {
			z = sz - 1 - z;
		}
		return this.schematic.index(x, y, z);
	}

	public int worldY(int index) {
		return this.origin.getY() + this.schematic.yOf(index);
	}
}
