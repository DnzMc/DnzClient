package com.dnz.schematic;

import com.mojang.blaze3d.Blaze3D;
import java.nio.file.Path;

/** Minecraft 26.3 specific calls. */
public final class Compat {
	private Compat() {
	}

	public static void openFolder(Path path) {
		Blaze3D.openPath(path);
	}
}
