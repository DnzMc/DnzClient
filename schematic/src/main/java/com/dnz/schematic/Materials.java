package com.dnz.schematic;

import com.dnz.schematic.litematic.Litematic;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;

/** How many of each item the schematic needs, how many are already built, and what is in the inventory. */
public final class Materials {
	public record Row(ItemStack icon, String name, int needed, int placed, int have) {
		/** Still to place. */
		public int left() {
			return Math.max(0, this.needed - this.placed);
		}

		/** Still to place and not in the inventory. */
		public int missing() {
			return Math.max(0, this.left() - this.have);
		}
	}

	private Materials() {
	}

	public static List<Row> compute(Placement placement, Checker checker, Player player) {
		Litematic schem = placement.schematic;
		Map<Item, int[]> byItem = new LinkedHashMap<>();
		for (int p = 1; p < schem.paletteSize(); p++) {
			BlockState state = placement.placedState(p);
			int per = itemsPerBlock(state);
			Item item = state.getBlock().asItem();
			if (per == 0 || item == Items.AIR) {
				continue; // upper door halves, bed heads, fire, portals... (no item of their own)
			}
			int[] counts = byItem.computeIfAbsent(item, k -> new int[2]);
			counts[0] += schem.total(p) * per;
			counts[1] += checker.okCount(p) * per;
		}
		List<Row> rows = new ArrayList<>();
		for (Map.Entry<Item, int[]> e : byItem.entrySet()) {
			ItemStack icon = new ItemStack(e.getKey());
			rows.add(new Row(icon, icon.getHoverName().getString(), e.getValue()[0], e.getValue()[1], count(player, e.getKey())));
		}
		rows.sort(Comparator.comparingInt(Row::missing).reversed().thenComparing(Comparator.comparingInt(Row::left).reversed())
			.thenComparing(Row::name));
		return rows;
	}

	/** Items one block of this state costs: 0 for the second half of doors, beds and tall plants, 2 for double slabs. */
	private static int itemsPerBlock(BlockState state) {
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
			return 0;
		}
		if (state.hasProperty(BlockStateProperties.BED_PART) && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) {
			return 0;
		}
		if (state.hasProperty(BlockStateProperties.SLAB_TYPE) && state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE) {
			return 2;
		}
		return 1;
	}

	private static int count(Player player, Item item) {
		if (player == null) {
			return 0;
		}
		int n = 0;
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(item)) {
				n += stack.getCount();
			}
		}
		return n;
	}
}
