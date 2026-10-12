package com.dnz.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/**
 * Discord Rich Presence: the player's Discord profile shows "Playing DNZ Client", with where they are (menu,
 * singleplayer, server) and for how long. Talks to the Discord app on this computer through its local IPC pipe
 * (Windows: \\.\pipe\discord-ipc-N, macOS/Linux: a Unix socket); nothing goes over the internet from here.
 * Off in config (discordPresence = false); streamer mode hides the server address.
 */
public final class DiscordPresence {
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("DNZ Discord");
	/** Application ID of the "DNZ Client" app in the Discord Developer Portal; its name is what Discord shows. */
	private static final String APP_ID = "1558531559940816938";
	private static final String WEBSITE = "https://dnzclient.com";
	private static final long STARTED = System.currentTimeMillis();

	private static final ScheduledExecutorService WORKER = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "DNZ Discord");
		t.setDaemon(true);
		return t;
	});

	private static Pipe pipe;
	private static long nextConnect;
	private static int ticks;
	/** Last activity built on the game thread; the worker sends it when it changed. */
	private static volatile JsonObject wanted;
	private static JsonObject sent;

	private DiscordPresence() {
	}

	public static void start() {
		WORKER.scheduleWithFixedDelay(DiscordPresence::sync, 2, 5, TimeUnit.SECONDS);
	}

	public static void stop() {
		WORKER.execute(DiscordPresence::close);
		WORKER.shutdown();
	}

	/** Game thread, every tick: reads where the player is (cheap; the worker does the talking). */
	public static void tick(Minecraft mc) {
		if (++ticks % 40 != 0) {
			return;
		}
		DnzConfig config = DnzConfig.get();
		wanted = config.discordPresence ? activity(mc, config.streamerMode) : null;
	}

	private static JsonObject activity(Minecraft mc, boolean hideServer) {
		boolean tr = mc.getLanguageManager().getSelected().startsWith("tr");
		String details;
		String state = null;
		if (mc.level == null) {
			details = tr ? "Ana menüde" : "In the menu";
		} else if (mc.hasSingleplayerServer()) {
			details = tr ? "Tek oyunculu" : "Singleplayer";
		} else {
			details = tr ? "Çok oyunculu" : "Multiplayer";
			ServerData server = mc.getCurrentServer();
			if (server != null && !hideServer) {
				state = server.ip;
			}
		}
		String version = FabricLoader.getInstance().getModContainer("dnzclient")
			.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("");

		JsonObject activity = new JsonObject();
		activity.addProperty("details", details);
		if (state != null) {
			activity.addProperty("state", state);
		}
		JsonObject timestamps = new JsonObject();
		timestamps.addProperty("start", STARTED);
		activity.add("timestamps", timestamps);
		JsonObject assets = new JsonObject();
		assets.addProperty("large_image", "logo");
		assets.addProperty("large_text", ("DNZ Client " + version).trim());
		activity.add("assets", assets);
		JsonArray buttons = new JsonArray();
		JsonObject site = new JsonObject();
		site.addProperty("label", "DNZ Client");
		site.addProperty("url", WEBSITE);
		buttons.add(site);
		activity.add("buttons", buttons);
		return activity;
	}

	/** Worker thread: connects when Discord is open and sends the activity when it changed. */
	private static void sync() {
		JsonObject activity = wanted;
		try {
			if (pipe == null) {
				if (activity == null || System.currentTimeMillis() < nextConnect) {
					return;
				}
				connect();
				if (pipe == null) {
					return;
				}
			}
			if (Objects.equals(activity, sent)) {
				return;
			}
			JsonObject args = new JsonObject();
			args.addProperty("pid", ProcessHandle.current().pid());
			if (activity != null) {
				args.add("activity", activity);
			}
			JsonObject command = new JsonObject();
			command.addProperty("cmd", "SET_ACTIVITY");
			command.add("args", args);
			command.addProperty("nonce", UUID.randomUUID().toString());
			pipe.send(1, command.toString());
			pipe.receive();
			sent = activity;
		} catch (Exception e) {
			// Discord closed or restarted: try again a little later.
			close();
			nextConnect = System.currentTimeMillis() + 30_000;
		}
	}

	private static void connect() throws IOException {
		for (int i = 0; i < 10; i++) {
			Pipe p = Pipe.open(i);
			if (p == null) {
				continue;
			}
			try {
				JsonObject hello = new JsonObject();
				hello.addProperty("v", 1);
				hello.addProperty("client_id", APP_ID);
				p.send(0, hello.toString());
				String ready = p.receive();
				if (ready.contains("\"READY\"")) {
					pipe = p;
					sent = null;
					LOG.info("Connected to Discord");
					return;
				}
				LOG.warn("Discord refused the connection: {}", ready);
			} catch (IOException e) {
				// not a working Discord pipe, try the next one
			}
			p.close();
		}
		nextConnect = System.currentTimeMillis() + 30_000;
	}

	private static void close() {
		if (pipe != null) {
			pipe.close();
			pipe = null;
		}
		sent = null;
	}

	/** One Discord IPC connection. Frames: opcode and length (little-endian ints), then JSON. */
	private static final class Pipe {
		private final InputStream in;
		private final OutputStream out;
		private final AutoCloseable handle;

		private Pipe(InputStream in, OutputStream out, AutoCloseable handle) {
			this.in = in;
			this.out = out;
			this.handle = handle;
		}

		static Pipe open(int index) {
			String name = "discord-ipc-" + index;
			try {
				if (System.getProperty("os.name", "").toLowerCase().startsWith("windows")) {
					RandomAccessFile file = new RandomAccessFile("\\\\.\\pipe\\" + name, "rw");
					InputStream in = new InputStream() {
						@Override
						public int read() throws IOException {
							return file.read();
						}

						@Override
						public int read(byte[] b, int off, int len) throws IOException {
							return file.read(b, off, len);
						}
					};
					OutputStream out = new OutputStream() {
						@Override
						public void write(int b) throws IOException {
							file.write(b);
						}

						@Override
						public void write(byte[] b, int off, int len) throws IOException {
							file.write(b, off, len);
						}
					};
					return new Pipe(in, out, file);
				}
				for (Path dir : socketDirs()) {
					Path socket = dir.resolve(name);
					if (Files.exists(socket)) {
						SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
						channel.connect(UnixDomainSocketAddress.of(socket));
						return new Pipe(Channels.newInputStream(channel), Channels.newOutputStream(channel), channel);
					}
				}
			} catch (Exception e) {
				// Discord isn't running (or is another user's)
			}
			return null;
		}

		private static List<Path> socketDirs() {
			List<Path> dirs = new ArrayList<>();
			for (String env : new String[] {"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
				String value = System.getenv(env);
				if (value != null && !value.isBlank()) {
					Path base = Path.of(value);
					dirs.add(base);
					dirs.add(base.resolve("app/com.discordapp.Discord")); // Flatpak
					dirs.add(base.resolve("snap.discord")); // Snap
				}
			}
			dirs.add(Path.of("/tmp"));
			return dirs;
		}

		void send(int opcode, String json) throws IOException {
			byte[] body = json.getBytes(StandardCharsets.UTF_8);
			ByteBuffer frame = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
			frame.putInt(opcode).putInt(body.length).put(body);
			out.write(frame.array());
			out.flush();
		}

		String receive() throws IOException {
			byte[] header = in.readNBytes(8);
			if (header.length < 8) {
				throw new EOFException();
			}
			ByteBuffer h = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
			int opcode = h.getInt();
			int length = h.getInt();
			if (length < 0 || length > 1 << 20) {
				throw new IOException("bad frame");
			}
			String body = new String(in.readNBytes(length), StandardCharsets.UTF_8);
			if (opcode == 2) { // CLOSE
				throw new IOException("Discord closed the connection: " + body);
			}
			return body;
		}

		void close() {
			try {
				handle.close();
			} catch (Exception ignored) {
			}
		}
	}
}
