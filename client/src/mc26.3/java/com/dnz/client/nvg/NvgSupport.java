package com.dnz.client.nvg;

/** NanoVG menus are not on Minecraft 26.3 yet: its normal DNZ menus are used. */
public final class NvgSupport {
	private NvgSupport() {
	}

	public static boolean supported() {
		return false;
	}
}
