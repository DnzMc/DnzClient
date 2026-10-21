package com.dnz.client.mixin;

import com.dnz.client.hud.BlockOutline;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Module "blockoutline": DNZ's own frame around the looked-at block instead of vanilla's. */
@Mixin(LevelRenderer.class)
abstract class BlockOutlineMixin {
	@Inject(method = "submitHitOutline", at = @At("HEAD"), cancellable = true)
	private void dnz$blockOutline(PoseStack pose, SubmitNodeCollector collector, RenderType type, BlockOutlineRenderState state, int color,
		float width, boolean translucent, CallbackInfo ci) {
		if (BlockOutline.submit(pose, collector, type, state, width, translucent)) {
			ci.cancel();
		}
	}
}
