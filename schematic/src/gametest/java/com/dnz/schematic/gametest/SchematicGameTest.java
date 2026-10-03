package com.dnz.schematic.gametest;

import com.dnz.schematic.Checker;
import com.dnz.schematic.Materials;
import com.dnz.schematic.Placement;
import com.dnz.schematic.SchematicConfig;
import com.dnz.schematic.Schematics;
import com.dnz.schematic.gui.SchematicScreen;
import com.dnz.schematic.litematic.Litematic;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Builds a small house schematic (5 x 3 x 5) with a few mistakes on purpose and checks that DNZ Schematic
 * finds exactly those mistakes, counts the materials right, and draws everything (screenshots).
 */
public class SchematicGameTest implements FabricClientGameTest {
	private static final int S = 5, H = 3;
	private final List<String> failures = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		// The house: stone floor, oak plank walls, a stair facing north in the front wall, a chest inside, glass on top of the walls' corners.
		BlockState[] states = new BlockState[S * H * S];
		BlockState stair = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
		for (int y = 0; y < H; y++) {
			for (int z = 0; z < S; z++) {
				for (int x = 0; x < S; x++) {
					boolean edge = x == 0 || z == 0 || x == S - 1 || z == S - 1;
					BlockState s = Blocks.AIR.defaultBlockState();
					if (y == 0) {
						s = Blocks.STONE.defaultBlockState();
					} else if (y == 1 && edge) {
						s = x == 2 && z == 0 ? stair : Blocks.OAK_PLANKS.defaultBlockState();
					} else if (y == 1 && x == 2 && z == 2) {
						s = Blocks.CHEST.defaultBlockState();
					} else if (y == 2 && (x == 0 || x == S - 1) && (z == 0 || z == S - 1)) {
						s = Blocks.GLASS.defaultBlockState();
					}
					states[(y * S + z) * S + x] = s;
				}
			}
		}
		Path file = context.computeOnClient(mc -> Schematics.folder().resolve("dnz-test-house.litematic"));
		try {
			Litematic.write(file, "DNZ Test House", S, H, S, states);
		} catch (Exception e) {
			throw new AssertionError(e);
		}

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			BlockPos player = context.computeOnClient(mc -> mc.player.blockPosition());
			BlockPos o = player.offset(3, 0, 3);
			// Clear the spot (trees, grass) and give it flat ground.
			world.getServer().runCommand(String.format("fill %d %d %d %d %d %d minecraft:air", o.getX() - 2, o.getY(), o.getZ() - 2, o.getX() + 7, o.getY() + 8, o.getZ() + 7));
			world.getServer().runCommand(String.format("fill %d %d %d %d %d %d minecraft:grass_block", o.getX() - 2, o.getY() - 1, o.getZ() - 2, o.getX() + 7, o.getY() - 1, o.getZ() + 7));

			context.runOnClient(mc -> {
				SchematicConfig c = SchematicConfig.get();
				c.layerMode = 0;
				c.visible = true;
				Schematics.load(file);
			});
			context.waitFor(mc -> Schematics.placement() != null, 200);
			context.runOnClient(mc -> Schematics.placement().setOrigin(o));

			// Build the floor right, the walls with 3 mistakes, and one extra block.
			world.getServer().runCommand(String.format("fill %d %d %d %d %d %d minecraft:stone", o.getX(), o.getY(), o.getZ(), o.getX() + 4, o.getY(), o.getZ() + 4));
			world.getServer().runCommand(String.format("fill %d %d %d %d %d %d minecraft:oak_planks", o.getX(), o.getY() + 1, o.getZ(), o.getX() + 4, o.getY() + 1, o.getZ()));
			world.getServer().runCommand(String.format("setblock %d %d %d minecraft:oak_stairs[facing=south]", o.getX() + 2, o.getY() + 1, o.getZ()));
			world.getServer().runCommand(String.format("setblock %d %d %d minecraft:dirt", o.getX() + 4, o.getY() + 1, o.getZ() + 2));
			world.getServer().runCommand(String.format("setblock %d %d %d minecraft:cobblestone", o.getX() + 2, o.getY() + 2, o.getZ() + 2));
			// Some planks in the inventory for the material list.
			world.getServer().runCommand("give @p minecraft:oak_planks 5");
			// Look at the house from the side and a bit above.
			// Spectator mode so the camera stays in the air.
			world.getServer().runCommand("gamemode spectator @p");
			world.getServer().runCommand(String.format("tp @p %d %d %d -45 32", o.getX() - 4, o.getY() + 5, o.getZ() - 4));
			context.waitTicks(60);

