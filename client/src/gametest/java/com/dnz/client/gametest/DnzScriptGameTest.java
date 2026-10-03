package com.dnz.client.gametest;

import com.dnz.client.gui.DnzHudTab;
import com.dnz.client.gui.DnzScriptsTab;
import com.dnz.client.hud.DnzHud;
import com.dnz.client.script.ScriptManager;
import com.dnz.client.script.ScriptMod;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.DeathScreen;

/**
 * Plays through the DNZ Script features in a real game (gradlew runClientGameTest):
 * the example mods load, every event fires, commands and keys work, broken or endless mods are stopped
 * without freezing the game, and errors show up in the DNZ menu. Screenshots go to the run folder.
 */
public class DnzScriptGameTest implements FabricClientGameTest {
	/** Records every event it gets in a HUD element, so the test can read what happened. */
	private static final String EVENT_RECORDER = """
		let seen = [];
		function mark(what) { if (seen.indexOf(what) < 0) seen.push(what); }
		hud.add("log", "Test", () => seen.join(","));
		on("join", () => mark("join"));
		on("death", () => mark("death"));
		on("respawn", () => mark("respawn"));
		on("damage", (amount) => mark("damage"));
		on("chat", (text) => { if (text.indexOf("dnz-ping") >= 0) mark("chat"); });
		key("K", () => mark("key"));
		command("dnztest", (args) => mark("cmd:" + args.join("+")));
		after(1, () => mark("after"));
		""";

