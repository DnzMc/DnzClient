package com.dnz.client.mixin;

import com.dnz.client.nvg.Nvg;
import com.dnz.client.nvg.NvgRecorder;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NanoVG menus: record a DNZ menu's content and tooltips. */
@Mixin(Screen.class)
public class ScreenNvgMixin {
	// After the background: Minecraft's blur and dimming stay Minecraft's (the blur needs its own layer).
	@Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
	private void dnz$begin(GuiGraphicsExtractor g, int mouseX, int mouseY, float a, CallbackInfo ci) {
		if (Nvg.active((Screen) (Object) this)) {
			NvgRecorder.begin();
		}
	}

	@Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("RETURN"))
	private void dnz$end(GuiGraphicsExtractor g, int mouseX, int mouseY, float a, CallbackInfo ci) {
		if (NvgRecorder.recording) {
			NvgRecorder.end();
		}
	}
}
