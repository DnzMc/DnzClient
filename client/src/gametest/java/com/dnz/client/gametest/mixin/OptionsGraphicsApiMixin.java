package com.dnz.client.gametest.mixin;

import net.minecraft.client.Options;
import net.minecraft.client.PreferredGraphicsApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tests only: -Pbench_api=vulkan|opengl picks the graphics backend (the test runner resets options.txt). */
@Mixin(Options.class)
public abstract class OptionsGraphicsApiMixin {
	@Inject(method = "load", at = @At("TAIL"))
	private void dnz$benchApi(CallbackInfo ci) {
		String api = System.getProperty("dnz.benchApi", "");
		if (api.equals("vulkan")) {
			((Options) (Object) this).preferredGraphicsBackend().set(PreferredGraphicsApi.VULKAN);
		} else if (api.equals("opengl")) {
			((Options) (Object) this).preferredGraphicsBackend().set(PreferredGraphicsApi.OPENGL);
		}
	}
}