	private final List<String> failures = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!System.getProperty("dnz.bench", "").isEmpty()) {
			return; // only the FPS benchmark runs
		}
		Path scripts = context.computeOnClient(mc -> ScriptManager.folder());
		copyExamples(scripts);
		write(scripts.resolve("dnz-test.js"), EVENT_RECORDER);
		// Let the file watcher's reload happen now, not in the middle of the test.
		context.waitTicks(30);
		context.runOnClient(mc -> ScriptManager.reloadAll(false));
		for (String id : List.of("olum-sayaci", "saat", "can-uyarisi", "dnz-test")) {
			expectStatus(context, id, ScriptMod.Status.RUNNING);
		}

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("time set day");
			context.waitTicks(40);
			expectSeen(context, "join");
			expectSeen(context, "after");
			expect(!hud(context, "saat", "clock").isEmpty(), "clock HUD shows the time");
			expect("Saat".equals(mod(context, "saat").name), "single-file mod name comes from \"// @name\" (was " + mod(context, "saat").name + ")");
			expect(!hudOverlaps(context), "script HUD elements don't overlap each other");
			log("HUD: deaths=" + hud(context, "olum-sayaci", "deaths") + " clock=" + hud(context, "saat", "clock")
				+ " game=" + hud(context, "saat", "gametime"));
			screenshot(context, "dnz-1-hud");

			world.getServer().runCommand("say dnz-ping");
			context.waitTicks(10);
			expectSeen(context, "chat");

			world.getServer().runCommand("damage @p 4");
			context.waitTicks(10);
			expectSeen(context, "damage");

			// By name: key numbers differ between versions (26.3 uses SDL scancodes, 26.2 GLFW codes).
			context.getInput().pressKey(InputConstants.getKey("key.keyboard.k"));
			context.waitTicks(5);
			expectSeen(context, "key");

			context.runOnClient(mc -> mc.player.connection.sendCommand("dnztest bir iki"));
			context.waitTicks(5);
			expectSeen(context, "cmd:bir+iki");

			world.getServer().runCommand("kill @a");
			context.waitForScreen(DeathScreen.class);
			context.waitTicks(40);
			expectSeen(context, "death");
			expect("1".equals(hud(context, "olum-sayaci", "deaths")), "death counter is 1 after dying (was " + hud(context, "olum-sayaci", "deaths") + ")");
			screenshot(context, "dnz-2-death");
			context.clickScreenButton("deathScreen.respawn");
			context.waitFor(mc -> mc.gui.screen() == null);
			context.waitTicks(20);
			expectSeen(context, "respawn");

			context.runOnClient(mc -> mc.player.connection.sendCommand("olumsifirla"));
			context.waitTicks(5);
			expect("0".equals(hud(context, "olum-sayaci", "deaths")), "/olumsifirla resets the counter (was " + hud(context, "olum-sayaci", "deaths") + ")");
			screenshot(context, "dnz-3-chat");

			// Broken mods: a syntax error, an error every tick, and an endless loop. Saving them reloads everything.
			write(scripts.resolve("bozuk.js"), "on(\"tick\", () => {\n  let a = 1;\n  oops(;\n});\n");
			write(scripts.resolve("hatali.js"), "on(\"tick\", () => {\n  undefinedFunction();\n});\n");
			write(scripts.resolve("yavas.js"), "on(\"tick\", () => {\n  while (true) {}\n});\n");
			context.waitFor(mc -> ScriptManager.mods().stream().anyMatch(m -> m.id.equals("yavas")), 200);
			long start = System.currentTimeMillis();
			context.waitTicks(60);
			log("60 ticks with broken mods took " + (System.currentTimeMillis() - start) + " ms");
			expectStatus(context, "bozuk", ScriptMod.Status.ERROR);
			expectStatus(context, "hatali", ScriptMod.Status.ERROR);
			expectStatus(context, "yavas", ScriptMod.Status.ERROR);
			expectStatus(context, "saat", ScriptMod.Status.RUNNING);
			expect(line(context, "bozuk") == 3, "syntax error is reported on line 3 (was " + line(context, "bozuk") + ")");
			screenshot(context, "dnz-4-errors");

			context.setScreen(() -> new DnzScriptsTab(null));
			context.waitTicks(5);
			screenshot(context, "dnz-5-scripts-tab");
			context.setScreen(() -> new DnzHudTab(null));
			context.waitTicks(5);
			screenshot(context, "dnz-6-hud-tab");
			context.setScreen(() -> null);
		}

		if (!this.failures.isEmpty()) {
			throw new AssertionError(this.failures.size() + " DNZ Script checks failed:\n - " + String.join("\n - ", this.failures));
		}
		log("ALL OK");
	}

	// ------------------------------------------------------------------ helpers

	private static String hud(ClientGameTestContext context, String mod, String element) {
		return context.computeOnClient(mc -> ScriptManager.hudValue("script:" + mod + ":" + element));
	}

	private static boolean hudOverlaps(ClientGameTestContext context) {
		return context.computeOnClient(mc -> {
			int w = mc.getWindow().getGuiScaledWidth();
			int h = mc.getWindow().getGuiScaledHeight();
			List<String> ids = ScriptManager.hudIds();
			for (int i = 0; i < ids.size(); i++) {
				for (int j = i + 1; j < ids.size(); j++) {
					int[] a = DnzHud.screenPos(ids.get(i), w, h);
					int[] b = DnzHud.screenPos(ids.get(j), w, h);
					int[] sa = DnzHud.size(ids.get(i));
					int[] sb = DnzHud.size(ids.get(j));
					boolean overlap = a[0] < b[0] + sb[0] && b[0] < a[0] + sa[0] && a[1] < b[1] + sb[1] && b[1] < a[1] + sa[1];
					if (overlap) {
						log("HUD overlap: " + ids.get(i) + " and " + ids.get(j));
						return true;
					}
				}
			}
			return false;
		});
	}

	private static ScriptMod mod(ClientGameTestContext context, String id) {
		return context.computeOnClient(mc -> ScriptManager.mods().stream().filter(m -> m.id.equals(id)).findFirst().orElse(null));
	}

	private static int line(ClientGameTestContext context, String id) {
		ScriptMod mod = mod(context, id);
		return mod == null ? -1 : mod.errorLine();
	}

	private void expectStatus(ClientGameTestContext context, String id, ScriptMod.Status status) {
		ScriptMod mod = mod(context, id);
		String actual = mod == null ? "missing" : mod.status() + (mod.error() == null ? "" : " (" + mod.error() + ")");
		log("mod " + id + ": " + actual);
		expect(mod != null && mod.status() == status, "mod " + id + " should be " + status + " but is " + actual);
	}

	private void expectSeen(ClientGameTestContext context, String event) {
		String seen = hud(context, "dnz-test", "log");
		expect(List.of(seen.split(",")).contains(event), "event \"" + event + "\" should have fired (seen: " + seen + ")");
	}

	private void expect(boolean ok, String what) {
		log((ok ? "ok   " : "FAIL ") + what);
		if (!ok) {
			this.failures.add(what);
		}
	}

	private static void screenshot(ClientGameTestContext context, String name) {
		log("screenshot " + context.takeScreenshot(name).toAbsolutePath());
	}

	private static void log(String message) {
		System.out.println("[DNZ TEST] " + message);
	}

	private static void copyExamples(Path scripts) {
		Path examples = Path.of(System.getProperty("dnz.examples", "script-examples"));
		try (Stream<Path> files = Files.walk(examples)) {
			for (Path source : files.toList()) {
				Path target = scripts.resolve(examples.relativize(source).toString());
				if (Files.isDirectory(source)) {
					Files.createDirectories(target);
				} else {
					Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException("could not copy the example scripts from " + examples.toAbsolutePath(), e);
		}
	}

	private static void write(Path file, String text) {
		try {
			Files.writeString(file, text);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
