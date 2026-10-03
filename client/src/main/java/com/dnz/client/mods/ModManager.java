package com.dnz.client.mods;

import com.dnz.client.L;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.fabricmc.loader.api.FabricLoader;

/** Lists, toggles and installs mods (Modrinth API). Changes apply after a restart. */
public final class ModManager {
	private static final String API = "https://api.modrinth.com/v2";
	private static final String USER_AGENT = "DNZClient (modrinth.com)";
	private static final Set<String> LOCKED_IDS = Set.of("dnzclient", "fabric-api", "fabricloader", "java", "minecraft");
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(15))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	/** Project ids installed during this session, so the UI can show "Yüklendi". */
	private static final Set<String> INSTALLED_THIS_SESSION = new HashSet<>();

	public record LocalMod(Path path, String name, String id, boolean enabled, boolean locked) {
	}

	public record RemoteMod(String projectId, String slug, String title, String author, long downloads) {
	}

	private ModManager() {
	}

	public static Path modsDir() {
		return FabricLoader.getInstance().getGameDir().resolve("mods");
	}

	public static String minecraftVersion() {
		return FabricLoader.getInstance().getModContainer("minecraft")
			.map(c -> c.getMetadata().getVersion().getFriendlyString())
			.orElse("26.3");
	}

	// ---------------------------------------------------------------- local

	public static List<LocalMod> listLocal() {
		List<LocalMod> mods = new ArrayList<>();
		try (Stream<Path> files = Files.list(modsDir())) {
			files.sorted().forEach(path -> {
				String file = path.getFileName().toString();
				boolean enabled = file.endsWith(".jar");
				if (!enabled && !file.endsWith(".jar.disabled")) {
					return;
				}
				String id = "";
				String name = file.replace(".disabled", "").replace(".jar", "");
				try (ZipFile zip = new ZipFile(path.toFile())) {
					ZipEntry entry = zip.getEntry("fabric.mod.json");
					if (entry != null) {
						try (InputStream in = zip.getInputStream(entry)) {
							JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
							id = json.has("id") ? json.get("id").getAsString() : "";
							if (json.has("name")) {
								name = json.get("name").getAsString();
							}
						}
					}
				} catch (Exception ignored) {
				}
				mods.add(new LocalMod(path, name, id, enabled, LOCKED_IDS.contains(id)));
			});
		} catch (IOException ignored) {
		}
		mods.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
		return mods;
	}

	/** Enables/disables a mod by renaming it. Returns null on success or an error message. */
	public static String toggle(LocalMod mod) {
		if (mod.locked()) {
			return L.t("mods.locked");
		}
		String file = mod.path().getFileName().toString();
		String target = mod.enabled() ? file + ".disabled" : file.substring(0, file.length() - ".disabled".length());
		try {
			Files.move(mod.path(), mod.path().resolveSibling(target), StandardCopyOption.REPLACE_EXISTING);
			return null;
		} catch (IOException e) {
			return L.t("mods.toggle_error", e.getMessage());
		}
	}

	// ---------------------------------------------------------------- modrinth

	public static boolean isInstalled(RemoteMod mod) {
		return INSTALLED_THIS_SESSION.contains(mod.projectId()) || FabricLoader.getInstance().isModLoaded(mod.slug());
	}

	public static CompletableFuture<List<RemoteMod>> search(String query) {
		String facets = "[[\"categories:fabric\"],[\"versions:" + minecraftVersion() + "\"],[\"project_type:mod\"]]";
		String url = API + "/search?limit=30&index=relevance&query=" + enc(query) + "&facets=" + enc(facets);
		return getJson(url).thenApply(json -> {
			List<RemoteMod> result = new ArrayList<>();
			for (JsonElement e : json.getAsJsonObject().getAsJsonArray("hits")) {
				JsonObject hit = e.getAsJsonObject();
				result.add(new RemoteMod(
					hit.get("project_id").getAsString(),
					hit.get("slug").getAsString(),
					hit.get("title").getAsString(),
					hit.has("author") ? hit.get("author").getAsString() : "",
					hit.has("downloads") ? hit.get("downloads").getAsLong() : 0));
			}
			return result;
		});
	}

	/** Installs the newest compatible version plus required dependencies. Completes with null or an error message. */
	public static CompletableFuture<String> install(RemoteMod mod) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				installProject(mod.projectId(), new HashSet<>(), true);
				INSTALLED_THIS_SESSION.add(mod.projectId());
				return null;
			} catch (Exception e) {
				return e.getMessage() == null ? e.toString() : e.getMessage();
			}
		});
	}

	private static void installProject(String projectId, Set<String> visited, boolean root) throws Exception {
		if (!visited.add(projectId)) {
			return;
		}
		String url = API + "/project/" + projectId + "/version?loaders=" + enc("[\"fabric\"]")
			+ "&game_versions=" + enc("[\"" + minecraftVersion() + "\"]");
		JsonArray versions = getJson(url).join().getAsJsonArray();
		if (versions.isEmpty()) {
			if (root) {
				throw new IllegalStateException(L.t("mods.unsupported", minecraftVersion()));
			}
			return;
		}
		JsonObject version = versions.get(0).getAsJsonObject();
		JsonArray files = version.getAsJsonArray("files");
		JsonObject file = files.get(0).getAsJsonObject();
		for (JsonElement f : files) {
			if (f.getAsJsonObject().get("primary").getAsBoolean()) {
				file = f.getAsJsonObject();
			}
		}

		String filename = file.get("filename").getAsString();
		if (filename.contains("/") || filename.contains("\\") || !filename.endsWith(".jar")) {
			throw new IllegalStateException(L.t("mods.bad_name"));
		}
		Path target = modsDir().resolve(filename);
		if (!Files.exists(target) && !Files.exists(target.resolveSibling(filename + ".disabled"))) {
			download(file.get("url").getAsString(), file.getAsJsonObject("hashes").get("sha1").getAsString(), target);
		}

		for (JsonElement d : version.getAsJsonArray("dependencies")) {
			JsonObject dep = d.getAsJsonObject();
			if (!"required".equals(dep.get("dependency_type").getAsString()) || dep.get("project_id").isJsonNull()) {
				continue;
			}
			String depId = dep.get("project_id").getAsString();
			JsonObject project = getJson(API + "/project/" + depId).join().getAsJsonObject();
			if (FabricLoader.getInstance().isModLoaded(project.get("slug").getAsString())) {
				continue;
			}
			installProject(depId, visited, false);
		}
	}

	private static void download(String url, String sha1, Path target) throws Exception {
		if (!url.startsWith("https://cdn.modrinth.com/")) {
			throw new IllegalStateException(L.t("mods.untrusted"));
		}
		Path temp = target.resolveSibling(target.getFileName() + ".part");
		HttpRequest request = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", USER_AGENT).timeout(Duration.ofMinutes(2)).build();
		HttpResponse<Path> response = HTTP.send(request, HttpResponse.BodyHandlers.ofFile(temp));
		if (response.statusCode() != 200) {
			Files.deleteIfExists(temp);
			throw new IllegalStateException(L.t("mods.download_failed", response.statusCode()));
		}
		String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(temp)));
		if (!actual.equalsIgnoreCase(sha1)) {
			Files.deleteIfExists(temp);
			throw new IllegalStateException(L.t("mods.corrupt"));
		}
		Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
	}

	private static CompletableFuture<JsonElement> getJson(String url) {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", USER_AGENT).timeout(Duration.ofSeconds(20)).build();
		return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
			if (response.statusCode() != 200) {
				throw new IllegalStateException(L.t("mods.api_error", response.statusCode()));
			}
			return JsonParser.parseString(response.body());
		});
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	public static String formatDownloads(long n) {
		if (n >= 1_000_000) {
			return String.format("%.1fM", n / 1_000_000.0);
		}
		if (n >= 1_000) {
			return String.format("%.1fK", n / 1_000.0);
		}
		return Long.toString(n);
	}
}
