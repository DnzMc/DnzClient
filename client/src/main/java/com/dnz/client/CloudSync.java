package com.dnz.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Signature;
import java.util.Base64;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.ProfileKeyPair;
import net.minecraft.world.entity.player.ProfilePublicKey;

/**
 * DNZ Cloud (opt-in): key bindings, a few control options and the DNZ Client settings follow the player's Minecraft
 * account to other computers. Stored by cloud.dnzclient.com (see cloud/worker.js); turned on in DNZ Launcher
 * (Settings) or in the DNZ menu (Preferences), saved in ~/.dnzlauncher/cloud.json.
 *
 * Sign-in: the game signs a challenge from the DNZ server with its Mojang-signed player certificate (the key used
 * for chat); the access token stays on this computer. Nothing is sent while the option is off.
 */
public final class CloudSync {
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("DNZ Cloud");
	static final String SERVER = "https://cloud.dnzclient.com";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path SWITCH = Path.of(System.getProperty("user.home"), ".dnzlauncher", "cloud.json");
	/** Exists while the game sends settings at exit; DNZ Launcher shows a "don't close" note meanwhile. */
	private static final Path SAVING = Path.of(System.getProperty("user.home"), ".dnzlauncher", "cloud-saving");
	private static final Path STATE = FabricLoader.getInstance().getConfigDir().resolve("dnzclient-cloud.json");
	/** Options of options.txt that travel besides the key bindings (key_*). */
	private static final Set<String> OPTIONS = Set.of("mouseSensitivity", "toggleCrouch", "toggleSprint", "autoJump", "invertYMouse", "rawMouseInput");
	/** DNZ settings that belong to one computer and stay local. */
	private static final Set<String> LOCAL_FIELDS = Set.of("fpsDefaults", "macRetina");

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private static final ScheduledExecutorService WORKER = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "DNZ Cloud");
		t.setDaemon(true);
		return t;
	});

	private static String token;
	private static long tokenUntil;
	/** True after this session's first download: uploads before it could overwrite newer settings from another PC. */
	private static volatile boolean ready;
	private static volatile boolean dirty;
	private static volatile boolean applying;

	private CloudSync() {
	}

	/** The switch, read once (and again when it is changed in the game). */
	private static volatile Boolean on;

	public static boolean enabled() {
		if (on == null) {
			try {
				on = Files.exists(SWITCH) && JsonParser.parseString(Files.readString(SWITCH)).getAsJsonObject().get("enabled").getAsBoolean();
			} catch (Exception e) {
				on = false;
			}
		}
		return on;
	}

	public static void setEnabled(boolean on) {
		CloudSync.on = on;
		try {
			Files.createDirectories(SWITCH.getParent());
			JsonObject o = new JsonObject();
			o.addProperty("enabled", on);
			Files.writeString(SWITCH, GSON.toJson(o));
		} catch (Exception ignored) {
		}
		if (on) {
			start();
		}
	}

	/** Game started (title screen): download the cloud copy, or upload the first one. */
	public static void start() {
		if (!enabled()) {
			return;
		}
		WORKER.execute(() -> {
			try {
				HttpResponse<String> answer = request("GET", null);
				if (answer.statusCode() == 404) {
					upload();
				} else if (answer.statusCode() == 200) {
					JsonObject cloud = JsonParser.parseString(answer.body()).getAsJsonObject();
					long updated = cloud.get("updatedAt").getAsLong();
					if (updated > lastSync()) {
						Minecraft.getInstance().execute(() -> {
							apply(cloud);
							setLastSync(updated);
							toast(L.t("cloud.loaded"));
						});
					} else if (dirty) {
						upload();
					}
				} else {
					LOG.warn("Cloud download: HTTP {} {}", answer.statusCode(), answer.body());
				}
				ready = true;
			} catch (Exception e) {
				// Offline or not signed in with a Minecraft account: try again next start.
				LOG.warn("Cloud sync failed at start: {}", e.toString());
			}
		});
	}

	/** Settings changed (DNZ menu or Options saved): only remembered; they go to the cloud when the game closes. */
	public static void changed() {
		if (!applying && enabled()) {
			dirty = true;
		}
	}

	/**
	 * Game closing: the only time settings are sent, so playing never waits for the cloud. While it runs, DNZ Launcher
	 * shows "Don't close, saving settings to the cloud" (it watches the marker file).
	 */
	public static void stop() {
		if (!ready || !dirty || !enabled()) {
			return;
		}
		try {
			Files.writeString(SAVING, String.valueOf(System.currentTimeMillis()));
			WORKER.submit(() -> {
				try {
					upload();
				} catch (Exception e) {
					LOG.warn("Cloud upload failed: {}", e.toString());
				}
			}).get(15, TimeUnit.SECONDS);
		} catch (Exception ignored) {
		} finally {
			try {
				Files.deleteIfExists(SAVING);
			} catch (Exception ignored) {
			}
		}
	}

	private static void upload() throws Exception {
		long now = System.currentTimeMillis();
		JsonObject data = new JsonObject();
		data.addProperty("v", 1);
		data.addProperty("updatedAt", now);
		data.add("options", GSON.toJsonTree(readOptions()));
		JsonObject dnz = GSON.toJsonTree(DnzConfig.get()).getAsJsonObject();
		LOCAL_FIELDS.forEach(dnz::remove);
		data.add("dnzclient", dnz);
		HttpResponse<String> answer = request("PUT", GSON.toJson(data));
		if (answer.statusCode() == 200) {
			dirty = false;
			setLastSync(now);
			LOG.info("Settings saved to the cloud");
		} else {
			LOG.warn("Cloud upload: HTTP {} {}", answer.statusCode(), answer.body());
		}
	}

	/** Runs on the game thread. */
	private static void apply(JsonObject cloud) {
		applying = true;
		try {
			if (cloud.has("dnzclient")) {
				JsonObject dnz = cloud.getAsJsonObject("dnzclient");
				JsonObject local = GSON.toJsonTree(DnzConfig.get()).getAsJsonObject();
				LOCAL_FIELDS.forEach(f -> {
					if (local.has(f)) {
						dnz.add(f, local.get(f));
					}
				});
				DnzConfig.replace(GSON.fromJson(dnz, DnzConfig.class));
			}
			if (cloud.has("options")) {
				Map<String, String> values = new LinkedHashMap<>();
				for (Map.Entry<String, JsonElement> e : cloud.getAsJsonObject("options").entrySet()) {
					if (synced(e.getKey())) {
						values.put(e.getKey(), e.getValue().getAsString());
					}
				}
				writeOptions(values);
				Minecraft mc = Minecraft.getInstance();
				mc.options.load();
				KeyMapping.resetMapping();
				mc.options.save();
			}
		} catch (Exception ignored) {
		} finally {
			applying = false;
		}
	}

	private static boolean synced(String option) {
		return option.startsWith("key_") || OPTIONS.contains(option);
	}

	private static File optionsFile() {
		return new File(Minecraft.getInstance().gameDirectory, "options.txt");
	}

	private static Map<String, String> readOptions() throws Exception {
		Map<String, String> values = new LinkedHashMap<>();
		File file = optionsFile();
		if (file.isFile()) {
			for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
				int colon = line.indexOf(':');
				if (colon > 0 && synced(line.substring(0, colon))) {
					values.put(line.substring(0, colon), line.substring(colon + 1));
				}
			}
		}
		return values;
	}

	/** Replaces the synced lines of options.txt (other options stay as they are on this computer). */
	private static void writeOptions(Map<String, String> values) throws Exception {
		File file = optionsFile();
		List<String> out = new ArrayList<>();
		Map<String, String> left = new LinkedHashMap<>(values);
		if (file.isFile()) {
			for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
				int colon = line.indexOf(':');
				String key = colon > 0 ? line.substring(0, colon) : "";
				if (left.containsKey(key)) {
					out.add(key + ":" + left.remove(key));
				} else {
					out.add(line);
				}
			}
		}
		left.forEach((k, v) -> out.add(k + ":" + v));
		Files.write(file.toPath(), out, StandardCharsets.UTF_8);
	}

	private static HttpResponse<String> request(String method, String body) throws Exception {
		HttpResponse<String> answer = send(method, body);
		if (answer.statusCode() == 401) {
			token = null; // expired: sign in again once
			answer = send(method, body);
		}
		return answer;
	}

	private static HttpResponse<String> send(String method, String body) throws Exception {
		HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(SERVER + "/settings"))
			.header("Authorization", "Bearer " + token())
			.timeout(Duration.ofSeconds(15));
		b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
		return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
	}

	/** The session token was refused (expired): sign in again on the next request. */
	static synchronized void forgetToken() {
		token = null;
	}

	/** Session token of the DNZ server for this player (also used by Friends). */
	static synchronized String token() throws Exception {
		if (token != null && System.currentTimeMillis() < tokenUntil) {
			return token;
		}
		Minecraft mc = Minecraft.getInstance();
		User user = mc.getUser();
		// The player certificate the game uses for chat: Mojang signed its public key for this player.
		ProfileKeyPair keys = mc.getProfileKeyPairManager().prepareKeyPair().get(20, TimeUnit.SECONDS)
			.orElseThrow(() -> new IllegalStateException("no player certificate (offline account?)"));
		ProfilePublicKey.Data data = keys.publicKey().data();
		HttpResponse<String> challengeAnswer = HTTP.send(HttpRequest.newBuilder(URI.create(SERVER + "/challenge")).timeout(Duration.ofSeconds(15)).build(),
			HttpResponse.BodyHandlers.ofString());
		String challenge = JsonParser.parseString(challengeAnswer.body()).getAsJsonObject().get("challenge").getAsString();
		Signature signer = Signature.getInstance("SHA256withRSA");
		signer.initSign(keys.privateKey());
		signer.update(challenge.getBytes(StandardCharsets.UTF_8));
		Base64.Encoder b64 = Base64.getEncoder();
		JsonObject ask = new JsonObject();
		ask.addProperty("uuid", user.getProfileId().toString().replace("-", ""));
		ask.addProperty("expiresAt", data.expiresAt().toEpochMilli());
		ask.addProperty("publicKey", b64.encodeToString(data.key().getEncoded()));
		ask.addProperty("keySignature", b64.encodeToString(data.keySignature()));
		ask.addProperty("challenge", challenge);
		ask.addProperty("signature", b64.encodeToString(signer.sign()));
		HttpResponse<String> answer = HTTP.send(HttpRequest.newBuilder(URI.create(SERVER + "/auth"))
			.header("Content-Type", "application/json").timeout(Duration.ofSeconds(15))
			.POST(HttpRequest.BodyPublishers.ofString(ask.toString())).build(), HttpResponse.BodyHandlers.ofString());
		if (answer.statusCode() != 200) {
			throw new IllegalStateException("DNZ Cloud sign-in failed: HTTP " + answer.statusCode() + " " + answer.body());
		}
		token = JsonParser.parseString(answer.body()).getAsJsonObject().get("token").getAsString();
		tokenUntil = System.currentTimeMillis() + 50 * 60 * 1000L;
		return token;
	}

	private static long lastSync() {
		try {
			return JsonParser.parseString(Files.readString(STATE)).getAsJsonObject().get("lastSync").getAsLong();
		} catch (Exception e) {
			return 0;
		}
	}

	private static void setLastSync(long time) {
		try {
			JsonObject o = new JsonObject();
			o.addProperty("lastSync", time);
			Files.writeString(STATE, GSON.toJson(o));
		} catch (Exception ignored) {
		}
	}

	private static void toast(String text) {
		SystemToast.addOrUpdate(Minecraft.getInstance().gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
			Component.literal("DNZ Cloud"), Component.literal(text));
	}
}
