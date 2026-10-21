package com.dnz.client189;

import net.minecraft.client.Minecraft;

/** Turkish when the game is in Turkish, English otherwise. */
public final class Lang {
	private Lang() {
	}

	public static boolean tr() {
		String code = Minecraft.getMinecraft().gameSettings.language;
		return code != null && code.toLowerCase().startsWith("tr");
	}

	public static String t(String turkish, String english) {
		return tr() ? turkish : english;
	}
}
