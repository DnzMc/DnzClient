package com.dnz.client189.mixin;

import com.dnz.client189.engine.Regions;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.util.EnumWorldBlockLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** DNZ Engine: finished chunk pieces go into DNZ's area buffers (see Regions) instead of one buffer each. */
@Mixin(ChunkRenderDispatcher.class)
public class ChunkUploadMixin {
	@Inject(method = "uploadChunk", at = @At("HEAD"), cancellable = true)
	private void dnz$upload(EnumWorldBlockLayer layer, WorldRenderer wr, RenderChunk chunk, CompiledChunk compiled,
		CallbackInfoReturnable<ListenableFuture<Object>> cir) {
		if (Regions.active && Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
			Regions.upload(layer, wr, chunk);
			wr.setTranslation(0, 0, 0);
			cir.setReturnValue(Futures.<Object>immediateFuture(null));
		}
	}
}
