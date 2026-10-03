package com.dnz.schematic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Settings and the last placement, in config/dnzschematic.json. */
public final class SchematicConfig {
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("dnzschematic.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static SchematicConfig instance;

	// Last placement (restored when joining a world again)
	public String file = "";
	public int x;
	public int y;
	public int z;
	public String rotation = "NONE";
	public String mirror = "NONE";
	public boolean visible = true;

	// Layers: 0 = all, 1 = only one layer, 2 = everything up to the layer
	public int layerMode;
	/** Layer as a height inside the schematic (0 = bottom). */
	public int layer;

	// Look
	/** Blocks around the camera where see-through blocks and marks are drawn. */
	public int range = 32;
	/** See-through block strength in percent. */
	public int ghostAlpha = 55;
	public boolean showMissing = true;
	public boolean showWrong = true;
	public boolean showExtra = true;
	public boolean hud = true;

	public static SchematicConfig get() {
		if (instance == null) {
			try {
				instance = Files.exists(FILE) ? GSON.fromJson(Files.readString(FILE), SchematicConfig.class) : null;
			} catch (Exception e) {
				instance = null;
			}
			if (instance == null) {
				instance = new SchematicConfig();
			}
		}
		return instance;
	}

	public void save() {
		try {
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, GSON.toJson(this));
		} catch (Exception ignored) {
			// Settings are a convenience; a failed save must never break the game.
		}
	}
}
