package com.dnz.client.mixin;

import com.dnz.client.turbo.Turbo;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** DNZ Turbo: far away decorations and drops are not drawn. */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void dnz$turbo(Entity entity, Frustum frustum, double x, double y, double z, float partialTick, CallbackInfoReturnable<Boolean> cir) {
		if (Turbo.skipEntity(entity, x, y, z)) {
			cir.setReturnValue(false);
		}
	}
}
