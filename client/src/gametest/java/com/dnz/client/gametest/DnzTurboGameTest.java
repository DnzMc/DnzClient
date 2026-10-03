package com.dnz.client.gametest;

import com.dnz.client.DnzConfig;
import com.dnz.client.turbo.Turbo;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

/** DNZ Turbo: far armor stands are skipped, near ones drawn, particles capped; off = everything drawn. */
public class DnzTurboGameTest implements FabricClientGameTest {
	private final List<String> failures = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!System.getProperty("dnz.bench", "").isEmpty()) {
			return; // only the FPS benchmark runs
		}
		context.runOnClient(mc -> DnzConfig.get().turbo = true);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("execute as @p at @s run summon minecraft:armor_stand ~5 ~ ~ {CustomName:'\"near\"'}");
			world.getServer().runCommand("execute as @p at @s run summon minecraft:armor_stand ~40 ~ ~ {CustomName:'\"far\"'}");
			context.waitTicks(20);
			check("near armor stand is drawn", !skipped(context, 5));
			check("far armor stand is skipped", skipped(context, 40));

			world.getServer().runCommand("execute as @p at @s run particle minecraft:flame ~ ~1 ~3 2 2 2 0 5000 force");
			context.waitTicks(2);
			// countParticles() looks like "SQ 1500 T 1500": take the biggest number in it.
			int particles = context.computeOnClient(mc -> java.util.regex.Pattern.compile("\\d+").matcher(mc.particleEngine.countParticles())
				.results().mapToInt(m -> Integer.parseInt(m.group())).max().orElse(0));
			check("particles capped at " + Turbo.PARTICLE_LIMIT + " (were " + particles + ")", particles <= Turbo.PARTICLE_LIMIT + 50);
			System.out.println("[DNZ TEST] screenshot " + context.takeScreenshot("turbo").toAbsolutePath());

			context.runOnClient(mc -> DnzConfig.get().turbo = false);
			check("turbo off: far armor stand is drawn", !skipped(context, 40));
			context.runOnClient(mc -> DnzConfig.get().turbo = true);
		}
		if (!this.failures.isEmpty()) {
			throw new AssertionError("DNZ Turbo checks failed: " + this.failures);
		}
		System.out.println("[DNZ TEST] TURBO OK");
	}

	/** Would Turbo skip the armor stand about [distance] blocks from the player? */
	private static boolean skipped(ClientGameTestContext context, int distance) {
		return context.computeOnClient(mc -> {
			for (Entity entity : mc.level.entitiesForRendering()) {
				if (entity instanceof ArmorStand && Math.abs(entity.distanceTo(mc.player) - distance) < 2) {
					return Turbo.skipEntity(entity, mc.player.getX(), mc.player.getEyeY(), mc.player.getZ());
				}
			}
			throw new AssertionError("no armor stand " + distance + " blocks away");
		});
	}

	private void check(String what, boolean ok) {
		System.out.println("[DNZ TEST] " + (ok ? "ok   " : "FAIL ") + what);
		if (!ok) {
			this.failures.add(what);
		}
	}
}
