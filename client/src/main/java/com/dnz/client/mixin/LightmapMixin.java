package com.dnz.client.mixin;

import com.dnz.client.DnzConfig;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fullbright: light the world as if the player had night vision. */
@Mixin(LightmapRenderStateExtractor.class)
public class LightmapMixin {
	@Inject(method = "extract", at = @At("TAIL"))
	private void dnz$fullbright(LightmapRenderState state, float partialTicks, CallbackInfo ci) {
		if (DnzConfig.get().fullbright && state.needsUpdate) {
			state.nightVisionEffectIntensity = 1.0F;
			state.brightness = 1.0F;
		}
	}
}
