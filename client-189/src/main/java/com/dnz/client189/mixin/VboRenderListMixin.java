package com.dnz.client189.mixin;

import com.dnz.client189.engine.Regions;
import net.minecraft.client.renderer.VboRenderList;
import net.minecraft.util.EnumWorldBlockLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DNZ Engine: the world's chunk pieces are drawn per area with one call (see Regions) instead of one by one. */
@Mixin(VboRenderList.class)
public class VboRenderListMixin {
	@Inject(method = "renderChunkLayer", at = @At("HEAD"), cancellable = true)
	private void dnz$regions(EnumWorldBlockLayer layer, CallbackInfo ci) {
		if (!Regions.active) {
			return;
		}
		ChunkRenderContainerAccessor self = (ChunkRenderContainerAccessor) (Object) this;
		if (self.dnz$initialized()) {
			Regions.draw(self.dnz$chunks(), layer, self.dnz$viewX(), self.dnz$viewY(), self.dnz$viewZ());
			self.dnz$chunks().clear();
		}
		ci.cancel();
	}
}
