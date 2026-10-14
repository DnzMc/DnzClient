package com.dnz.client.hud;

import com.dnz.client.L;
import java.lang.reflect.Method;

/** Shader module: the name of the shader pack Iris uses, or "Off" (also without Iris). Iris is read by reflection. */
final class ShaderName {
	private static boolean looked;
	private static Object api;
	private static Method inUse;
	private static Method packName;

	private ShaderName() {
	}

	static String text() {
		try {
			if (!looked) {
				looked = true;
				Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
				api = apiClass.getMethod("getInstance").invoke(null);
				inUse = apiClass.getMethod("isShaderPackInUse");
				packName = Class.forName("net.irisshaders.iris.Iris").getMethod("getCurrentPackName");
			}
			if (api != null && (boolean) inUse.invoke(api)) {
				String name = String.valueOf(packName.invoke(null));
				return name.endsWith(".zip") ? name.substring(0, name.length() - 4) : name;
			}
		} catch (Throwable ignored) {
			api = null;
		}
		return L.t("hud.shader.off");
	}
}
