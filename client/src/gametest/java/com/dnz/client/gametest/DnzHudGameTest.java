package com.dnz.client.gametest;

import com.dnz.client.DnzConfig;
import com.dnz.client.gui.DnzHudScreen;
import com.dnz.client.gui.DnzHudTab;
import com.dnz.client.hud.CombatTracker;
import com.dnz.client.hud.DnzHud;
import com.dnz.client.hud.HudModule;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * Every module on: hits a zombie (reach, combo, target info), holds bread while hungry (food preview),
 * and takes screenshots of the HUD, the HUD editor and the Modules tab.
 */
public class DnzHudGameTest implements FabricClientGameTest {
	private final List<String> failures = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!System.getProperty("dnz.bench", "").isEmpty()) {
			return; // only the FPS benchmark runs
		}
		context.runOnClient(mc -> {
			for (HudModule m : DnzHud.modules()) {
				if (!m.id.equals("fullbright") && !m.id.equals("streamer") && !m.id.equals("togglesprint")) {
					m.setEnabled(true);
				}
			}
			DnzConfig.get().save();
		});
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			var server = world.getServer();
			server.runCommand("time set 6000");
			server.runCommand("give @p minecraft:bread 8");
			server.runCommand("give @p minecraft:totem_of_undying 2");
			server.runCommand("give @p minecraft:ender_pearl 16");
			server.runCommand("item replace entity @p armor.chest with minecraft:diamond_chestplate");
			server.runCommand("item replace entity @p armor.head with minecraft:iron_helmet");
			// Get hungry, then a little saturation back, so both food displays have something to show.
			server.runCommand("effect give @p minecraft:hunger 4 255");
			context.waitTicks(80);
			server.runCommand("effect clear @p minecraft:hunger");
			server.runCommand("effect give @p minecraft:saturation 1 2");
			server.runCommand("effect give @p minecraft:speed 60 1");
			server.runCommand("execute as @p at @s run summon minecraft:zombie ^ ^ ^2.5 {NoAI:1b,Health:20f}");
			context.waitTicks(10);
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ facing entity @e[type=minecraft:zombie,limit=1,sort=nearest] eyes");
			context.waitTicks(5);

			// Two hits, far enough apart that both do damage.
			for (int i = 0; i < 2; i++) {
				context.runOnClient(mc -> {
					Entity zombie = mc.level.getEntitiesOfClass(Zombie.class, mc.player.getBoundingBox().inflate(6)).stream().findFirst().orElse(null);
					if (zombie != null) {
						mc.gameMode.attack(mc.player, zombie);
					}
				});
				context.waitTicks(14);
			}
			double reach = context.computeOnClient(mc -> CombatTracker.reach());
			int combo = context.computeOnClient(mc -> CombatTracker.combo());
			boolean target = context.computeOnClient(mc -> CombatTracker.target(mc) != null);
			System.out.printf("[DNZ TEST] reach=%.2f combo=%d target=%s%n", reach, combo, target);
			check("reach measured (1.5 - 3.5)", reach > 1.5 && reach < 3.5);
			check("combo counts both hits", combo == 2);
			check("target info finds the zombie", target);
			int food = context.computeOnClient(mc -> mc.player.getFoodData().getFoodLevel());
			System.out.println("[DNZ TEST] food level " + food);
			check("player is hungry (food preview visible)", food < 20);
			System.out.println("[DNZ TEST] screenshot " + context.takeScreenshot("hud-all").toAbsolutePath());

			// Shulker preview: a filled box gets the grid, and the text list of its contents is left out.
			server.runCommand("item replace entity @p hotbar.8 with minecraft:red_shulker_box[container=[{slot:0,item:{id:\"minecraft:diamond\",count:64}},"
				+ "{slot:13,item:{id:\"minecraft:totem_of_undying\",count:1}}]]");
			context.waitTicks(5);
			boolean grid = context.computeOnClient(mc -> com.dnz.client.hud.ShulkerPreview.of(mc.player.getInventory().getItem(8)).isPresent());
			String lines = context.computeOnClient(mc -> mc.player.getInventory().getItem(8)
				.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player, net.minecraft.world.item.TooltipFlag.NORMAL).toString());
			System.out.println("[DNZ TEST] shulker grid=" + grid + " lines=" + lines);
			check("shulker preview shows the grid", grid);
			check("shulker text list hidden", !lines.contains("Diamond") && !lines.contains("Elmas"));
			context.runOnClient(mc -> DnzHud.module("shulkerpreview").setEnabled(false));
			check("shulker preview can be switched off",
				context.computeOnClient(mc -> com.dnz.client.hud.ShulkerPreview.of(mc.player.getInventory().getItem(8)).isEmpty()));
			context.runOnClient(mc -> DnzHud.module("shulkerpreview").setEnabled(true));

			// Turning a feature off from the Modules list works (zoom).
			context.runOnClient(mc -> DnzHud.module("zoom").setEnabled(false));
			check("zoom can be switched off", !context.computeOnClient(mc -> DnzHud.module("zoom").enabled()));
			context.runOnClient(mc -> DnzHud.module("zoom").setEnabled(true));

			context.setScreen(() -> new DnzHudScreen(null));
			context.waitTicks(5);
			System.out.println("[DNZ TEST] screenshot " + context.takeScreenshot("hud-editor").toAbsolutePath());
			context.setScreen(() -> new DnzHudTab(null));
			context.waitTicks(5);
			System.out.println("[DNZ TEST] screenshot " + context.takeScreenshot("hud-modules").toAbsolutePath());
			context.setScreen(() -> null);
		}
		if (!this.failures.isEmpty()) {
			throw new AssertionError("HUD checks failed: " + this.failures);
		}
		System.out.println("[DNZ TEST] HUD OK");
	}

	private void check(String what, boolean ok) {
		System.out.println("[DNZ TEST] " + (ok ? "ok   " : "FAIL ") + what);
		if (!ok) {
			this.failures.add(what);
		}
	}
}
