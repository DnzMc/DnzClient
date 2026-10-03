package com.dnz.client.script;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;

/** One DNZ Script mod: its info from dnzmod.json and everything it registered while running. */
public final class ScriptMod {
	public enum Status { RUNNING, OFF, ERROR }

	public final String id;
	public final String name;
	public final String version;
	public final String author;
	public final String description;
	/** Folder, .dnzmod file or single .js file the mod was loaded from. */
	public final Path source;
	/** File name shown in error messages (e.g. "main.js"). */
	final String fileName;
	final String code;

	Status status = Status.OFF;
	String error;
	int errorLine;
	int runtimeErrors;

	Scriptable scope;
	Function emit;

	final Map<String, HudEntry> hud = new LinkedHashMap<>();
	final Map<String, Function> commands = new LinkedHashMap<>();
	final List<KeyHandler> keys = new ArrayList<>();
	final List<Timer> timers = new ArrayList<>();
	final Deque<String> log = new ArrayDeque<>();
	/** Saved values (storage.get/set), loaded on first use. */
	JsonObject storage;
	int nextTimerId = 1;
	long lastChatSend;
	private final Map<String, long[]> rate = new HashMap<>();

	record HudEntry(String id, String label, Object value) {
	}

	static final class KeyHandler {
		final String name;
		final int code;
		final Function handler;

		KeyHandler(String name, int code, Function handler) {
			this.name = name;
			this.code = code;
			this.handler = handler;
		}
	}

	static final class Timer {
		final int id;
		final int intervalTicks;
		final boolean repeat;
		final Function handler;
		int ticksLeft;

		Timer(int id, int intervalTicks, boolean repeat, Function handler) {
			this.id = id;
			this.intervalTicks = intervalTicks;
			this.repeat = repeat;
			this.handler = handler;
			this.ticksLeft = intervalTicks;
		}
	}

	ScriptMod(String id, String name, String version, String author, String description, Path source, String fileName, String code) {
		this.id = id;
		this.name = name;
		this.version = version;
		this.author = author;
		this.description = description;
		this.source = source;
		this.fileName = fileName;
		this.code = code;
	}

	public Status status() {
		return this.status;
	}

	/** Error text for the player, or null. */
	public String error() {
		return this.error;
	}

	/** Line of the error in the mod's file (0 if unknown). */
	public int errorLine() {
		return this.errorLine;
	}

	/** Last lines the mod wrote with log(). */
	public List<String> logLines() {
		return List.copyOf(this.log);
	}

	/** At most [perSecond] uses of [what] per second (sounds, chat lines...). */
	boolean allow(String what, int perSecond) {
		long second = System.currentTimeMillis() / 1000;
		long[] counter = this.rate.computeIfAbsent(what, k -> new long[2]);
		if (counter[0] != second) {
			counter[0] = second;
			counter[1] = 0;
		}
		return ++counter[1] <= perSecond;
	}

	void addLog(String line) {
		if (this.log.size() >= 100) {
			this.log.removeFirst();
		}
		this.log.addLast(line);
	}

	/** Forgets everything the running script registered. */
	void clearRuntime() {
		this.scope = null;
		this.emit = null;
		this.hud.clear();
		this.commands.clear();
		this.keys.clear();
		this.timers.clear();
		this.runtimeErrors = 0;
	}
}