			// Expected: floor 25 OK; front wall row 5 = 4 planks OK + stair wrong way;
			// other walls: 11 planks missing except 1 dirt (wrong); chest missing; 4 glass missing; 1 extra.
			int ok = count(context, Checker.OK);
			int missing = count(context, Checker.MISSING);
			int wrong = count(context, Checker.WRONG_BLOCK);
			int wrongState = count(context, Checker.WRONG_STATE);
			int extra = count(context, Checker.EXTRA);
			System.out.printf("[DNZ TEST] ok=%d missing=%d wrong=%d wrongState=%d extra=%d%n", ok, missing, wrong, wrongState, extra);
			check("whole schematic checked", context.computeOnClient(mc -> Schematics.checker().complete()));
			check("correct blocks = 29", ok == 29);
			check("wrong block (dirt) = 1", wrong == 1);
			check("wrong direction (stair) = 1", wrongState == 1);
			check("extra block (cobblestone) = 1", extra == 1);
			check("missing = 10 planks + chest + 4 glass = 15", missing == 15);

			// Material list: planks needed 15, placed 4, 5 in the inventory -> 6 still missing.
			var planks = context.computeOnClient(mc -> Materials.compute(Schematics.placement(), Schematics.checker(), mc.player).stream()
				.filter(r -> r.icon().is(Items.OAK_PLANKS)).findFirst().orElse(null));
			check("materials: planks row", planks != null);
			if (planks != null) {
				System.out.printf("[DNZ TEST] planks needed=%d placed=%d have=%d missing=%d%n", planks.needed(), planks.placed(), planks.have(), planks.missing());
				check("materials: planks 15 needed, 4 placed, 5 have, 6 missing",
					planks.needed() == 15 && planks.placed() == 4 && planks.have() == 5 && planks.missing() == 6);
			}

			// The nearest mistake is found.
			context.waitTicks(12);
			check("nearest mistake found", context.computeOnClient(mc -> Schematics.nearestError() != null));
			System.out.println("[DNZ TEST] screenshot " + context.takeScreenshot("schematic-world").toAbsolutePath());
			// From the other side too (the red wrong block and the extra block are there).
			world.getServer().runCommand(String.format("tp @p %d %d %d 135 32", o.getX() + 9, o.getY() + 5, o.getZ() + 9));
			context.waitTicks(10);
			System.out.println("[DNZ TEST] screenshot " + context.takeScreenshot("schematic-world-back").toAbsolutePath());

			// Layers: only the wall layer -> the glass layer and floor are hidden.
			context.runOnClient(mc -> {
				SchematicConfig c = SchematicConfig.get();
				c.layerMode = 1;
				c.layer = 1;
			});
			check("layer 2 shown, layer 3 hidden", context.computeOnClient(mc -> Schematics.layerVisible(1) && !Schematics.layerVisible(2)));
			context.waitTicks(10);
			System.out.println("[DNZ TEST] screenshot " + context.takeScreenshot("schematic-layer").toAbsolutePath());
			context.runOnClient(mc -> SchematicConfig.get().layerMode = 0);

