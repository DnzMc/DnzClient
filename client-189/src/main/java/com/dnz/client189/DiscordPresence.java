package com.dnz.client189;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;
import net.minecraft.client.Minecraft;

/**
 * Discord status on 1.8.9, the same as DNZ Client 26.x: "Playing DNZ Client", where (menu / singleplayer /
 * server) and for how long, through the Discord app's local pipe (Windows; Java 8 has no Unix sockets for
 * macOS/Linux).
 */
public final class DiscordPresence {
	private static final String APP_ID = "1558531559940816938";
	private static final long STARTED = System.currentTimeMillis();
	private static volatile String wanted;
	private static String sent;
	private static RandomAccessFile pipe;
	private static long nextConnect;
	private static int ticks;

	private DiscordPresence() {
	}

	public static void start() {
		if (!System.getProperty("os.name", "").toLowerCase().startsWith("windows")) {
			return;
		}
		Thread t = new Thread(() -> {
			while (true) {
				sync();
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					return;
				}
			}
		}, "DNZ Discord");
		t.setDaemon(true);
		t.start();
	}

	/** Game thread: what to show (built every 2 seconds). */
	public static void tick(Minecraft mc) {
		if (++ticks % 40 != 0) {
			return;
		}
		Module m = Modules.get("discord");
		if (m == null || !m.enabled()) {
			wanted = "";
			return;
		}
		String details;
		String state = null;
		if (mc.theWorld == null) {
			details = Lang.t("Ana menüde", "In the menu");
		} else if (mc.isSingleplayer()) {
			details = Lang.t("Tek oyunculu", "Singleplayer");
		} else {
			details = Lang.t("Çok oyunculu", "Multiplayer");
			Module streamer = Modules.get("streamer");
			if (mc.getCurrentServerData() != null && (streamer == null || !streamer.enabled())) {
				state = mc.getCurrentServerData().serverIP;
			}
		}
		JsonObject activity = new JsonObject();
		activity.addProperty("details", details + " · 1.8.9");
		if (state != null) {
			activity.addProperty("state", state);
		}
		JsonObject times = new JsonObject();
		times.addProperty("start", STARTED);
		activity.add("timestamps", times);
		JsonObject assets = new JsonObject();
		assets.addProperty("large_image", "logo");
		assets.addProperty("large_text", "DNZ Client " + DnzClient.VERSION + " (1.8.9)");
		activity.add("assets", assets);
		JsonArray buttons = new JsonArray();
		JsonObject site = new JsonObject();
		site.addProperty("label", "DNZ Client");
		site.addProperty("url", "https://dnzclient.com");
		buttons.add(site);
		activity.add("buttons", buttons);
		wanted = activity.toString();
	}

	private static void sync() {
		String activity = wanted;
		if (activity == null) {
			return;
		}
		try {
			if (pipe == null) {
				if (activity.isEmpty() || System.currentTimeMillis() < nextConnect) {
					return;
				}
				connect();
				if (pipe == null) {
					return;
				}
			}
			if (activity.equals(sent)) {
				return;
			}
			String args = "{\"pid\":" + pid() + (activity.isEmpty() ? "" : ",\"activity\":" + activity) + "}";
			send(1, "{\"cmd\":\"SET_ACTIVITY\",\"args\":" + args + ",\"nonce\":\"" + UUID.randomUUID() + "\"}");
			receive();
			sent = activity;
		} catch (Exception e) {
			close();
			nextConnect = System.currentTimeMillis() + 30000;
		}
	}

	private static long pid() {
		String name = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
		try {
			return Long.parseLong(name.substring(0, name.indexOf('@')));
		} catch (Exception e) {
			return 0;
		}
	}

	private static void connect() {
		for (int i = 0; i < 10; i++) {
			try {
				pipe = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw");
				send(0, "{\"v\":1,\"client_id\":\"" + APP_ID + "\"}");
				if (receive().contains("READY")) {
					sent = null;
					return;
				}
			} catch (Exception ignored) {
			}
			close();
		}
		nextConnect = System.currentTimeMillis() + 30000;
	}

	private static void close() {
		if (pipe != null) {
			try {
				pipe.close();
			} catch (IOException ignored) {
			}
			pipe = null;
		}
		sent = null;
	}

	private static void send(int op, String json) throws IOException {
		byte[] body = json.getBytes("UTF-8");
		ByteBuffer frame = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
		frame.putInt(op).putInt(body.length).put(body);
		pipe.write(frame.array());
	}

	private static String receive() throws IOException {
		byte[] header = new byte[8];
		pipe.readFully(header);
		ByteBuffer h = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
		int op = h.getInt();
		int length = h.getInt();
		if (length < 0 || length > 1 << 20) {
			throw new IOException("bad frame");
		}
		byte[] body = new byte[length];
		pipe.readFully(body);
		String text = new String(body, "UTF-8");
		if (op == 2) {
			throw new IOException(text);
		}
		return text;
	}
}
