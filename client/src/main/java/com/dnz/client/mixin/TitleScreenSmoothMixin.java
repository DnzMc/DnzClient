package com.dnz.client.mixin;

import com.dnz.client.gui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Modern look: no pixel font on the title screen either (buttons, version line, yellow text), like the DNZ menu. */
@Mixin(TitleScreen.class)
public abstract class TitleScreenSmoothMixin {
	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void dnz$smoothOn(GuiGraphicsExtractor g, int mouseX, int mouseY, float a, CallbackInfo ci) {
		if (Theme.dnzStyle() && !Theme.minecraftLook()) {
			Theme.smoothAll = true;
		}
	}

	@Inject(method = "extractRenderState", at = @At("RETURN"))
	private void dnz$smoothOff(GuiGraphicsExtractor g, int mouseX, int mouseY, float a, CallbackInfo ci) {
		Theme.smoothAll = false;
	}
}
