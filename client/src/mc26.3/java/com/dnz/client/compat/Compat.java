package com.dnz.client.compat;

import com.mojang.blaze3d.Blaze3D;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;

/** Minecraft 26.3 specific calls. */
public final class Compat {
	private Compat() {
	}

	public static Screen optionsScreen(Screen parent) {
		return new OptionsScreen(parent, Minecraft.getInstance().options);
	}

	public static void openFolder(Path path) {
		Blaze3D.openPath(path);
	}
}
