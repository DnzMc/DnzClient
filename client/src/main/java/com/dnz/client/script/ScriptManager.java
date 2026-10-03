package com.dnz.client.script;

import com.dnz.client.DnzConfig;
import com.dnz.client.L;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.ScriptStackElement;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds, runs and reloads DNZ Script mods from "dnzclient/scripts" in the game folder:
 * <ul>
 *   <li>a folder with dnzmod.json + main.js,</li>
 *   <li>a ".dnzmod" file (the same, zipped; used for sharing),</li>
 *   <li>or a single ".js" file for quick tests.</li>
 * </ul>
 * Saving a file reloads the mods right away. Scripts only run while the player is in a world.
 */
public final class ScriptManager {
	static final Logger LOGGER = LoggerFactory.getLogger("DNZ Script");
	private static final int MAX_CODE_CHARS = 512 * 1024;
	private static final int MAX_RUNTIME_ERRORS = 25;
	private static final long HUD_CACHE_NANOS = 50_000_000L;
	/** "// @name Saat" style info lines at the top of a single-file mod. */
	private static final java.util.regex.Pattern HEADER = java.util.regex.Pattern.compile("//\\s*@(name|version|author|description|id)\\s+(.+)");

	private static final List<ScriptMod> MODS = new ArrayList<>();
	private static final Map<String, Object[]> HUD_CACHE = new HashMap<>();
	private static final Set<Path> WATCHED = new HashSet<>();
	private static String prelude;
	private static WatchService watcher;
	/** When a script file last changed (0 = nothing to reload). Set by the watcher thread. */
	private static volatile long changedAt;
	private static boolean dead;
	private static float lastHealth = -1;
	private static long lastErrorMessage;

	private ScriptManager() {
	}

