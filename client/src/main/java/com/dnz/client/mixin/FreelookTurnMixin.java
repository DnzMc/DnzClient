package com.dnz.client.mixin;

import com.dnz.client.hud.Freelook;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freelook: while it is on, the mouse turns the camera and the player keeps looking where they were. */
@Mixin(Entity.class)
public class FreelookTurnMixin {
	@Inject(method = "turn", at = @At("HEAD"), cancellable = true)
	private void dnz$freelook(double dx, double dy, CallbackInfo ci) {
		if (Freelook.active() && (Object) this == Minecraft.getInstance().player) {
			Freelook.turn(dx, dy);
			ci.cancel();
		}
	}
}
