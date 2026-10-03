package com.dnz.client.script;

import static com.dnz.client.script.JsFunction.VOID;
import static com.dnz.client.script.JsFunction.arg;
import static com.dnz.client.script.JsFunction.function;
import static com.dnz.client.script.JsFunction.number;
import static com.dnz.client.script.JsFunction.text;

import com.dnz.client.gui.Theme;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

/**
 * The Java side of the DNZ Script API: the "__n" object that prelude.js builds the friendly API on.
 * Every function checks its input and has limits, so a mod can't spam chat or slow the game down.
 * Scripts only get information the player can already see (no other players' positions, no x-ray...).
 */
final class ScriptApi {
	static final List<String> EVENTS = List.of("tick", "join", "leave", "death", "respawn", "damage", "chat");

	private static final int MAX_HUD = 16;
	private static final int MAX_KEYS = 16;
	private static final int MAX_COMMANDS = 16;
	private static final int MAX_TIMERS = 64;
	private static final int MAX_STORAGE_CHARS = 64 * 1024;
	private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,32}");

	private ScriptApi() {
	}

	static void install(ScriptMod mod, Context cx, Scriptable scope) {
		Scriptable n = cx.newObject(scope);
		ScriptableObject.putProperty(n, "events", cx.newArray(scope, EVENTS.toArray()));

		def(n, scope, "log", (c, s, a) -> {
			String line = text(a, 0);
			mod.addLog(line);
			ScriptManager.LOGGER.info("[{}] {}", mod.id, line);
			return VOID;
		});

		// ---- timers, keys, commands
		def(n, scope, "timer", (c, s, a) -> {
			double seconds = number(a, 0, -1);
			boolean repeat = Context.toBoolean(arg(a, 2));
			String what = repeat ? "every()" : "after()";
			if (!(seconds > 0)) {
				throw error(what + ": the time must be more than 0 seconds");
			}
			Function handler = function(a, 1, what);
			if (mod.timers.size() >= MAX_TIMERS) {
				throw error(what + ": too many timers (max " + MAX_TIMERS + ")");
			}
			int ticks = Math.max(1, (int) Math.round(seconds * 20));
			ScriptMod.Timer timer = new ScriptMod.Timer(mod.nextTimerId++, ticks, repeat, handler);
			mod.timers.add(timer);
			return timer.id;
		});
		def(n, scope, "cancel", (c, s, a) -> {
			int id = (int) number(a, 0, -1);
			mod.timers.removeIf(t -> t.id == id);
			return VOID;
		});
		def(n, scope, "key", (c, s, a) -> {
			String name = text(a, 0).trim();
			Function handler = function(a, 1, "key(\"" + name + "\")");
			if (mod.keys.size() >= MAX_KEYS) {
				throw error("key(): too many keys (max " + MAX_KEYS + ")");
			}
			mod.keys.add(new ScriptMod.KeyHandler(name, keyCode(name), handler));
			return VOID;
		});
		def(n, scope, "command", (c, s, a) -> {
			String name = text(a, 0).trim().toLowerCase(Locale.ROOT);
			if (name.startsWith("/")) {
				name = name.substring(1);
			}
			if (!ID.matcher(name).matches()) {
				throw error("command(): the name may only use a-z, 0-9, - and _ (e.g. command(\"hello\", ...))");
			}
			Function handler = function(a, 1, "command(\"" + name + "\")");
			if (mod.commands.size() >= MAX_COMMANDS && !mod.commands.containsKey(name)) {
				throw error("command(): too many commands (max " + MAX_COMMANDS + ")");
			}
			mod.commands.put(name, handler);
			return VOID;
		});

		// ---- HUD
		def(n, scope, "hudAdd", (c, s, a) -> {
			String id = text(a, 0).trim().toLowerCase(Locale.ROOT);
			if (!ID.matcher(id).matches()) {
				throw error("hud.add(): the id may only use a-z, 0-9, - and _ (e.g. hud.add(\"deaths\", ...))");
			}
			if (mod.hud.size() >= MAX_HUD && !mod.hud.containsKey(id)) {
				throw error("hud.add(): too many HUD elements (max " + MAX_HUD + ")");
			}
			String label = text(a, 1);
			Object value = arg(a, 2);
			Object stored = value instanceof Function ? value : Undefined.isUndefined(value) ? "" : Context.toString(value);
			mod.hud.put(id, new ScriptMod.HudEntry(id, label.isEmpty() ? id : limit(label, 32), stored));
			return VOID;
		});
		def(n, scope, "hudRemove", (c, s, a) -> {
			mod.hud.remove(text(a, 0).trim().toLowerCase(Locale.ROOT));
			return VOID;
		});

		// ---- screen
		def(n, scope, "title", (c, s, a) -> {
			Minecraft mc = Minecraft.getInstance();
			int color = color(text(a, 2));
			int ticks = (int) Math.round(clamp(number(a, 3, 3), 0.5, 30) * 20);
			mc.gui.hud.setTimes(5, ticks, 10);
			mc.gui.hud.setSubtitle(Component.literal(limit(text(a, 1), 100)).withColor(color));
			mc.gui.hud.setTitle(Component.literal(limit(text(a, 0), 64)).withColor(color));
			return VOID;
		});
		def(n, scope, "actionbar", (c, s, a) -> {
			Minecraft.getInstance().gui.hud.setOverlayMessage(Component.literal(limit(text(a, 0), 128)).withColor(color(text(a, 1))), false);
			return VOID;
		});
		def(n, scope, "toast", (c, s, a) -> {
			String body = text(a, 1);
			SystemToast.addOrUpdate(Minecraft.getInstance().gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
				Component.literal(limit(text(a, 0), 64)), body.isEmpty() ? null : Component.literal(limit(body, 128)));
			return VOID;
		});

		// ---- chat
		def(n, scope, "chatShow", (c, s, a) -> {
			Minecraft mc = Minecraft.getInstance();
			int color = color(text(a, 1));
			if (mc.player == null) {
				return VOID; // chat only exists in a world
			}
			if (!mod.allow("chat.show", 20)) {
				throw error("chat.show(): too many messages (max 20 per second)");
			}
			// Always starts with the mod's name, so a script can't pretend to be the server or another player.
			Component line = Component.literal("[" + mod.name + "] ").withColor(Theme.accentInfo().rgb())
				.append(Component.literal(limit(text(a, 0), 256)).withColor(color));
			mc.gui.hud.getChat().addClientSystemMessage(line);
			return VOID;
		});
		def(n, scope, "chatSend", (c, s, a) -> {
			Minecraft mc = Minecraft.getInstance();
			String message = text(a, 0).trim();
			if (mc.player == null || mc.getConnection() == null) {
				throw error("chat.send(): only works in a world");
			}
			if (message.isEmpty()) {
				return VOID;
			}
			if (message.length() > 256) {
				throw error("chat.send(): the message is too long (max 256 characters)");
			}
			long now = System.currentTimeMillis();
			if (now - mod.lastChatSend < 2000) {
				throw error("chat.send(): too fast (max 1 message every 2 seconds, so you don't get kicked for spam)");
			}
			mod.lastChatSend = now;
			if (message.startsWith("/")) {
				mc.getConnection().sendCommand(message.substring(1));
			} else {
				mc.getConnection().sendChat(message);
			}
			return VOID;
		});

		// ---- sound
		def(n, scope, "sound", (c, s, a) -> {
			String raw = text(a, 0).trim().toLowerCase(Locale.ROOT);
			Identifier id = Identifier.tryParse(raw.contains(":") ? raw : "minecraft:" + raw);
			if (id == null || raw.isEmpty()) {
				throw error("sound.play(): \"" + raw + "\" is not a sound name (e.g. \"entity.player.levelup\")");
			}
			if (!mod.allow("sound", 10)) {
				throw error("sound.play(): too many sounds (max 10 per second)");
			}
			float volume = (float) clamp(number(a, 1, 1), 0, 1);
			float pitch = (float) clamp(number(a, 2, 1), 0.5, 2);
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(id), pitch, volume));
			return VOID;
		});

		// ---- information (read-only)
		def(n, scope, "player", (c, s, a) -> playerValue(text(a, 0)));
		def(n, scope, "game", (c, s, a) -> gameValue(text(a, 0)));

		// ---- storage
		def(n, scope, "storageGet", (c, s, a) -> {
			JsonElement value = ScriptManager.storage(mod).get(storageName(text(a, 0)));
			return value == null ? null : value.toString();
		});
		def(n, scope, "storageSet", (c, s, a) -> {
			String name = storageName(text(a, 0));
			Object value = arg(a, 1);
			var data = ScriptManager.storage(mod);
			if (value == null || Undefined.isUndefined(value)) {
				data.remove(name);
			} else {
				data.add(name, JsonParser.parseString(Context.toString(value)));
				if (data.toString().length() > MAX_STORAGE_CHARS) {
					data.remove(name);
					throw error("storage.set(): too much saved data (max 64 KB per mod)");
				}
			}
			ScriptManager.saveStorage(mod);
			return VOID;
		});

		ScriptableObject.putProperty(scope, "__n", n);
	}

	private static void def(Scriptable target, Scriptable scope, String name, JsFunction.Body body) {
		ScriptableObject.putProperty(target, name, new JsFunction(scope, name, 3, body));
	}

	/** An error the script can catch; shown to the player with the mod's line number. */
	static RuntimeException error(String message) {
		return Context.reportRuntimeError(message);
	}

	private static Object playerValue(String field) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if ("inGame".equals(field)) {
			return p != null;
		}
		if (p == null) {
			return null;
		}
		return switch (field) {
			case "name" -> p.getScoreboardName();
			case "health" -> round(p.getHealth(), 1);
			case "maxHealth" -> round(p.getMaxHealth(), 1);
			case "food" -> p.getFoodData().getFoodLevel();
			case "armor" -> p.getArmorValue();
			case "xp" -> p.experienceLevel;
			case "x" -> round(p.getX(), 2);
			case "y" -> round(p.getY(), 2);
			case "z" -> round(p.getZ(), 2);
			case "dimension" -> {
				Identifier id = p.level().dimension().identifier();
				yield "minecraft".equals(id.getNamespace()) ? id.getPath() : id.toString();
			}
			default -> throw error("player: unknown value \"" + field + "\"");
		};
	}

	private static Object gameValue(String field) {
		Minecraft mc = Minecraft.getInstance();
		return switch (field) {
			case "fps" -> mc.getFps();
			case "ping" -> {
				if (mc.player == null || mc.getConnection() == null) {
					yield 0;
				}
				PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
				yield info == null ? 0 : info.getLatency();
			}
			case "time" -> mc.level == null ? 0 : (int) Math.floorMod(mc.level.getDefaultClockTime(), 24000L);
			case "day" -> mc.level == null ? 0 : (int) (mc.level.getDefaultClockTime() / 24000L);
			case "server" -> mc.getCurrentServer() != null ? mc.getCurrentServer().ip : mc.level != null ? "singleplayer" : "";
			case "version" -> SharedConstants.getCurrentVersion().name();
			case "language" -> mc.getLanguageManager().getSelected();
			default -> throw error("game: unknown value \"" + field + "\"");
		};
	}

	/** GLFW key code for names like "K", "F6", "LEFT_ALT", "SPACE". */
	private static int keyCode(String name) {
		String keyName = "key.keyboard." + name.trim().toLowerCase(Locale.ROOT).replace('_', '.').replace(' ', '.');
		try {
			InputConstants.Key key = InputConstants.getKey(keyName);
			if (key != InputConstants.UNKNOWN && key.getValue() >= 0) {
				return key.getValue();
			}
		} catch (RuntimeException ignored) {
			// unknown names can throw
		}
		throw error("key(): unknown key \"" + name + "\" (use a letter like \"K\", or \"F6\", \"LEFT_ALT\", \"SPACE\")");
	}

	private static String storageName(String name) {
		if (name.isEmpty() || name.length() > 64) {
			throw error("storage: the name must be 1-64 characters");
		}
		return name;
	}

	/** Colors by name ("red", "gold", "accent"...) or hex ("#ff8800"). */
	static int color(String name) {
		String n = name.trim().toLowerCase(Locale.ROOT);
		if (n.isEmpty()) {
			return 0xFFFFFF;
		}
		if (n.matches("#[0-9a-f]{6}")) {
			return Integer.parseInt(n.substring(1), 16);
		}
		return switch (n) {
			case "white" -> 0xFFFFFF;
			case "red" -> 0xFF5555;
			case "green" -> 0x55FF55;
			case "blue" -> 0x5555FF;
			case "yellow" -> 0xFFFF55;
			case "gold", "orange" -> 0xFFAA00;
			case "aqua", "cyan" -> 0x55FFFF;
			case "purple", "pink", "magenta" -> 0xFF55FF;
			case "gray", "grey" -> 0xAAAAAA;
			case "dark_gray", "dark_grey" -> 0x555555;
			case "black" -> 0x000000;
			case "accent" -> Theme.accentInfo().rgb();
			default -> throw error("unknown color \"" + name + "\" (use red, green, blue, yellow, gold, aqua, purple, gray, white, accent or \"#ff8800\")");
		};
	}

	private static String limit(String text, int max) {
		return text.length() <= max ? text : text.substring(0, max);
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double round(double value, int decimals) {
		double scale = Math.pow(10, decimals);
		return Math.round(value * scale) / scale;
	}
}
