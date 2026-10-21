package com.dnz.client189.mixin;

import com.dnz.client189.engine.Culling;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** DNZ Engine: far decorations and drops are not drawn (see Culling). */
@Mixin(RenderManager.class)
public class RenderManagerMixin {
	@Inject(method = "renderEntityStatic", at = @At("HEAD"), cancellable = true)
	private void dnz$skipFar(Entity entity, float partialTicks, boolean hideDebugBox, CallbackInfoReturnable<Boolean> cir) {
		if (Culling.skipEntity(entity) || com.dnz.client189.engine.Occlusion.hidden(entity)) {
			cir.setReturnValue(false);
		}
	}
}
