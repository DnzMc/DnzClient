package com.dnz.client189.mixin;

import com.dnz.client189.engine.Engine;
import net.minecraft.client.renderer.entity.RenderEntityItem;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.entity.item.EntityItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** DNZ Engine: a dropped stack further than a few blocks away is drawn as one item instead of up to five copies. */
@Mixin(RenderEntityItem.class)
public class RenderEntityItemMixin {
	@Inject(method = "func_177077_a", at = @At("RETURN"), cancellable = true)
	private void dnz$oneCopy(EntityItem item, double x, double y, double z, float partialTicks, IBakedModel model, CallbackInfoReturnable<Integer> cir) {
		if (cir.getReturnValueI() > 1 && Engine.farItem(x, y, z)) {
			cir.setReturnValue(1);
		}
	}
}
