package com.dnz.client.mixin;

import com.dnz.client.hud.Freelook;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freelook: the third person camera uses the free look direction instead of the player's (see Freelook). */
@Mixin(Camera.class)
public abstract class FreelookCameraMixin {
	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"))
	private void dnz$freelook(float partialTicks, CallbackInfo ci) {
		if (Freelook.active()) {
			this.setRotation(Freelook.yaw(), Freelook.pitch());
		}
	}
}
