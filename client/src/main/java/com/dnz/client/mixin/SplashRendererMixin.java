package com.dnz.client.mixin;

import com.dnz.client.gui.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.SplashRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The tilted yellow title screen text is hidden in the Simple style (clean title screen). */
@Mixin(SplashRenderer.class)
public class SplashRendererMixin {
	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void dnz$hideInSimple(GuiGraphicsExtractor g, int width, Font font, float alpha, CallbackInfo ci) {
		if (Theme.simpleStyle()) {
			ci.cancel();
		}
	}
}
