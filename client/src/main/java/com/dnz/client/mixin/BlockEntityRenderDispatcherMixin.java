package com.dnz.client.mixin;

import com.dnz.client.turbo.Turbo;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** DNZ Turbo: far away signs, banners and heads are not drawn (their text can't be read from there anyway). */
@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {
	@Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
	private void dnz$turbo(BlockEntity blockEntity, float partialTick, ModelFeatureRenderer.CrumblingOverlay crumbling, boolean flag,
		CallbackInfoReturnable<BlockEntityRenderState> cir) {
		if (Turbo.skipBlockEntity(blockEntity)) {
			cir.setReturnValue(null);
		}
	}
}
