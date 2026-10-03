package com.dnz.client;

import net.minecraft.client.resources.language.I18n;

/** Translated text from assets/dnzclient/lang (follows the game language). */
public final class L {
	private L() {
	}

	public static String t(String key, Object... args) {
		return I18n.get("dnzclient." + key, args);
	}
}
