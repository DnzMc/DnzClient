package com.dnz.client.mixin;

import com.dnz.client.skia.SkiaOverlay;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NanoVG menus (and the Skia experiment): draw on top of the finished OpenGL frame right before it is shown. */
@Pseudo
@Mixin(targets = "com.mojang.blaze3d.opengl.GlSurface")
public class SkiaPresentMixin {
	@Inject(method = "present", at = @At("HEAD"), require = 0)
	private void dnz$skia(CallbackInfo ci) {
		var window = Minecraft.getInstance().getWindow();
		com.dnz.client.nvg.NvgOverlay.draw(window.getWidth(), window.getHeight());
		SkiaOverlay.draw(window.getWidth(), window.getHeight());
	}
}
