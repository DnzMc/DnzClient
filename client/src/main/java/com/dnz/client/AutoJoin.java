package com.dnz.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/**
 * "Join" from the DNZ Launcher's server list: the launcher leaves config/dnzclient-join.json in the profile,
 * and when the title screen first shows up the game connects to that server. The note is used once and deleted.
 */
public final class AutoJoin {
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("dnzclient-join.json");
	/** Older notes are ignored (e.g. the game was not started after all). */
	private static final long MAX_AGE_MS = 10 * 60 * 1000L;
	private static boolean done;

	private AutoJoin() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
			if (done || !(screen instanceof TitleScreen)) {
				return;
			}
			done = true;
			tryJoin(mc, screen);
		});
	}

	/** Joins the server of the launcher's note, if there is a fresh one. Returns true when it started connecting. */
	public static boolean tryJoin(Minecraft mc, net.minecraft.client.gui.screens.Screen screen) {
		{
			JsonObject note = read();
			if (note == null) {
				return false;
			}
			String address = note.has("address") ? note.get("address").getAsString() : "";
			String name = note.has("name") ? note.get("name").getAsString() : address;
			long time = note.has("time") ? note.get("time").getAsLong() : 0;
			if (address.isBlank() || System.currentTimeMillis() - time > MAX_AGE_MS) {
				return false;
			}
			// After the title screen has finished opening.
			mc.execute(() -> ConnectScreen.startConnecting(screen, mc, ServerAddress.parseString(address),
				new ServerData(name, address, ServerData.Type.OTHER), false, null));
			return true;
		}
	}

	/** Where the launcher leaves its note (used by the in-game test). */
	public static Path noteFile() {
		return FILE;
	}

	private static JsonObject read() {
		try {
			if (!Files.exists(FILE)) {
				return null;
			}
			JsonObject note = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
			Files.delete(FILE);
			return note;
		} catch (Exception e) {
			try {
				Files.deleteIfExists(FILE);
			} catch (Exception ignored) {
			}
			return null;
		}
	}
}
