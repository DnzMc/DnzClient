package com.dnz.client.compat;

import java.nio.file.Path;
import net.minecraft.client.Minecraft;
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

	public static void openFolder(Path path) {
		Util.getPlatform().openPath(path);
	}
}
