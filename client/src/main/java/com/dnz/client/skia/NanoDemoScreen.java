package com.dnz.client.skia;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Comparison: an empty screen whose content NanoVG draws (see SkiaOverlay). */
public class NanoDemoScreen extends Screen {
	public NanoDemoScreen() {
		super(Component.literal("DNZ NanoVG"));
	}

	@Override
	protected void init() {
		SkiaOverlay.nanoPainter = NanoDemo::paint;
	}

	@Override
	public void removed() {
		SkiaOverlay.nanoPainter = null;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
