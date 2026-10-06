package com.dnz.client.compat;

import java.nio.file.Path;
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
}