	public static void init() {
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			reloadAll(false);
			startWatcher();
		});
		ClientTickEvents.END_CLIENT_TICK.register(ScriptManager::tick);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onMain(() -> {
			dead = false;
			lastHealth = -1;
			emitAll("join", null);
		}));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onMain(() -> emitAll("leave", null)));
		ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, time) -> onMain(() -> emitAll("chat", message.getString())));
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				onMain(() -> emitAll("chat", message.getString()));
			}
		});
		ClientSendMessageEvents.ALLOW_COMMAND.register(ScriptManager::onCommand);
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof DeathScreen && !dead) {
				dead = true;
				emitAll("death", null);
			}
		});
	}

	// ------------------------------------------------------------------ folders

	/** Where script mods live: .minecraft/dnzclient/scripts (inside the profile's game folder). */
	public static Path folder() {
		Path dir = FabricLoader.getInstance().getGameDir().resolve("dnzclient").resolve("scripts");
		try {
			Files.createDirectories(dir);
		} catch (IOException ignored) {
		}
		return dir;
	}

	private static Path dataFile(ScriptMod mod) {
		return FabricLoader.getInstance().getGameDir().resolve("dnzclient").resolve("script-data").resolve(mod.id + ".json");
	}

	// ------------------------------------------------------------------ public info (DNZ menu, HUD)

	public static List<ScriptMod> mods() {
		return List.copyOf(MODS);
	}

	/** HUD element ids of all running mods ("script:modid:elementid"). */
	public static List<String> hudIds() {
		List<String> ids = new ArrayList<>();
		for (ScriptMod mod : MODS) {
			if (mod.status == ScriptMod.Status.RUNNING) {
				mod.hud.keySet().forEach(id -> ids.add("script:" + mod.id + ":" + id));
			}
		}
		return ids;
	}

	public static String hudLabel(String hudId) {
		ScriptMod.HudEntry entry = hudEntry(hudId);
		return entry == null ? hudId : entry.label();
	}

	/** Current text of a script HUD element (asked again at most every 50 ms). */
	public static String hudValue(String hudId) {
		long now = System.nanoTime();
		Object[] cached = HUD_CACHE.get(hudId);
		if (cached != null && now - (long) cached[0] < HUD_CACHE_NANOS) {
			return (String) cached[1];
		}
		String text = "";
		ScriptMod mod = hudMod(hudId);
		ScriptMod.HudEntry entry = hudEntry(hudId);
		if (mod != null && entry != null) {
			if (entry.value() instanceof Function f) {
				Object result = invokeWith(mod, f, ScriptEngine.HUD_BUDGET_MS, cx -> new Object[0], true);
				text = result == null ? "" : (String) result;
			} else {
				text = String.valueOf(entry.value());
			}
		}
		if (text.length() > 64) {
			text = text.substring(0, 64);
		}
		HUD_CACHE.put(hudId, new Object[] {now, text});
		return text;
	}

	private static ScriptMod hudMod(String hudId) {
		String[] parts = hudId.split(":", 3);
		if (parts.length != 3) {
			return null;
		}
		for (ScriptMod mod : MODS) {
			if (mod.id.equals(parts[1]) && mod.status == ScriptMod.Status.RUNNING) {
				return mod;
			}
		}
		return null;
	}

	private static ScriptMod.HudEntry hudEntry(String hudId) {
		ScriptMod mod = hudMod(hudId);
		return mod == null ? null : mod.hud.get(hudId.split(":", 3)[2]);
	}

	// ------------------------------------------------------------------ loading

	/** Loads every mod again (after a file changed, or from the DNZ menu). */
	public static void reloadAll(boolean announce) {
		for (ScriptMod mod : MODS) {
			mod.clearRuntime();
		}
		MODS.clear();
		HUD_CACHE.clear();
		Set<String> ids = new HashSet<>();
		for (ScriptMod mod : discover()) {
			MODS.add(mod);
			if (mod.status == ScriptMod.Status.ERROR) {
				continue; // could not be read
			}
			if (!ids.add(mod.id)) {
				mod.status = ScriptMod.Status.ERROR;
				mod.error = L.t("script.duplicate", mod.id);
				continue;
			}
			if (DnzConfig.get().disabledScripts.contains(mod.id)) {
				mod.status = ScriptMod.Status.OFF;
			} else {
				load(mod);
			}
		}
		registerWatches();
		if (announce && Minecraft.getInstance().player != null) {
			long running = MODS.stream().filter(m -> m.status == ScriptMod.Status.RUNNING).count();
			long broken = MODS.stream().filter(m -> m.status == ScriptMod.Status.ERROR).count();
			Component line = Component.literal("[DNZ Script] ").withColor(0x7CE38B)
				.append(Component.literal(L.t("script.reloaded", running)).withColor(0xFFFFFF));
			if (broken > 0) {
				line = line.copy().append(Component.literal("  " + L.t("script.reloaded_errors", broken)).withColor(0xFF6B6B));
			}
			Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(line);
			MODS.stream().filter(m -> m.status == ScriptMod.Status.ERROR).forEach(ScriptManager::showError);
		}
	}

	/** Turns a mod on (runs it) or off (stops it) and remembers the choice. */
	public static void setEnabled(ScriptMod mod, boolean enabled) {
		Set<String> disabled = DnzConfig.get().disabledScripts;
		if (enabled) {
			disabled.remove(mod.id);
			load(mod);
		} else {
			disabled.add(mod.id);
			mod.clearRuntime();
			mod.status = ScriptMod.Status.OFF;
			mod.error = null;
			mod.errorLine = 0;
		}
		HUD_CACHE.clear();
		DnzConfig.get().save();
	}

	private static void load(ScriptMod mod) {
		mod.clearRuntime();
		mod.error = null;
		mod.errorLine = 0;
		try {
			ScriptEngine.run(ScriptEngine.LOAD_BUDGET_MS, cx -> {
				ScriptableObject scope = cx.initSafeStandardObjects();
				ScriptApi.install(mod, cx, scope);
				cx.evaluateString(scope, prelude(), "dnz-prelude.js", 1, null);
				mod.scope = scope;
				cx.evaluateString(scope, mod.code, mod.fileName, 1, null);
				if (!(scope.get("__emit", scope) instanceof Function emit)) {
					throw Context.reportRuntimeError("DNZ Script could not start (__emit was changed)");
				}
				mod.emit = emit;
				return null;
			});
			mod.status = ScriptMod.Status.RUNNING;
		} catch (ScriptEngine.Timeout e) {
			fail(mod, L.t("script.too_slow_load"), 0, true);
		} catch (RhinoException e) {
			fail(mod, e.details(), line(mod, e), true);
		} catch (RuntimeException | StackOverflowError e) {
			fail(mod, String.valueOf(e.getMessage()), 0, true);
		}
	}

	private static List<ScriptMod> discover() {
		List<ScriptMod> found = new ArrayList<>();
		try (Stream<Path> files = Files.list(folder())) {
			for (Path path : files.sorted().toList()) {
				try {
					ScriptMod mod = read(path);
					if (mod != null) {
						found.add(mod);
					}
				} catch (Exception e) {
					ScriptMod broken = create(new JsonObject(), baseName(path), path, path.getFileName().toString(), "");
					broken.status = ScriptMod.Status.ERROR;
					broken.error = L.t("script.unreadable", String.valueOf(e.getMessage()));
					found.add(broken);
				}
			}
		} catch (IOException e) {
			LOGGER.warn("Could not list script mods", e);
		}
		return found;
	}

	/** Reads one entry of the scripts folder, or null if it isn't a script mod. */
	private static ScriptMod read(Path path) throws IOException {
		String file = path.getFileName().toString();
		if (Files.isDirectory(path)) {
			Path metaFile = path.resolve("dnzmod.json");
			JsonObject meta = Files.exists(metaFile) ? JsonParser.parseString(Files.readString(metaFile)).getAsJsonObject() : new JsonObject();
			String main = string(meta, "main", "main.js");
			Path mainFile = path.resolve(main).normalize();
			if (!mainFile.startsWith(path) || !Files.isRegularFile(mainFile)) {
				return null;
			}
			return create(meta, file, path, main, readLimited(Files.readString(mainFile)));
		}
		String lower = file.toLowerCase(Locale.ROOT);
		if (lower.endsWith(".js")) {
			String code = readLimited(Files.readString(path));
			return create(headerMeta(code), baseName(path), path, file, code);
		}
		if (lower.endsWith(".dnzmod")) {
			try (ZipFile zip = new ZipFile(path.toFile())) {
				ZipEntry metaEntry = zip.getEntry("dnzmod.json");
				JsonObject meta = metaEntry == null ? new JsonObject() : JsonParser.parseString(readEntry(zip, metaEntry)).getAsJsonObject();
				String main = string(meta, "main", "main.js");
				ZipEntry mainEntry = zip.getEntry(main);
				if (mainEntry == null) {
					throw new IOException(main + " is missing");
				}
				return create(meta, baseName(path), path, main, readLimited(readEntry(zip, mainEntry)));
			}
		}
		return null;
	}

	/**
	 * Info of a single-file mod from its first comment lines:
	 * <pre>// @name Saat
	 * // @author DNZ</pre>
	 */
	private static JsonObject headerMeta(String code) {
		JsonObject meta = new JsonObject();
		for (String line : code.lines().limit(20).toList()) {
			String trimmed = line.trim();
			if (!trimmed.startsWith("//")) {
				if (!trimmed.isEmpty()) {
					break;
				}
				continue;
			}
			java.util.regex.Matcher m = HEADER.matcher(trimmed);
			if (m.matches()) {
				meta.addProperty(m.group(1).toLowerCase(Locale.ROOT), m.group(2).trim());
			}
		}
		return meta;
	}

	private static ScriptMod create(JsonObject meta, String fallbackId, Path source, String fileName, String code) {
		String id = string(meta, "id", fallbackId).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
		return new ScriptMod(id, string(meta, "name", fallbackId), string(meta, "version", "1.0.0"), string(meta, "author", ""),
			string(meta, "description", ""), source, fileName, code);
	}

	private static String readEntry(ZipFile zip, ZipEntry entry) throws IOException {
		if (entry.getSize() > MAX_CODE_CHARS * 4L) {
			throw new IOException(entry.getName() + " is too big");
		}
		try (InputStream in = zip.getInputStream(entry)) {
			return new String(in.readNBytes(MAX_CODE_CHARS * 4 + 1), StandardCharsets.UTF_8);
		}
	}

	private static String readLimited(String code) throws IOException {
		if (code.length() > MAX_CODE_CHARS) {
			throw new IOException("the code is too long (max 512 KB)");
		}
		return code;
	}

	private static String string(JsonObject meta, String key, String fallback) {
		return meta.has(key) && meta.get(key).isJsonPrimitive() && !meta.get(key).getAsString().isBlank() ? meta.get(key).getAsString().trim() : fallback;
	}

	private static String baseName(Path path) {
		String file = path.getFileName().toString();
		int dot = file.lastIndexOf('.');
		return dot > 0 ? file.substring(0, dot) : file;
	}

	private static String prelude() {
		if (prelude == null) {
			try (InputStream in = ScriptManager.class.getResourceAsStream("/assets/dnzclient/script/prelude.js")) {
				prelude = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			} catch (IOException | NullPointerException e) {
				throw new IllegalStateException("DNZ Script prelude missing", e);
			}
		}
		return prelude;
	}

	// ------------------------------------------------------------------ running

	private static void tick(Minecraft mc) {
		if (changedAt != 0 && System.currentTimeMillis() - changedAt > 400) {
			changedAt = 0;
			reloadAll(true);
		}
		LocalPlayer player = mc.player;
		if (MODS.isEmpty() || player == null) {
			return;
		}
		float health = player.getHealth();
		if (lastHealth >= 0 && health < lastHealth - 0.01F && health > 0) {
			emitAll("damage", (double) (lastHealth - health));
		}
		lastHealth = health;
		if (dead && health > 0 && !(mc.gui.screen() instanceof DeathScreen)) {
			dead = false;
			emitAll("respawn", null);
		}
		runTimers();
		emitAll("tick", null);
	}

	/** A key went down (from KeyboardHandlerMixin). Only counts while playing, not while typing in chat or a menu. */
	public static void onKeyPress(int keyCode) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.gui.screen() != null) {
			return;
		}
		for (ScriptMod mod : List.copyOf(MODS)) {
			if (mod.status != ScriptMod.Status.RUNNING) {
				continue;
			}
			for (ScriptMod.KeyHandler key : List.copyOf(mod.keys)) {
				if (key.code == keyCode) {
					invoke(mod, key.handler);
				}
			}
		}
	}

	static void emitAll(String event, Object value) {
		Object arg = value == null ? Undefined.instance : value;
		for (ScriptMod mod : List.copyOf(MODS)) {
			if (mod.status == ScriptMod.Status.RUNNING && mod.emit != null) {
				invoke(mod, mod.emit, event, arg);
			}
		}
	}

	private static void runTimers() {
		for (ScriptMod mod : List.copyOf(MODS)) {
			if (mod.status != ScriptMod.Status.RUNNING) {
				continue;
			}
			for (ScriptMod.Timer timer : List.copyOf(mod.timers)) {
				if (--timer.ticksLeft > 0) {
					continue;
				}
				if (timer.repeat) {
					timer.ticksLeft = timer.intervalTicks;
				} else {
					mod.timers.remove(timer);
				}
				invoke(mod, timer.handler);
				if (mod.status != ScriptMod.Status.RUNNING) {
					break;
				}
			}
		}
	}

	/** Runs a script command ("/name args...") locally instead of sending it to the server. */
	private static boolean onCommand(String command) {
		String[] parts = command.trim().split("\\s+");
		if (parts.length == 0 || parts[0].isEmpty()) {
			return true;
		}
		String name = parts[0].toLowerCase(Locale.ROOT);
		for (ScriptMod mod : List.copyOf(MODS)) {
			Function handler = mod.status == ScriptMod.Status.RUNNING ? mod.commands.get(name) : null;
			if (handler != null) {
				Object[] args = Arrays.copyOfRange(parts, 1, parts.length, Object[].class);
				invokeWith(mod, handler, ScriptEngine.CALL_BUDGET_MS, cx -> new Object[] {cx.newArray(mod.scope, args)}, false);
				return false;
			}
		}
		return true;
	}

	private static Object invoke(ScriptMod mod, Function fn, Object... args) {
		return invokeWith(mod, fn, ScriptEngine.CALL_BUDGET_MS, cx -> args, false);
	}

	/**
	 * Calls a script function with a time limit and catches its errors.
	 * With [asText] the result is turned into text inside the script context (for HUD values).
	 */
	private static Object invokeWith(ScriptMod mod, Function fn, long budgetMs, java.util.function.Function<Context, Object[]> args, boolean asText) {
		if (mod.scope == null) {
			return null;
		}
		try {
			return ScriptEngine.run(budgetMs, cx -> {
				Object result = fn.call(cx, mod.scope, mod.scope, args.apply(cx));
				return asText ? (Undefined.isUndefined(result) || result == null ? "" : Context.toString(result)) : result;
			});
		} catch (ScriptEngine.Timeout e) {
			fail(mod, L.t("script.too_slow"), 0, false);
		} catch (RhinoException e) {
			fail(mod, e.details(), line(mod, e), false);
		} catch (RuntimeException | StackOverflowError e) {
			fail(mod, String.valueOf(e.getMessage()), 0, false);
		}
		return null;
	}

	// ------------------------------------------------------------------ errors

	/** Line in the mod's own file where the error happened (not in DNZ's code). */
	private static int line(ScriptMod mod, RhinoException e) {
		for (ScriptStackElement element : e.getScriptStack()) {
			if (mod.fileName.equals(element.fileName)) {
				return element.lineNumber;
			}
		}
		return mod.fileName.equals(e.sourceName()) ? e.lineNumber() : 0;
	}

	private static void fail(ScriptMod mod, String message, int line, boolean loading) {
		mod.error = message;
		mod.errorLine = line;
		LOGGER.warn("[{}] {}{}", mod.id, line > 0 ? "line " + line + ": " : "", message);
		if (loading) {
			mod.clearRuntime();
			mod.status = ScriptMod.Status.ERROR;
		} else if (++mod.runtimeErrors >= MAX_RUNTIME_ERRORS) {
			mod.clearRuntime();
			mod.status = ScriptMod.Status.ERROR;
			mod.error = L.t("script.stopped", message);
		}
		showError(mod);
	}

	/** Red chat line about a broken mod (at most one every 3 seconds, so errors don't flood the chat). */
	private static void showError(ScriptMod mod) {
		Minecraft mc = Minecraft.getInstance();
		long now = System.currentTimeMillis();
		if (mc.player == null || mod.error == null || now - lastErrorMessage < 3000) {
			return;
		}
		lastErrorMessage = now;
		String where = mod.errorLine > 0 ? " (" + L.t("script.line", mod.errorLine) + ")" : "";
		mc.gui.hud.getChat().addClientSystemMessage(Component.literal("[DNZ Script] " + mod.name + ": ").withColor(0xFF6B6B)
			.append(Component.literal(mod.error + where).withColor(0xFFB0B0)));
	}

	// ------------------------------------------------------------------ storage

	static JsonObject storage(ScriptMod mod) {
		if (mod.storage == null) {
			Path file = dataFile(mod);
			try {
				mod.storage = Files.exists(file) ? JsonParser.parseString(Files.readString(file)).getAsJsonObject() : new JsonObject();
			} catch (Exception e) {
				mod.storage = new JsonObject();
			}
		}
		return mod.storage;
	}

	static void saveStorage(ScriptMod mod) {
		Path file = dataFile(mod);
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, storage(mod).toString());
		} catch (IOException e) {
			LOGGER.warn("[{}] Could not save storage", mod.id, e);
		}
	}

	// ------------------------------------------------------------------ hot reload

	private static void startWatcher() {
		try {
			watcher = FileSystems.getDefault().newWatchService();
		} catch (IOException e) {
			LOGGER.warn("Script hot reload is not available", e);
			return;
		}
		registerWatches();
		Thread thread = new Thread(() -> {
			while (true) {
				WatchKey key;
				try {
					key = watcher.take();
				} catch (InterruptedException | ClosedWatchServiceException e) {
					return;
				}
				key.pollEvents();
				key.reset();
				changedAt = System.currentTimeMillis();
			}
		}, "DNZ Script watcher");
		thread.setDaemon(true);
		thread.start();
	}

	/** Watches the scripts folder and every mod folder in it. */
	private static void registerWatches() {
		if (watcher == null) {
			return;
		}
		List<Path> folders = new ArrayList<>();
		folders.add(folder());
		try (Stream<Path> files = Files.list(folder())) {
			files.filter(Files::isDirectory).forEach(folders::add);
		} catch (IOException ignored) {
		}
		for (Path dir : folders) {
			if (WATCHED.add(dir)) {
				try {
					dir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
				} catch (IOException e) {
					WATCHED.remove(dir);
				}
			}
		}
	}

	private static void onMain(Runnable task) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.isSameThread()) {
			task.run();
		} else {
			mc.execute(task);
		}
	}
}
