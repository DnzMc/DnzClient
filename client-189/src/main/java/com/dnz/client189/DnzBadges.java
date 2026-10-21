package com.dnz.client189;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.xml.bind.DatatypeConverter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;

/**
 * DNZ badge on 1.8.9, the same as on 26.x: a DNZ mark next to the names of players who also play with DNZ
 * Client (above their head and in the player list).
 */
public final class DnzBadges {
	private static final String SERVER = "https://cloud.dnzclient.com";
	private static final char GLYPH = (char) 0x24B9;
	/** The mark before a name: the DNZ logo (white, so the logo keeps its own colors) and a space. */
	public static final String MARK = (char) 0xA7 + "f" + GLYPH + (char) 0xA7 + "r ";
	private static final int EVERY = 120;
	private static final AtomicBoolean busy = new AtomicBoolean();

	private static volatile Set<UUID> dnz = Collections.emptySet();
	private static String server;
	private static int wait;
	private static int failures;
	private static int ticks;
	private static String token;
	private static long tokenUntil;

	private DnzBadges() {
	}

	private static boolean on() {
		Module m = Modules.get("dnzbadge");
		return m != null && m.enabled();
	}

	/** True when the badge belongs next to this player (the player themself too, while on a server). */
	public static boolean has(UUID player) {
		if (!on() || player == null) {
			return false;
		}
		Minecraft mc = Minecraft.getMinecraft();
		return dnz.contains(player) || (mc.thePlayer != null && !mc.isSingleplayer() && player.equals(mc.thePlayer.getUniqueID()));
	}

	/** Every client tick; works once a second. */
	public static void tick(Minecraft mc) {
		if (++ticks % 20 != 0) {
			return;
		}
		if (!on() || mc.thePlayer == null || mc.getNetHandler() == null || mc.isSingleplayer()) {
			set(Collections.<UUID>emptySet(), mc);
			server = null;
			return;
		}
		String current = mc.getCurrentServerData() == null ? "" : mc.getCurrentServerData().serverIP;
		if (!current.equals(server)) {
			server = current;
			set(Collections.<UUID>emptySet(), mc);
			wait = 3;
		}
		if (--wait > 0 || !busy.compareAndSet(false, true)) {
			return;
		}
		wait = EVERY * Math.min(1 + failures, 8);
		final JsonArray players = new JsonArray();
		for (NetworkPlayerInfo info : mc.getNetHandler().getPlayerInfoMap()) {
			players.add(new com.google.gson.JsonPrimitive(info.getGameProfile().getId().toString().replace("-", "")));
			if (players.size() >= 300) {
				break;
			}
		}
		final String accessToken = mc.getSession().getToken();
		final String uuid = mc.getSession().getPlayerID().replace("-", "");
		final String name = mc.getSession().getUsername();
		Thread t = new Thread(() -> {
			try {
				report(players, accessToken, uuid, name);
				failures = 0;
			} catch (Exception e) {
				failures++;
			} finally {
				busy.set(false);
			}
		}, "DNZ Badge");
		t.setDaemon(true);
		t.start();
	}

	private static void set(Set<UUID> found, Minecraft mc) {
		if (found.equals(dnz)) {
			return;
		}
		dnz = found;
		// Names above heads are worked out once per player: ask again
		if (mc.theWorld != null) {
			for (EntityPlayer p : mc.theWorld.playerEntities) {
				p.refreshDisplayName();
			}
		}
	}

	private static void report(JsonArray players, String accessToken, String uuid, String name) throws Exception {
		JsonObject body = new JsonObject();
		body.add("players", players);
		body.addProperty("name", name);
		String answer;
		try {
			answer = post(SERVER + "/online", body.toString(), "Bearer " + token(accessToken, uuid));
		} catch (IllegalStateException e) {
			token = null; // expired: sign in again once
			answer = post(SERVER + "/online", body.toString(), "Bearer " + token(accessToken, uuid));
		}
		final Set<UUID> found = new HashSet<UUID>();
		for (JsonElement e : new JsonParser().parse(answer).getAsJsonObject().getAsJsonArray("dnz")) {
			String id = e.getAsString();
			found.add(new UUID(Long.parseUnsignedLong(id.substring(0, 16), 16), Long.parseUnsignedLong(id.substring(16), 16)));
		}
		Minecraft.getMinecraft().addScheduledTask(() -> set(found, Minecraft.getMinecraft()));
	}

