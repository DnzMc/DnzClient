package com.dnz.client.mixin;

import com.dnz.client.gui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LogoRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LogoRenderer.class)
public class LogoRendererMixin {
	@Shadow
	@Final
	private boolean keepLogoThroughFade;

	/** Replace the Minecraft logo with "DNZ Client" + "Powered by Sodium and Sodium Extra". */
	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IFI)V", at = @At("HEAD"), cancellable = true)
	private void dnz$drawLogo(GuiGraphicsExtractor g, int width, float alpha, int heightOffset, CallbackInfo ci) {
		ci.cancel();
		float a = this.keepLogoThroughFade ? 1.0F : alpha;
		Font font = Minecraft.getInstance().font;
		int color = Theme.withAlpha(0xFFFFFF, a);
		if (Theme.simpleStyle()) {
			// Sharp bold letters (same as the ESC menu title), not the pixel font blown up 4 times.
			Theme.spacedText(g, font, "DNZ CLIENT", width / 2, heightOffset + 6, 2.6F, 2, color, 0.0F);
			Theme.centeredText(g, font, Theme.poweredBy(), width / 2, heightOffset + 38, color);
			return;
		}
		if (Theme.dnzStyle() && !Theme.minecraftLook()) {
			// Modern look: sharp bold "DNZ CLIENT" made for the screen's real size, not the pixel font blown up.
			Theme.logo(g, font, width / 2, heightOffset + 4, 2.7F, a);
			Theme.scaledCentered(g, font, Theme.poweredBy(), width / 2, heightOffset + 38, 1.0F, color);
			return;
		}
		Theme.scaledCentered(g, font, Theme.brand(), width / 2, heightOffset + 2, 4.0F, color);
		Theme.scaledCentered(g, font, Theme.poweredBy(), width / 2, heightOffset + 38, 1.0F, color);
	}
}
