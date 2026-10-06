package com.dnz.client.skia;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Experiment: an empty screen whose content Skia draws (see SkiaOverlay). */
public class SkiaDemoScreen extends Screen {
	public SkiaDemoScreen() {
		super(Component.literal("DNZ Skia"));
	}

	@Override
	protected void init() {
		SkiaOverlay.painter = SkiaDemo::paint;
	}

	@Override
	public void removed() {
		SkiaOverlay.painter = null;
	}

	@Override
	public boolean isPauseScreen() {
		return false; // measured like a menu over the running game, without the 60 FPS menu cap
	}
}
