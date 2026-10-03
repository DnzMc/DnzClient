package com.dnz.client.skin;

import com.dnz.client.L;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Uploads a skin PNG to the player's own Minecraft account via the official Mojang API. */
public final class SkinUploader {
	private static final URI SKIN_ENDPOINT = URI.create("https://api.minecraftservices.com/minecraft/profile/skins");
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

	private SkinUploader() {
	}

	/** Completes with null on success, or a user-facing (Turkish) error message. */
	public static CompletableFuture<String> upload(String accessToken, Path file, boolean slim) {
		byte[] png;
		try {
			png = Files.readAllBytes(file);
		} catch (IOException e) {
			return CompletableFuture.completedFuture(L.t("skin.read_error", e.getMessage()));
		}
		if (!isValidSkin(png)) {
			return CompletableFuture.completedFuture(L.t("skin.invalid"));
		}

		String boundary = "----DNZ" + UUID.randomUUID().toString().replace("-", "");
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		try {
			write(body, "--" + boundary + "\r\n");
			write(body, "Content-Disposition: form-data; name=\"variant\"\r\n\r\n");
			write(body, (slim ? "slim" : "classic") + "\r\n");
			write(body, "--" + boundary + "\r\n");
			write(body, "Content-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\n");
			write(body, "Content-Type: image/png\r\n\r\n");
			body.write(png);
			write(body, "\r\n--" + boundary + "--\r\n");
		} catch (IOException e) {
			return CompletableFuture.completedFuture(L.t("error", e.getMessage()));
		}

		HttpRequest request = HttpRequest.newBuilder(SKIN_ENDPOINT)
			.timeout(Duration.ofSeconds(30))
			.header("Authorization", "Bearer " + accessToken)
			.header("Content-Type", "multipart/form-data; boundary=" + boundary)
			.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
			.build();

		return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).handle((response, error) -> {
			if (error != null) {
				return L.t("skin.network", error.getMessage());
			}
			int code = response.statusCode();
			if (code >= 200 && code < 300) {
				return null;
			}
			if (code == 401) {
				return L.t("skin.auth");
			}
			if (code == 429) {
				return L.t("skin.rate_limit");
			}
			return L.t("skin.server", code);
		});
	}

	private static boolean isValidSkin(byte[] png) {
		// PNG signature + IHDR width/height (big-endian ints at offsets 16 and 20)
		if (png.length < 24 || (png[0] & 0xFF) != 0x89 || png[1] != 'P' || png[2] != 'N' || png[3] != 'G') {
			return false;
		}
		int w = readInt(png, 16);
		int h = readInt(png, 20);
		return w == 64 && (h == 64 || h == 32);
	}

	private static int readInt(byte[] b, int off) {
		return (b[off] & 0xFF) << 24 | (b[off + 1] & 0xFF) << 16 | (b[off + 2] & 0xFF) << 8 | (b[off + 3] & 0xFF);
	}

	private static void write(ByteArrayOutputStream out, String s) throws IOException {
		out.write(s.getBytes(StandardCharsets.UTF_8));
	}
}