			// Fixing a mistake is noticed right away (the player is close).
			world.getServer().runCommand(String.format("setblock %d %d %d minecraft:oak_planks", o.getX() + 4, o.getY() + 1, o.getZ() + 2));
			context.waitTicks(5);
			check("fixed block turns correct", count(context, Checker.WRONG_BLOCK) == 0);

			// The menu, on a few tabs.
			for (int tab : new int[] {1, 3, 4}) {
				context.runOnClient(mc -> mc.gui.setScreen(SchematicScreen.open(null, tab)));
				context.waitTicks(5);
				System.out.println("[DNZ TEST] screenshot " + context.takeScreenshot("schematic-menu-" + tab).toAbsolutePath());
			}
			context.runOnClient(mc -> mc.gui.setScreen(null));

			// Turning the schematic re-checks everything.
			context.runOnClient(mc -> Schematics.placement().setRotation(net.minecraft.world.level.block.Rotation.CLOCKWISE_90));
			context.waitTicks(10);
			// A quarter turn moves the stair to the east wall: now two blocks are wrong and none faces the wrong way.
			check("turning re-checks", count(context, Checker.WRONG_BLOCK) == 2 && count(context, Checker.WRONG_STATE) == 0);
			context.runOnClient(mc -> Schematics.remove());

			// Speed: a big schematic (100 x 50 x 100 = 500 000 blocks) must cost almost nothing per tick once checked.
			int bx = 100, by = 50, bz = 100;
			BlockState[] big = new BlockState[bx * by * bz];
			for (int z = 0; z < bz; z++) {
				for (int x = 0; x < bx; x++) {
					big[(0 * bz + z) * bx + x] = Blocks.STONE.defaultBlockState();
					if ((x + z) % 7 == 0) {
						big[(1 * bz + z) * bx + x] = Blocks.OAK_PLANKS.defaultBlockState();
					}
				}
			}
			Path bigFile = context.computeOnClient(mc -> Schematics.folder().resolve("dnz-test-big.litematic"));
			try {
				Litematic.write(bigFile, "DNZ Big Test", bx, by, bz, big);
			} catch (Exception e) {
				throw new AssertionError(e);
			}
			context.runOnClient(mc -> Schematics.load(bigFile));
			context.waitFor(mc -> Schematics.placement() != null && Schematics.placement().schematic.volume() == bx * by * bz, 400);
			context.runOnClient(mc -> Schematics.placement().setOrigin(o.offset(-50, 0, -50)));
			context.waitFor(mc -> Schematics.checker().complete(), 600);
			context.waitTicks(20);
			double msPerTick = context.computeOnClient(mc -> {
				int runs = 60;
				long start = System.nanoTime();
				for (int i = 0; i < runs; i++) {
					Schematics.tick(mc);
					com.dnz.schematic.GhostRenderer.tick(mc);
				}
				return (System.nanoTime() - start) / 1_000_000.0 / runs;
			});
			System.out.printf("[DNZ TEST] big schematic: %.3f ms per tick (a tick is 50 ms)%n", msPerTick);
			check("big schematic costs under 1 ms per tick", msPerTick < 1.0);
			context.runOnClient(mc -> Schematics.remove());
			try {
				java.nio.file.Files.deleteIfExists(bigFile);
			} catch (Exception ignored) {
			}
		}
		try {
			java.nio.file.Files.deleteIfExists(file);
		} catch (Exception ignored) {
		}
		if (!this.failures.isEmpty()) {
			throw new AssertionError("DNZ Schematic checks failed: " + this.failures);
		}
		System.out.println("[DNZ TEST] SCHEMATIC OK");
	}

	private static int count(ClientGameTestContext context, byte status) {
		return context.computeOnClient(mc -> {
			Placement p = Schematics.placement();
			return p == null ? -1 : Schematics.checker().count(status);
		});
	}

	private void check(String what, boolean ok) {
		System.out.println("[DNZ TEST] " + (ok ? "ok   " : "FAIL ") + what);
		if (!ok) {
			this.failures.add(what);
		}
	}
}
