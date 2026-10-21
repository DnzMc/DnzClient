package com.dnz.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Saved DNZ Client preferences (config/dnzclient.json). */
public final class DnzConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("dnzclient.json");
	private static DnzConfig instance;

	public int accent = 0;
	public int theme = 0;
	/** Chest / inventory screens: false = vanilla white, true = black. */
	public boolean containerDark = false;
	public boolean fullbright = false;
	public boolean streamerMode = false;
	/** Java style: vanilla Minecraft buttons. */
	public boolean javaStyle = false;
	/** Simple style: clean glass menus with a smooth font (ignored while javaStyle is on). */
	public boolean simpleStyle = false;
	/** DNZ look for the multiplayer server list (cards with ping in ms and player count). */
	public boolean serverCards = true;
	/** Discord profile shows "Playing DNZ Client" (see DiscordPresence). */
	public boolean discordPresence = true;
	/** DNZ logo next to the names of players who also play with DNZ Client (see DnzBadges). */
	public boolean dnzBadge = true;
	/** Shield module: how far the shield is lowered in first person (0-100 %) and its size (50-100 %), see ShieldView. */
	public int shieldLower = 30;
	public int shieldSize = 80;
	/** Which one-time FPS defaults were applied (1 = VSync off + unlimited FPS). */
	public int fpsDefaults = 0;
	/** DNZ Turbo: far decorations/drops/signs not drawn, particle cap (see Turbo). */
	public boolean turbo = true;
	/** Freelook switch (only works in single player; on servers it shows Off and can't be turned on, see Freelook). */
	public boolean freelook = true;
	/** Cool mode: FPS capped at twice the screen refresh rate (less heat, steadier frames on laptops). */
	public boolean coolMode = false;
	/** DNZ menus drawn by NanoVG on the graphics card (OpenGL only; off = DNZ's own drawing). */
	public boolean nanoVg = true;
	/** macOS: draw at full Retina resolution (sharper, much lower FPS). */
	public boolean macRetina = false;
	/** Size of the DNZ menu in percent (100 = about two thirds of the window, at any resolution and GUI scale). */
	public int menuSize = 100;
	/** DNZ menu look: 0 = Modern (smooth font), 1 = Minecraft (pixel font, black and white). */
	public int menuLook = 0;
	/** DNZ menu colors: false = dark, true = white. */
	public boolean menuLight = false;
	/** Test only (set by hand in the file): the new DNZ title screen in place of Minecraft's. */
	public boolean testTitle = false;
	/** HUD elements by id; positions are fractions (0..1) of the screen size. */
	public java.util.Map<String, HudPos> hud = new java.util.LinkedHashMap<>();
	public java.util.Set<String> disabledScripts = new java.util.LinkedHashSet<>();

	/** Custom crosshair settings (module "crosshair"); sizes in screen pixels. */
	public Crosshair crosshair = new Crosshair();

	public static class Crosshair {
		public int style = 0;
		public float size = 6;
		public float gap = 3;
		public float thickness = 2;
		public int color = 0xFFFFFFFF;
		public boolean outline = true;
		public boolean dot = false;
	}

	public BlockOutline blockOutline = new BlockOutline();

	public static class BlockOutline {
		public int color = 0xFFFFFFFF;
		/** Outline opacity in percent. */
		public int opacity = 100;
		/** Line width; 2 = vanilla. */
		public float thickness = 2;
		/** Rainbow color that keeps changing. */
		public boolean chroma = false;
		public boolean fill = false;
		/** Fill opacity in percent. */
		public int fillOpacity = 20;
	}

	/** Shows the sender's skin head before chat messages (module "chatheads"). */
	public boolean chatHeads = true;

	/** A module's saved state (the name stays HudPos so older config files keep working). */
	public static class HudPos {
		public boolean enabled = true;
		public float x;
		public float y;
		/** Size on screen, 0.5 .. 2.5. */
		public float scale = 1.0F;
		/** Dark panel behind the element (off = text only, with shadow). */
		public boolean bg = true;
		/** Module specific on/off options (e.g. coords: "nether"). */
		public java.util.Map<String, Boolean> opts = new java.util.HashMap<>();
		/** Text before the value ("FPS" in "FPS 240"); empty = the module's own name. */
		public String label = "";
		public String value = "";

		public HudPos() {
		}

		public HudPos(float x, float y) {
			this.x = x;
			this.y = y;
		}
	}

	public static DnzConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	/** DNZ Cloud: settings from another computer take the place of these. */
	static void replace(DnzConfig config) {
		if (config.hud == null) {
			config.hud = new java.util.LinkedHashMap<>();
		}
		if (config.disabledScripts == null) {
			config.disabledScripts = new java.util.LinkedHashSet<>();
		}
		instance = config;
		config.save();
	}

	private static DnzConfig load() {
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE)) {
				DnzConfig config = GSON.fromJson(reader, DnzConfig.class);
				if (config != null) {
					// Files from older versions don't have the newer fields
					if (config.hud == null) {
						config.hud = new java.util.LinkedHashMap<>();
					}
					if (config.disabledScripts == null) {
						config.disabledScripts = new java.util.LinkedHashSet<>();
					}
					if (config.crosshair == null) {
						config.crosshair = new Crosshair();
					}
					return config;
				}
			} catch (Exception ignored) {
			}
		}
		return new DnzConfig();
	}

	public void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(this, writer);
			}
			CloudSync.changed();
		} catch (IOException ignored) {
		}
	}
}
