package com.dnz.client189.mixin;

import com.dnz.client189.engine.Culling;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DNZ Engine: far signs, banners and heads are not drawn (see Culling). */
@Mixin(TileEntityRendererDispatcher.class)
public class TileEntityRendererDispatcherMixin {
	@Inject(method = "renderTileEntity", at = @At("HEAD"), cancellable = true)
	private void dnz$skipFar(TileEntity te, float partialTicks, int destroyStage, CallbackInfo ci) {
		if (Culling.skipTileEntity(te) || com.dnz.client189.engine.Occlusion.hidden(te)) {
			ci.cancel();
		}
	}
}
