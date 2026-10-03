package com.dnz.schematic;

import com.dnz.schematic.litematic.Litematic;
import java.util.Arrays;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Compares the schematic with the world, block by block.
 * The whole schematic is walked a slice per tick (so big buildings never cause lag), and the blocks right
 * around the player are checked every tick, so building feels instant.
 */
public final class Checker {
	public static final byte UNKNOWN = 0;
	public static final byte OK = 1;
	public static final byte MISSING = 2;
	public static final byte WRONG_BLOCK = 3;
	public static final byte WRONG_STATE = 4;
	public static final byte EXTRA = 5;
	public static final byte AIR_OK = 6;

	/** Blocks checked per tick by the background walk until the whole schematic was seen once. */
	private static final int SWEEP_PER_TICK = 30_000;
	/** After that only a slow re-check in the background (changes near the player are seen every tick anyway). */
	private static final int SWEEP_IDLE_PER_TICK = 3_000;
	/** Blocks around the player checked every tick. */
	private static final int NEAR = 5;

	/**
	 * Properties that change by themselves (leaf decay distance, crop age, redstone power...).
	 * A difference there is not a building mistake.
	 */
	private static final Set<String> IGNORED = Set.of("distance", "persistent", "power", "powered", "triggered", "age", "lit",
		"occupied", "moisture", "stage", "has_bottle_0", "has_bottle_1", "has_bottle_2", "has_record", "has_book");

	private final Placement placement;
	private final Litematic schem;
	private final byte[] status;
	private final int[] counts = new int[7];
	/** Correctly placed positions per palette entry (for the material list). */
	private final int[] okPerPalette;
	private int cursor;
	private int seenVersion = -1;
	/** True once the whole schematic has been walked at least once since the last change. */
	private boolean complete;
	private int walked;
	/** Grows whenever any position's result changes, so the renderer only rebuilds when needed. */
	private int changes;
	private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
	private final int[] xz = new int[2];

	public Checker(Placement placement) {
		this.placement = placement;
		this.schem = placement.schematic;
		this.status = new byte[this.schem.volume()];
		this.okPerPalette = new int[this.schem.paletteSize()];
	}

	public byte status(int index) {
		return this.status[index];
	}

	public int count(byte status) {
		return this.counts[status];
	}

	public int okCount(int paletteIndex) {
		return this.okPerPalette[paletteIndex];
	}

	public int changes() {
		return this.changes;
	}

	/** Missing, wrong or extra blocks exist (known so far). */
	public boolean hasErrors() {
		return this.counts[MISSING] + this.counts[WRONG_BLOCK] + this.counts[WRONG_STATE] + this.counts[EXTRA] > 0;
	}

	public boolean complete() {
		return this.complete;
	}

	public static boolean isError(byte status) {
		return status == MISSING || status == WRONG_BLOCK || status == WRONG_STATE || status == EXTRA;
	}

	public void tick(ClientLevel level, BlockPos player) {
		if (this.placement.version() != this.seenVersion) {
			this.seenVersion = this.placement.version();
			Arrays.fill(this.status, UNKNOWN);
			Arrays.fill(this.counts, 0);
			Arrays.fill(this.okPerPalette, 0);
			this.counts[UNKNOWN] = this.status.length;
			this.cursor = 0;
			this.walked = 0;
			this.complete = false;
		}

		// Right around the player first.
		BlockPos min = this.placement.min(), max = this.placement.max();
		int x0 = Math.max(min.getX(), player.getX() - NEAR), x1 = Math.min(max.getX(), player.getX() + NEAR);
		int y0 = Math.max(min.getY(), player.getY() - NEAR), y1 = Math.min(max.getY(), player.getY() + NEAR);
		int z0 = Math.max(min.getZ(), player.getZ() - NEAR), z1 = Math.min(max.getZ(), player.getZ() + NEAR);
		for (int y = y0; y <= y1; y++) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					int i = this.placement.toIndex(x, y, z);
					if (i >= 0) {
						this.check(level, i);
					}
				}
			}
		}

		int n = Math.min(this.complete ? SWEEP_IDLE_PER_TICK : SWEEP_PER_TICK, this.status.length);
		for (int k = 0; k < n; k++) {
			this.check(level, this.cursor);
			if (++this.cursor >= this.status.length) {
				this.cursor = 0;
			}
		}
		this.walked += n;
		if (this.walked >= this.status.length) {
			this.complete = true;
		}
	}

	/** Checks one position right now (e.g. the block the player looks at). */
	public void check(ClientLevel level, int index) {
		int palette = this.schem.paletteAt(index);
		this.placement.toWorldXZ(this.schem.xOf(index), this.schem.zOf(index), this.xz);
		this.pos.set(this.xz[0], this.placement.worldY(index), this.xz[1]);
		byte result;
		if (level.isOutsideBuildHeight(this.pos) || !level.isLoaded(this.pos)) {
			result = UNKNOWN;
		} else {
			result = compare(this.placement.placedState(palette), level.getBlockState(this.pos), palette == 0);
		}
		byte old = this.status[index];
		if (old != result) {
			this.changes++;
			this.status[index] = result;
			this.counts[old]--;
			this.counts[result]++;
			if (old == OK) {
				this.okPerPalette[palette]--;
			}
			if (result == OK) {
				this.okPerPalette[palette]++;
			}
		}
	}

	static byte compare(BlockState expected, BlockState actual, boolean expectedAir) {
		if (expectedAir) {
			// Grass, flowers, snow layers and water left in the area are not counted as extra blocks.
			return actual.isAir() || actual.canBeReplaced() ? AIR_OK : EXTRA;
		}
		if (actual.isAir() || (actual.canBeReplaced() && actual.getBlock() != expected.getBlock())) {
			return MISSING;
		}
		if (actual.getBlock() != expected.getBlock()) {
			return WRONG_BLOCK;
		}
		if (actual == expected) {
			return OK;
		}
		for (Property<?> property : expected.getProperties()) {
			if (!IGNORED.contains(property.getName()) && !expected.getValue(property).equals(actual.getValue(property))) {
				return WRONG_STATE;
			}
		}
		return OK;
	}
}
