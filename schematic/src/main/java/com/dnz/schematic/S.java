package com.dnz.schematic;

import net.minecraft.client.resources.language.I18n;

/** Translated text from assets/dnzschematic/lang (follows the game language). */
public final class S {
	private S() {
	}

	public static String t(String key, Object... args) {
		return I18n.get("dnzschematic." + key, args);
	}
}
