package com.dnz.client.nvg;

import com.dnz.client.DnzConfig;
import com.dnz.client.gui.DnzMenuScreen;
import net.minecraft.client.gui.screens.Screen;

/** NanoVG menus: whether a screen is drawn by NanoVG this frame. */
public final class Nvg {
	/** Turned off for the rest of the session when NanoVG could not start (the normal menus keep working). */
	public static volatile boolean broken;

	private Nvg() {
	}

	public static boolean active(Screen screen) {
		return screen instanceof DnzMenuScreen && DnzConfig.get().nanoVg && !broken && NvgSupport.supported();
	}
}
