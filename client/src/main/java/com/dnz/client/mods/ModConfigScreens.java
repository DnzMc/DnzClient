package com.dnz.client.mods;

import com.dnz.client.gui.DnzVisualScreen;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;

/**
 * Finds a mod's settings screen through the Mod Menu API (the standard way Fabric mods expose settings).
 * Uses reflection so DNZ Client still works when Mod Menu is not installed.
 */
public final class ModConfigScreens {
	private static Map<String, Object> factories;
	private static Method createMethod;

	private ModConfigScreens() {
	}

	public static boolean modMenuLoaded() {
		return FabricLoader.getInstance().isModLoaded("modmenu");
	}

	/** Sodium's video settings screen, or the vanilla one when Sodium is not installed. */
	public static Screen videoSettings(Screen parent) {
		if (FabricLoader.getInstance().isModLoaded("sodium")) {
			try {
				Class<?> screen = Class.forName("net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen");
				Object result = screen.getMethod("createScreen", Screen.class).invoke(null, parent);
				if (result instanceof Screen s) {
					return s;
				}
			} catch (Throwable ignored) {
				// Sodium changed its API; fall back to the vanilla screen.
			}
		}
		Minecraft mc = Minecraft.getInstance();
		return new VideoSettingsScreen(parent, mc, mc.options);
	}

	public static Optional<Function<Screen, Screen>> find(String modId) {
		if ("dnzclient".equals(modId)) {
			return Optional.of(DnzVisualScreen::new);
		}
		if (modId == null || modId.isEmpty() || !FabricLoader.getInstance().isModLoaded(modId)) {
			return Optional.empty();
		}
		Object factory = factories().get(modId);
		if (factory == null || createMethod == null) {
			return Optional.empty();
		}
		return Optional.of(parent -> {
			try {
				Object screen = createMethod.invoke(factory, parent);
				return screen instanceof Screen s ? s : null;
			} catch (Throwable t) {
				return null;
			}
		});
	}

	private static synchronized Map<String, Object> factories() {
		if (factories != null) {
			return factories;
		}
		factories = new HashMap<>();
		try {
			Class<?> factoryClass = Class.forName("com.terraformersmc.modmenu.api.ConfigScreenFactory");
			createMethod = factoryClass.getMethod("create", Screen.class);
		} catch (Throwable t) {
			return factories; // Mod Menu not installed
		}
		for (EntrypointContainer<Object> container : FabricLoader.getInstance().getEntrypointContainers("modmenu", Object.class)) {
			String owner = container.getProvider().getMetadata().getId();
			try {
				Object api = container.getEntrypoint();
				Class<?> apiClass = Class.forName("com.terraformersmc.modmenu.api.ModMenuApi");
				Object own = apiClass.getMethod("getModConfigScreenFactory").invoke(api);
				if (own != null) {
					factories.putIfAbsent(owner, own);
				}
				Object provided = apiClass.getMethod("getProvidedConfigScreenFactories").invoke(api);
				if (provided instanceof Map<?, ?> map) {
					map.forEach((id, f) -> {
						if (id instanceof String s && f != null) {
							factories.putIfAbsent(s, f);
						}
					});
				}
			} catch (Throwable ignored) {
			}
		}
		// Mods without settings still get Mod Menu's default factory, which returns null; callers handle that.
		return factories;
	}
}
