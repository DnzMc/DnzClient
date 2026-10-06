package com.dnz.client.mods;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Mod Menu adds "(N Mods)" to the version line at the bottom left of the title screen. DNZ turns that off in Mod
 * Menu's own setting (a count on the Mods button stays). Read by reflection: Mod Menu is optional.
 */
public final class ModMenuTitleCount {
	private ModMenuTitleCount() {
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void hide() {
		if (!FabricLoader.getInstance().isModLoaded("modmenu")) {
			return;
		}
		try {
			Class<?> config = Class.forName("com.terraformersmc.modmenu.config.ModMenuConfig");
			Object option = config.getField("MOD_COUNT_LOCATION").get(null);
			Class<Enum> locations = (Class<Enum>) Class.forName("com.terraformersmc.modmenu.config.ModMenuConfig$ModCountLocation");
			String current = ((Enum<?>) option.getClass().getMethod("getValue").invoke(option)).name();
			String wanted = switch (current) {
				case "TITLE_SCREEN" -> "NONE";
				case "TITLE_SCREEN_AND_MODS_BUTTON" -> "MODS_BUTTON";
				default -> null;
			};
			if (wanted == null) {
				return;
			}
			for (Method m : option.getClass().getMethods()) {
				if (m.getName().equals("setValue") && m.getParameterCount() == 1) {
					m.invoke(option, Enum.valueOf(locations, wanted));
					Class.forName("com.terraformersmc.modmenu.config.ModMenuConfigManager").getMethod("save").invoke(null);
					return;
				}
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			// Another Mod Menu version: the count just stays.
		}
	}
}
