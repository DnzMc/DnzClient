package com.dnz.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

/**
 * DNZ badge: a small DNZ logo next to the names of players who also play with DNZ Client (above their head and in
 * the player list). While on a server, every few minutes the ids of the players there go to DNZ Cloud (signed in like
 * Cloud Save, see CloudSync); it notes that this player uses DNZ Client and answers which of the others do too.
 * Nothing is sent in single player or with the option off (DNZ menu → Visual → DNZ Badge).
 */
public final class DnzBadges {
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("DNZ Badge");
	private static final FontDescription FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("dnzclient", "badge"));
	/** Seconds between two reports while on a server. */
	private static final int EVERY = 120;
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private static final AtomicBoolean busy = new AtomicBoolean();

	/** Players on this server known to use DNZ Client (read on the render thread, replaced as a whole). */
	private static volatile Set<UUID> dnz = Set.of();
	private static String server;
	private static int wait;
	/** After an error (offline, no Mojang certificate...) wait longer before trying again. */
	private static int failures;

	private DnzBadges() {
	}

	/** The badge (a logo glyph and a space) to put before a name. */
	public static MutableComponent badge() {
		return Component.empty().append(Component.literal("").withStyle(style -> style.withFont(FONT))).append(" ");
	}

	/** True when the badge belongs next to this player (the player themself counts while the option is on). */
	public static boolean has(UUID player) {
		if (!DnzConfig.get().dnzBadge || player == null) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		return dnz.contains(player) || (mc.getConnection() != null && player.equals(mc.getUser().getProfileId()));
	}

	/** Once a second (FriendsInit). */
	public static void tick(Minecraft mc) {
		if (!DnzConfig.get().dnzBadge || mc.getConnection() == null || mc.player == null || mc.isLocalServer()) {
			dnz = Set.of();
			server = null;
			return;
		}
		String current = mc.getCurrentServer() == null ? "" : mc.getCurrentServer().ip;
		if (!current.equals(server)) {
			server = current;
			dnz = Set.of();
			wait = 3; // a moment after joining, when the player list has arrived
		}
		if (--wait > 0 || !busy.compareAndSet(false, true)) {
			return;
		}
		wait = EVERY * Math.min(1 + failures, 8);
		JsonArray players = new JsonArray();
		for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
			players.add(info.getProfile().id().toString().replace("-", ""));
			if (players.size() >= 300) {
				break;
			}
		}
		Thread.ofVirtual().name("DNZ Badge").start(() -> {
			try {
				report(players);
				failures = 0;
			} catch (Exception e) {
				failures++;
				LOG.debug("DNZ badge report failed: {}", e.toString());
			} finally {
				busy.set(false);
			}
		});
	}

	private static void report(JsonArray players) throws Exception {
		JsonObject body = new JsonObject();
		body.add("players", players);
		HttpResponse<String> answer = send(body.toString());
		if (answer.statusCode() == 401) {
			CloudSync.forgetToken();
			answer = send(body.toString());
		}
		if (answer.statusCode() != 200) {
			throw new IllegalStateException("HTTP " + answer.statusCode());
		}
		Set<UUID> found = new HashSet<>();
		for (JsonElement e : JsonParser.parseString(answer.body()).getAsJsonObject().getAsJsonArray("dnz")) {
			String id = e.getAsString();
			found.add(new UUID(Long.parseUnsignedLong(id.substring(0, 16), 16), Long.parseUnsignedLong(id.substring(16), 16)));
		}
		dnz = Set.copyOf(found);
	}

	private static HttpResponse<String> send(String body) throws Exception {
		return HTTP.send(HttpRequest.newBuilder(URI.create(CloudSync.SERVER + "/online"))
			.header("Authorization", "Bearer " + CloudSync.token()).header("Content-Type", "application/json")
			.timeout(Duration.ofSeconds(15)).POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
	}
}
