package com.dnz.client.mixin;

import com.dnz.client.hud.Zoom;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies the zoom to the world field of view. */
@Mixin(Camera.class)
public class CameraMixin {
	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
	private void dnz$zoom(float partialTicks, CallbackInfoReturnable<Float> cir) {
		float multiplier = Zoom.fovMultiplier();
		if (multiplier != 1.0F) {
			cir.setReturnValue(cir.getReturnValueF() * multiplier);
		}
	}
}
