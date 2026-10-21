package com.dnz.client189.mixin;

import com.dnz.client189.engine.Regions;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.util.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DNZ Engine: a chunk piece that moves or is deleted gives its space in the area buffer back. */
@Mixin(RenderChunk.class)
public class RenderChunkMixin {
	@Inject(method = "setPosition", at = @At("HEAD"))
	private void dnz$moved(BlockPos pos, CallbackInfo ci) {
		Regions.free((RenderChunk) (Object) this);
	}

	@Inject(method = "deleteGlResources", at = @At("HEAD"))
	private void dnz$deleted(CallbackInfo ci) {
		Regions.free((RenderChunk) (Object) this);
	}
}
