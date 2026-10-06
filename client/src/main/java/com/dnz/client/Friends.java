package com.dnz.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

/**
 * DNZ friends, without any server: the friend list is kept on this computer (config/dnzclient-friends.json). On the
 * server you play on, friends get a star in the player list, and a note shows when one joins or leaves.
 * Nothing is sent anywhere.
 */
public final class Friends {
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("dnzclient-friends.json");
	private static List<String> names;
	/** Friends on the current server (lower-case names), updated once a second. */
	private static Set<String> here = Set.of();
	private static String server;

	private Friends() {
	}

	/** Friend names in the order they were added. */
	public static synchronized List<String> list() {
		if (names == null) {
			names = new ArrayList<>();
			try {
				if (Files.exists(FILE)) {
					for (JsonElement e : JsonParser.parseString(Files.readString(FILE)).getAsJsonObject().getAsJsonArray("friends")) {
						names.add(e.getAsString());
					}
				}
			} catch (Exception ignored) {
			}
		}
		return List.copyOf(names);
	}

	public static synchronized boolean isFriend(String name) {
		String lower = name.toLowerCase(Locale.ROOT);
		for (String n : list()) {
			if (n.toLowerCase(Locale.ROOT).equals(lower)) {
				return true;
			}
		}
		return false;
	}

	/** Adds a Minecraft name (3 to 16 letters, digits or _); false when it is not a valid name or already there. */
	public static synchronized boolean add(String name) {
		String n = name.trim();
		if (!n.matches("[A-Za-z0-9_]{3,16}") || isFriend(n)) {
			return false;
		}
		names.add(n);
		save();
		return true;
	}

	public static synchronized void remove(String name) {
		list();
		names.removeIf(n -> n.equalsIgnoreCase(name));
		save();
	}

	public static boolean here(String name) {
		return here.contains(name.toLowerCase(Locale.ROOT));
	}

	private static void save() {
		try {
			JsonArray a = new JsonArray();
			names.forEach(a::add);
			JsonObject o = new JsonObject();
			o.add("friends", a);
			Files.writeString(FILE, o.toString());
		} catch (Exception ignored) {
		}
	}

	/** Once a second: which friends are on this server; a note when one joins or leaves. */
	public static void tick(Minecraft mc) {
		if (mc.getConnection() == null || mc.player == null) {
			here = Set.of();
			server = null;
			return;
		}
		Set<String> now = new HashSet<>();
		for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
			String name = info.getProfile().name();
			if (isFriend(name) && !name.equalsIgnoreCase(mc.getUser().getName())) {
				now.add(name.toLowerCase(Locale.ROOT));
			}
		}
		String current = mc.getCurrentServer() == null ? "" : mc.getCurrentServer().ip;
		// No notes for the friends already here when joining a server.
		if (current.equals(server)) {
			for (String n : list()) {
				String lower = n.toLowerCase(Locale.ROOT);
				if (now.contains(lower) && !here.contains(lower)) {
					toast(n, L.t("friends.joined"));
				} else if (!now.contains(lower) && here.contains(lower)) {
					toast(n, L.t("friends.left"));
				}
			}
		}
		server = current;
		here = now;
	}

	private static void toast(String name, String text) {
		Minecraft mc = Minecraft.getInstance();
		SystemToast.addOrUpdate(mc.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, Component.literal(name), Component.literal(text));
	}
}