	/** DNZ Cloud session token for this player (kept 50 minutes). */
	private static synchronized String token(String accessToken, String uuid) throws Exception {
		if (token != null && System.currentTimeMillis() < tokenUntil) {
			return token;
		}
		JsonObject cert = new JsonParser().parse(post("https://api.minecraftservices.com/player/certificates", "", "Bearer " + accessToken)).getAsJsonObject();
		JsonObject keys = cert.getAsJsonObject("keyPair");
		byte[] privateDer = DatatypeConverter.parseBase64Binary(pem(keys.get("privateKey").getAsString()));
		byte[] publicDer = DatatypeConverter.parseBase64Binary(pem(keys.get("publicKey").getAsString()));
		PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privateDer));
		long expiresAt = DatatypeConverter.parseDateTime(cert.get("expiresAt").getAsString()).getTimeInMillis();

		String challenge = new JsonParser().parse(get(SERVER + "/challenge")).getAsJsonObject().get("challenge").getAsString();
		Signature signer = Signature.getInstance("SHA256withRSA");
		signer.initSign(key);
		signer.update(challenge.getBytes("UTF-8"));

		JsonObject ask = new JsonObject();
		ask.addProperty("uuid", uuid);
		ask.addProperty("expiresAt", expiresAt);
		ask.addProperty("publicKey", DatatypeConverter.printBase64Binary(publicDer));
		ask.addProperty("keySignature", cert.get("publicKeySignatureV2").getAsString());
		ask.addProperty("challenge", challenge);
		ask.addProperty("signature", DatatypeConverter.printBase64Binary(signer.sign()));
		token = new JsonParser().parse(post(SERVER + "/auth", ask.toString(), null)).getAsJsonObject().get("token").getAsString();
		tokenUntil = System.currentTimeMillis() + 50 * 60 * 1000L;
		return token;
	}

	/**
	 * Puts DNZ's badge logo on the badge character: 1.8.9 draws such characters from a "unicode page" picture,
	 * so the logo is painted into that picture (in memory, from the game's own files; nothing of Mojang's is
	 * shipped) and the character gets the full width.
	 */
	public static void installGlyph(net.minecraft.client.resources.IResourceManager manager, byte[] widths) {
		try {
			net.minecraft.util.ResourceLocation page = new net.minecraft.util.ResourceLocation("textures/font/unicode_page_24.png");
			java.awt.image.BufferedImage vanilla = net.minecraft.client.renderer.texture.TextureUtil.readBufferedImage(manager.getResource(page).getInputStream());
			java.awt.image.BufferedImage logo = net.minecraft.client.renderer.texture.TextureUtil.readBufferedImage(
				manager.getResource(new net.minecraft.util.ResourceLocation("dnzclient", "textures/badge.png")).getInputStream());
			int cell = vanilla.getWidth() / 16;
			int x = (GLYPH & 15) * cell;
			int y = (GLYPH >> 4 & 15) * cell;
			java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(vanilla.getWidth(), vanilla.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
			java.awt.Graphics2D g = out.createGraphics();
			g.drawImage(vanilla, 0, 0, null);
			g.setComposite(java.awt.AlphaComposite.Clear);
			g.fillRect(x, y, cell, cell);
			g.setComposite(java.awt.AlphaComposite.SrcOver);
			g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g.drawImage(logo, x, y, cell, cell, null);
			g.dispose();
			Minecraft.getMinecraft().getTextureManager().loadTexture(page, new net.minecraft.client.renderer.texture.DynamicTexture(out));
			widths[GLYPH] = 0x0F;
		} catch (Exception ignored) {
			// no badge picture (e.g. a resource pack without that page): the circled D stays
		}
	}

	private static String pem(String text) {
		return text.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
	}

	private static String get(String url) throws Exception {
		return request(url, null, null);
	}

	private static String post(String url, String body, String auth) throws Exception {
		return request(url, body, auth);
	}

	private static String request(String url, String body, String auth) throws Exception {
		HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
		c.setConnectTimeout(10000);
		c.setReadTimeout(15000);
		c.setRequestProperty("User-Agent", "DNZClient/1.8.9");
		if (auth != null) {
			c.setRequestProperty("Authorization", auth);
		}
		if (body != null) {
			c.setRequestMethod("POST");
			c.setDoOutput(true);
			c.setRequestProperty("Content-Type", "application/json");
			OutputStream out = c.getOutputStream();
			out.write(body.getBytes("UTF-8"));
			out.close();
		}
		int code = c.getResponseCode();
		if (code == 401) {
			throw new IllegalStateException("401");
		}
		if (code != 200) {
			throw new java.io.IOException("HTTP " + code + " " + url);
		}
		InputStream in = c.getInputStream();
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		byte[] b = new byte[4096];
		int n;
		while ((n = in.read(b)) > 0) {
			buf.write(b, 0, n);
		}
		in.close();
		return buf.toString("UTF-8");
	}
}
