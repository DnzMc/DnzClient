package com.dnz.client189.mixin;

import com.dnz.client189.BlockOutline;
import com.dnz.client189.engine.Regions;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DNZ Engine: when the world's renderers are (re)built, the area buffers start over. */
@Mixin(RenderGlobal.class)
public class RenderGlobalMixin {
	@Inject(method = "loadRenderers", at = @At("HEAD"))
	private void dnz$reset(CallbackInfo ci) {
		Regions.reset();
	}

	@Redirect(method = "drawSelectionBox", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;color(FFFF)V"))
	private void dnz$outlineColor(float r, float g, float b, float a) {
		if (BlockOutline.on()) {
			BlockOutline.lineColor();
		} else {
			GlStateManager.color(r, g, b, a);
		}
	}

	@Redirect(method = "drawSelectionBox", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glLineWidth(F)V", remap = false))
	private void dnz$outlineWidth(float width) {
		GL11.glLineWidth(BlockOutline.on() ? width * 1.5F : width);
	}

	@Redirect(method = "drawSelectionBox", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/RenderGlobal;drawSelectionBoundingBox(Lnet/minecraft/util/AxisAlignedBB;)V"))
	private void dnz$outlineFill(AxisAlignedBB box) {
		if (BlockOutline.on()) {
			BlockOutline.fill(box);
		}
		RenderGlobal.drawSelectionBoundingBox(box);
	}
}
