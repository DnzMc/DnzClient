package com.dnz.client.gametest;

import com.dnz.client.DnzConfig;
import com.dnz.client.hud.DnzHud;
import com.dnz.client.hud.HudModule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

/**
 * FPS benchmark (only with gradlew runClientGameTest -Pbench=base|full): the same busy scene every time — a big leaf
 * block (forest), about 1900 chests with signs (block entities, like on a market/spawner server), 200 armor stands,
 * 80 animals — at 1600x900, render distance 12, unlimited FPS. Measures average FPS and the 1% lows (stutters) for
 * DNZ defaults, all DNZ HUD modules on, and DNZ Turbo off. Results go to dnz-benchmark-<base|full>.txt.
 */
public class DnzBenchmarkGameTest implements FabricClientGameTest {
	private static final int WARMUP_TICKS = 200;
	private static final int MEASURE_TICKS = 600;

	private static volatile boolean recording;
	private static long last;
	private static final long[] FRAMES = new long[100_000];
	private static int count;

	@Override
	public void runTest(ClientGameTestContext context) {
		String bench = System.getProperty("dnz.bench", "");
		if (bench.isEmpty()) {
			return;
		}
		LevelRenderEvents.START_MAIN.register(ctx -> {
			if (!recording) {
				return;
			}
			long now = System.nanoTime();
			if (last != 0 && count < FRAMES.length) {
				FRAMES[count++] = now - last;
			}
			last = now;
		});
		// -Pbench_size=2560x1440 measures at the player's own resolution.
		String[] size = System.getProperty("dnz.benchSize", "1600x900").split("x");
		context.getInput().resizeWindow(Integer.parseInt(size[0]), Integer.parseInt(size[1]));
		context.runOnClient(mc -> {
			mc.options.renderDistance().set(12);
			mc.options.simulationDistance().set(8);
			mc.options.framerateLimit().set(260);
			mc.options.enableVsync().set(false);
			// Nobody touches the keyboard in a test: without this Minecraft drops to 30 FPS after 30 s (AFK limit).
			mc.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
			DnzConfig.get().turbo = true;
		});

		List<String> report = new ArrayList<>();
		report.add("DNZ FPS benchmark: " + bench + "  (" + String.join("x", size) + ", render distance 12)");
		// A normal Minecraft world (hills, trees, caves, water), always the same seed so runs can be compared.
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false)
			.adjustSettings(ui -> ui.setSeed("20261001")).create()) {
			var server = world.getServer();
			server.runCommand("gamemode spectator @p");
			server.runCommand("time set 6000");
			context.waitTicks(200); // world loads (render distance 12 takes longer than the usual wait)
			report.add("GPU: " + context.computeOnClient(mc -> {
				var info = com.mojang.blaze3d.systems.RenderSystem.getDevice().getDeviceInfo();
				return info.name() + " (" + info.vendorName() + ", " + info.backendName() + ", driver " + info.driverInfo() + ")";
			}));
			// Light scene first: the normal world as it is, nothing built yet. A normal world keeps generating land
			// for a while after joining; measuring during that would mostly measure the world generator.
			context.waitTicks(1200);
			report.add(measure(context, "Light: normal world"));
			// Heavy scene, built around where the player stands (~ = relative to the player).
			for (int x = -48; x < 48; x += 24) {
				here(server, "fill ~" + x + " ~ ~30 ~" + (x + 23) + " ~15 ~70 minecraft:oak_leaves[persistent=true]");
			}
			for (int z = -40; z < 0; z += 20) {
				here(server, "fill ~-30 ~ ~" + z + " ~30 ~ ~" + (z + 19) + " minecraft:chest");
				here(server, "fill ~-30 ~1 ~" + z + " ~30 ~1 ~" + (z + 19) + " minecraft:oak_sign");
			}
			for (int i = 0; i < 200; i++) {
				here(server, "summon minecraft:armor_stand ~" + (i % 20 * 3 - 30) + " ~2 ~" + (i / 20 * 3 - 70) + " {NoGravity:1b}");
			}
			for (int i = 0; i < 80; i++) {
				here(server, "summon minecraft:cow ~" + (i % 10 * 3 - 15) + " ~ ~" + (i / 10 * 3 + 5) + " {NoAI:1b}");
			}
			here(server, "tp @s ~ ~22 ~-75 0 25");
			context.waitTicks(200);
			context.waitTicks(WARMUP_TICKS); // let Java warm up and chunks settle

			report.add(measure(context, "Heavy: DNZ default"));
			context.runOnClient(mc -> {
				// Only the HUD panels (features like fullbright or streamer mode stay as they are).
				for (HudModule m : DnzHud.modules()) {
					if (m.movable()) {
						m.setEnabled(true);
					}
				}
			});
			context.waitTicks(40);
			DnzHud.PROFILE.clear();
			DnzHud.profiling = true;
			report.add(measure(context, "DNZ all HUD panels on"));
			DnzHud.profiling = false;
			// Which panels cost the most (time on the game's thread per frame).
			DnzHud.PROFILE.entrySet().stream()
				.sorted((a, b) -> Long.compare(b.getValue()[0] / Math.max(1, b.getValue()[1]), a.getValue()[0] / Math.max(1, a.getValue()[1])))
				.limit(12)
				.forEach(e -> report.add(String.format("   HUD %-16s %6.1f microseconds per frame", e.getKey(), e.getValue()[0] / 1000.0 / Math.max(1, e.getValue()[1]))));
			context.runOnClient(mc -> DnzConfig.get().turbo = false);
			context.waitTicks(40);
			report.add(measure(context, "DNZ Turbo off"));
			context.runOnClient(mc -> DnzConfig.get().turbo = true);
			System.out.println("[DNZ BENCH] screenshot " + context.takeScreenshot("benchmark-" + bench).toAbsolutePath());
		}
		report.forEach(line -> System.out.println("[DNZ BENCH] " + line));
		try {
			Files.write(Path.of("dnz-benchmark-" + bench + ".txt"), report);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/** Runs a command at the player's position. */
	private static void here(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server, String command) {
		server.runCommand("execute as @p at @s run " + command);
	}

	private static String measure(ClientGameTestContext context, String label) {
		count = 0;
		last = 0;
		recording = true;
		long start = System.nanoTime();
		context.waitTicks(MEASURE_TICKS);
		long wall = System.nanoTime() - start;
		recording = false;
		long[] frames = Arrays.copyOf(FRAMES, count);
		Arrays.sort(frames);
		double avgFps = count / (wall / 1e9);
		// 1% low: the FPS of the slowest 1% of frames (stutters).
		int from = Math.max(0, (int) (frames.length * 0.99));
		double slow = 0;
		for (int i = from; i < frames.length; i++) {
			slow += frames[i];
		}
		double lowFps = frames.length > from ? 1e9 / (slow / (frames.length - from)) : 0;
		double worstMs = frames.length > 0 ? frames[frames.length - 1] / 1e6 : 0;
		return String.format("%-24s avg %6.0f FPS   1%% low %6.0f FPS   worst frame %5.1f ms   (%d frames)", label, avgFps, lowFps, worstMs, count);
	}
}
