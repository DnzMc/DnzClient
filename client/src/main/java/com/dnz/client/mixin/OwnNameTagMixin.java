package com.dnz.client.mixin;

import com.dnz.client.DnzConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** DNZ badge: the player's own name (with the DNZ logo) above their head in third person (F5); the game hides it. */
@Mixin(LivingEntityRenderer.class)
public class OwnNameTagMixin {
	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("RETURN"), cancellable = true)
	private void dnz$ownName(LivingEntity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() || !DnzConfig.get().dnzBadge) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (entity == mc.player && mc.getCameraEntity() == entity && !mc.options.getCameraType().isFirstPerson()
			&& !mc.gui.hud.isHidden() && !entity.isInvisible() && !entity.isVehicle()) {
			cir.setReturnValue(true);
		}
	}
}
