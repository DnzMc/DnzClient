package com.dnz.client.skia;

import java.nio.ByteBuffer;
import java.util.function.Consumer;
import org.jetbrains.skia.Canvas;

/** Skia menus are an experiment on Minecraft 26.2 only for now: nothing is drawn here. */
public final class SkiaOverlay {
	public static volatile Consumer<Canvas> painter;
	public static volatile java.util.function.LongConsumer nanoPainter;
	public static volatile long lastNanos;

	private SkiaOverlay() {
	}

	public static ByteBuffer readFrame(int w, int h) {
		return null;
	}
}
