package com.dnz.client.gametest;

import com.dnz.client.DnzConfig;
import com.dnz.client.gui.DnzHudScreen;
import com.dnz.client.gui.DnzMenuScreen;
import com.dnz.client.gui.DnzShiftScreen;
import com.dnz.client.hud.DnzHud;
import com.dnz.client.hud.HudModule;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/**
 * Footage for the DNZ trailer (only with gradlew runClientGameTest -Ptrailer): one screenshot per game tick
 * while a scripted camera flies over the land, shows the HUD, a fight, the Right Shift menu and the HUD
 * editor.
 */
public class DnzTrailerGameTest implements FabricClientGameTest {
	/** The HUD that shows in the footage (a tidy set, not every module). */
	private static final List<String> HUD = List.of("fps", "cps", "coords", "direction", "keystrokes", "minimap", "armor", "target", "combo", "reach", "crosshair");

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!"trailer".equals(System.getProperty("dnz.bench", ""))) {
			return;
		}
		context.getInput().resizeWindow(1920, 1080);
		context.runOnClient(mc -> {
			mc.options.renderDistance().set(12);
			mc.options.framerateLimit().set(260);
			mc.options.enableVsync().set(false);
			mc.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
			for (HudModule m : DnzHud.modules()) {
				if (m.movable() || m.id.equals("crosshair")) {
					m.setEnabled(HUD.contains(m.id));
				}
			}
			DnzConfig.get().save();
		});
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false)
			.adjustSettings(ui -> ui.setSeed("20261001")).create()) {
			TestServerContext server = world.getServer();
			server.runCommand("time set 11000");
			server.runCommand("gamerule doDaylightCycle false");
			server.runCommand("weather clear 100000");
			server.runCommand("effect give @p minecraft:resistance 100000 255 true");
			server.runCommand("effect give @p minecraft:saturation 100000 255 true");
			world.getConnection().waitForChunksRender();
			context.waitTicks(200);
			Vec3 home = context.computeOnClient(mc -> mc.player.position());
			double hx = home.x, hy = home.y, hz = home.z;

			// 1 · Drone shot over the land: no HUD, high and slow.
			server.runCommand("gamemode spectator @p");
			context.runOnClient(mc -> { if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle(); });
			tp(server, hx - 60, hy + 40, hz - 60, -45, 22);
			context.waitTicks(120); // let the land around the start load
			for (int i = 0; i < 160; i++) {
				double k = i / 159.0;
				tp(server, hx - 60 + k * 110, hy + 40 - k * 12, hz - 60 + k * 70, (float) (-45 + k * 70), (float) (22 - k * 6));
				shot(context, "fly", i);
			}

			// 2 · The HUD in first person, standing on the ground and looking around.
			server.runCommand("gamemode survival @p");
			context.runOnClient(mc -> { if (mc.gui.hud.isHidden()) mc.gui.hud.toggle(); });
			server.runCommand("item replace entity @p armor.chest with minecraft:diamond_chestplate");
			server.runCommand("item replace entity @p armor.head with minecraft:diamond_helmet");
			server.runCommand("item replace entity @p armor.legs with minecraft:diamond_leggings");
			server.runCommand("item replace entity @p armor.feet with minecraft:diamond_boots");
			server.runCommand("item replace entity @p hotbar.0 with minecraft:diamond_sword");
			server.runCommand("item replace entity @p hotbar.1 with minecraft:end_crystal 64");
			server.runCommand("item replace entity @p hotbar.2 with minecraft:totem_of_undying");
			server.runCommand("item replace entity @p hotbar.3 with minecraft:golden_apple 16");
			tp(server, hx, hy, hz, 0, 5);
			context.waitTicks(40);
			for (int i = 0; i < 110; i++) {
				double k = i / 109.0;
				tp(server, hx, hy, hz, (float) (-30 + k * 60), (float) (8 - Math.sin(k * Math.PI) * 6));
				shot(context, "hud", i);
			}

			// 3 · A quick fight: target info, combo and reach count up.
			tp(server, hx, hy, hz, 0, 5);
			here(server, "summon minecraft:zombie ^ ^ ^2.6 {NoAI:1b,Health:40f,Attributes:[{id:\"minecraft:max_health\",base:40}]}");
			context.waitTicks(10);
			here(server, "tp @s ~ ~ ~ facing entity @e[type=minecraft:zombie,limit=1,sort=nearest] eyes");
			context.waitTicks(4);
			for (int i = 0; i < 90; i++) {
				if (i % 14 == 4) {
					context.runOnClient(mc -> {
						Entity zombie = mc.level.getEntitiesOfClass(Zombie.class, mc.player.getBoundingBox().inflate(6)).stream().findFirst().orElse(null);
						if (zombie != null) {
							mc.gameMode.attack(mc.player, zombie);
						}
					});
				}
				shot(context, "fight", i);
			}
			server.runCommand("kill @e[type=minecraft:zombie]");

			// 4 · Hitboxes on, with animals around.
			for (int i = 0; i < 8; i++) {
				here(server, "summon minecraft:cow ^" + (i % 4 * 2 - 3) + " ^ ^" + (5 + i / 4 * 3) + " {NoAI:1b}");
			}
			tp(server, hx, hy, hz, 0, 12);
			context.waitTicks(10);
			context.runOnClient(mc -> DnzHud.module("hitboxes").setEnabled(true));
			for (int i = 0; i < 60; i++) {
				double k = i / 59.0;
				tp(server, hx, hy, hz, (float) (-12 + k * 24), 12);
				shot(context, "hitbox", i);
			}
			context.runOnClient(mc -> DnzHud.module("hitboxes").setEnabled(false));

			// 5 · Right Shift: the DNZ screen, then the Mods page with modules switching on.
			tp(server, hx, hy, hz, 20, 0);
			context.waitTicks(4);
			context.runOnClient(mc -> mc.gui.setScreen(new DnzShiftScreen()));
			for (int i = 0; i < 45; i++) {
				shot(context, "shift", i);
			}
			context.runOnClient(mc -> mc.gui.setScreen(DnzMenuScreen.Tab.HUD.open(new DnzShiftScreen())));
			List<String> flip = List.of("clock", "speed", "ping", "biome", "memory", "lookat");
			for (int i = 0; i < 100; i++) {
				if (i >= 20 && i % 12 == 8 && (i - 20) / 12 < flip.size()) {
					String id = flip.get((i - 20) / 12);
					context.runOnClient(mc -> DnzHud.module(id).setEnabled(true));
				}
				shot(context, "menu", i);
			}

			// 6 · The HUD editor.
			context.runOnClient(mc -> mc.gui.setScreen(new DnzHudScreen(null)));
			for (int i = 0; i < 60; i++) {
				shot(context, "editor", i);
			}
			context.runOnClient(mc -> mc.gui.setScreen(null));
		}
		System.out.println("[DNZ TRAILER] done");
	}

	private static void tp(TestServerContext server, double x, double y, double z, float yaw, float pitch) {
		server.runCommand(String.format(Locale.ROOT, "tp @p %.3f %.3f %.3f %.2f %.2f", x, y, z, yaw, pitch));
	}

	private static void here(TestServerContext server, String command) {
		server.runCommand("execute as @p at @s run " + command);
	}

	private static void shot(ClientGameTestContext context, String name, int frame) {
		context.waitTicks(1);
		context.takeScreenshot(String.format(Locale.ROOT, "trailer-%s-%04d", name, frame));
	}
}
