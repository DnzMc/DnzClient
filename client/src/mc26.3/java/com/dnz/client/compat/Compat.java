package com.dnz.client.compat;

import com.mojang.blaze3d.Blaze3D;
import java.nio.file.Path;
import java.io.IOException;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.WorldOptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;

/** Minecraft 26.3 specific calls. */
public final class Compat {
	private Compat() {
	}

	public static Screen optionsScreen(Screen parent) {
		return new OptionsScreen(parent, Minecraft.getInstance().options);
	}

	/** Vanilla world options with "Open to LAN" (Open World card); the bundled hosting mod makes it reachable from the internet. */
	public static Screen openWorldScreen(Screen parent) {
		return new WorldOptionsScreen(parent, Minecraft.getInstance().level);
	}

	public static void openFolder(Path path) {
		Blaze3D.openPath(path);
	}

	public static boolean customKeyDown(Minecraft mc, int key) {
		return InputConstants.isKeyDown(key);
	}

	public static byte[] packIcon(Pack pack) throws IOException {
		try (var resources = pack.openMetadata()) {
			var supplier = resources.getRootResource("pack.png");
			if (supplier == null) return null;
			try (var input = supplier.get()) { return input.readNBytes(2_000_001); }
		}
	}
}
