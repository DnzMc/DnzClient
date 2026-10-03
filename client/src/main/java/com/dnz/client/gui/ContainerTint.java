package com.dnz.client.gui;

import com.dnz.client.DnzConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;

/** Darkens chest / inventory textures when the "Siyah" container style is chosen. */
public final class ContainerTint {
	private static final float DARK = 0.30F;
	private static final int VANILLA_LABEL = 0xFF404040;
	private static final int DARK_LABEL = 0xFFE6E6EA;

	private ContainerTint() {
	}

	public static boolean active() {
		return DnzConfig.get().containerDark && Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?>;
	}

	public static boolean isContainerTexture(Identifier id) {
		String path = id.getPath();
		return path.startsWith("textures/gui/container/") || (path.startsWith("container/") && !path.contains("highlight"));
	}

	public static int darken(int color) {
		int a = color >>> 24;
		int r = Math.round(((color >> 16) & 0xFF) * DARK);
		int g = Math.round(((color >> 8) & 0xFF) * DARK);
		int b = Math.round((color & 0xFF) * DARK);
		return a << 24 | r << 16 | g << 8 | b;
	}

	public static int tintTexture(Identifier id, int color) {
		return active() && isContainerTexture(id) ? darken(color) : color;
	}

	public static int tintLabel(int color) {
		return color == VANILLA_LABEL && active() ? DARK_LABEL : color;
	}
}
