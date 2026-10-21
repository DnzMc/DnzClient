package com.dnz.client189;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;

/** Saved DNZ settings for 1.8.9 (config/dnzclient.json): which modules are on and where HUD elements sit. */
public final class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Config instance;

	public Map<String, ModuleState> modules = new LinkedHashMap<String, ModuleState>();

	public static class ModuleState {
		public boolean enabled;
		/** Position as a fraction (0..1) of the screen, so it fits any window size. */
		public float x;
		public float y;
		public float scale = 1.0F;
		public String value = "";
	}

	private static File file() {
		return new File(Minecraft.getMinecraft().mcDataDir, "config/dnzclient.json");
	}

	public static Config get() {
		if (instance == null) {
			instance = new Config();
			File f = file();
			if (f.exists()) {
				try {
					Reader reader = new FileReader(f);
					try {
						Config loaded = GSON.fromJson(reader, Config.class);
						if (loaded != null && loaded.modules != null) {
							instance = loaded;
						}
					} finally {
						reader.close();
					}
				} catch (Exception ignored) {
				}
			}
		}
		return instance;
	}

	public void save() {
		try {
			File f = file();
			f.getParentFile().mkdirs();
			Writer writer = new FileWriter(f);
			try {
				GSON.toJson(this, writer);
			} finally {
				writer.close();
			}
		} catch (Exception ignored) {
		}
	}
}
