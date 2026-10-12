package com.dnz.client.compat;

import java.nio.file.Path;
import java.io.IOException;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MultiplayerOptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.util.Util;

/** Minecraft 26.2 specific calls. */
public final class Compat {
	private Compat() {
	}

	public static Screen optionsScreen(Screen parent) {
		return new OptionsScreen(parent, Minecraft.getInstance().options, true);
	}

	/** Vanilla "Open to LAN" screen (Open World card); the bundled hosting mod makes it reachable from the internet. */
	public static Screen openWorldScreen(Screen parent) {
		return new MultiplayerOptionsScreen(parent);
	}

	public static void openFolder(Path path) {
		Util.getPlatform().openPath(path);
	}

	public static boolean customKeyDown(Minecraft mc, int key) {
		return InputConstants.isKeyDown(mc.getWindow(), key);
	}

	public static byte[] packIcon(Pack pack) throws IOException {
		try (var resources = pack.open()) {
			var supplier = resources.getRootResource("pack.png");
			if (supplier == null) return null;
			try (var input = supplier.get()) { return input.readNBytes(2_000_001); }
		}
	}
}
