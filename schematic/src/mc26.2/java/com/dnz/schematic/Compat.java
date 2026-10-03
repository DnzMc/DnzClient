package com.dnz.schematic;

import java.nio.file.Path;
import net.minecraft.util.Util;

/** Minecraft 26.2 specific calls. */
public final class Compat {
	private Compat() {
	}

	public static void openFolder(Path path) {
		Util.getPlatform().openPath(path);
	}
}
