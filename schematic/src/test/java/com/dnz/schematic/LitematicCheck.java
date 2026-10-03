package com.dnz.schematic;

import com.dnz.schematic.litematic.Litematic;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Checks reading/writing .litematic files, turning/mirroring and the block comparison without starting the game.
 * gradlew litematicCheck
 */
public final class LitematicCheck {
	private static int failed;

	public static void main(String[] args) throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();

		// 1) Many different blocks (palette needs 8+ bits, so entries are split across longs) survive a round trip.
		List<BlockState> all = new ArrayList<>();
		for (var block : BuiltInRegistries.BLOCK) {
			all.add(block.defaultBlockState());
			if (all.size() >= 300) {
				break;
			}
		}
		int sx = 13, sy = 7, sz = 11;
		BlockState[] states = new BlockState[sx * sy * sz];
		Random random = new Random(1);
		for (int i = 0; i < states.length; i++) {
			states[i] = random.nextInt(5) == 0 ? Blocks.AIR.defaultBlockState() : all.get(random.nextInt(all.size()));
		}
		Path file = Files.createTempFile("dnz-check", ".litematic");
		Litematic.write(file, "Check", sx, sy, sz, states);
		Litematic read = Litematic.read(file);
		boolean same = read.sizeX == sx && read.sizeY == sy && read.sizeZ == sz;
		for (int i = 0; same && i < states.length; i++) {
			same = read.state(read.paletteAt(i)) == states[i];
		}
		check("round trip (" + read.paletteSize() + " kinds of blocks)", same);
		check("name", "Check".equals(read.name));
		int nonAir = 0;
		for (BlockState s : states) {
			nonAir += s.isAir() ? 0 : 1;
		}
		check("block count", read.nonAirCount() == nonAir);
		Files.delete(file);

		// 2) Every turn and mirror: each position lands inside the box exactly once, and maps back to itself.
		for (Rotation rotation : Rotation.values()) {
			for (Mirror mirror : Mirror.values()) {
				Placement p = new Placement(read, new BlockPos(100, 64, -50));
				p.setRotation(rotation);
				p.setMirror(mirror);
				Set<Long> seen = new HashSet<>();
				boolean ok = true;
				int[] xz = new int[2];
				for (int i = 0; i < read.volume() && ok; i++) {
					p.toWorldXZ(read.xOf(i), read.zOf(i), xz);
					int wy = p.worldY(i);
					ok = seen.add(BlockPos.asLong(xz[0], wy, xz[1]))
						&& xz[0] >= p.min().getX() && xz[0] <= p.max().getX()
						&& xz[1] >= p.min().getZ() && xz[1] <= p.max().getZ()
						&& p.toIndex(xz[0], wy, xz[1]) == i;
				}
				check("turn " + rotation + " / mirror " + mirror, ok);
			}
		}

		// 3) A quarter turn matches Minecraft's own rotation: a stair facing north turns to face east.
		BlockState stair = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
		check("stairs turn with the building", stair.rotate(Rotation.CLOCKWISE_90).getValue(StairBlock.FACING) == Direction.EAST);

		// 4) Comparison
		BlockState stone = Blocks.STONE.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		check("correct block", Checker.compare(stone, stone, false) == Checker.OK);
		check("missing block", Checker.compare(stone, air, false) == Checker.MISSING);
		check("grass counts as missing", Checker.compare(stone, Blocks.SHORT_GRASS.defaultBlockState(), false) == Checker.MISSING);
		check("wrong block", Checker.compare(stone, Blocks.DIRT.defaultBlockState(), false) == Checker.WRONG_BLOCK);
		check("wrong direction", Checker.compare(stair, stair.setValue(StairBlock.FACING, Direction.SOUTH), false) == Checker.WRONG_STATE);
		check("extra block", Checker.compare(air, stone, true) == Checker.EXTRA);
		check("grass is not extra", Checker.compare(air, Blocks.SHORT_GRASS.defaultBlockState(), true) == Checker.AIR_OK);
		BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState();
		check("leaf decay distance ignored",
			Checker.compare(leaves, leaves.setValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE, 3), false) == Checker.OK);

		// 5) Real files from this computer, if there are any (only read, never changed).
		for (String arg : args) {
			Path real = Path.of(arg);
			try {
				Litematic l = Litematic.read(real);
				System.out.println("REAL " + real.getFileName() + ": " + l.name + " " + l.sizeX + "x" + l.sizeY + "x" + l.sizeZ
					+ ", " + l.nonAirCount() + " blocks, " + l.paletteSize() + " kinds");
			} catch (Exception e) {
				failed++;
				System.out.println("FAIL real file " + real + ": " + e);
			}
		}

		System.out.println(failed == 0 ? "LITEMATIC OK" : "LITEMATIC FAILED (" + failed + ")");
		System.exit(failed == 0 ? 0 : 1);
	}

	private static void check(String name, boolean ok) {
		if (!ok) {
			failed++;
		}
		System.out.println((ok ? "OK   " : "FAIL ") + name);
	}
}
