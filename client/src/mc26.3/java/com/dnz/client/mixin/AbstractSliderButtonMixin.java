package com.dnz.client.mixin;

import com.dnz.client.gui.Smooth;
import com.dnz.client.gui.Theme;
import com.dnz.client.gui.Ui;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Draws sliders (FOV, volume, ...) in the DNZ style. */
@Mixin(AbstractSliderButton.class)
public class AbstractSliderButtonMixin {
	@Redirect(
		method = "extractWidgetRenderState",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V")
	)
	private void dnz$modernSlider(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h, int color) {
		if (Theme.javaStyle()) {
			g.blitSprite(pipeline, sprite, x, y, w, h, color);
			return;
		}
		AbstractWidget self = (AbstractWidget) (Object) this;
		if (Theme.dnzStyle()) {
			// DNZ: dark glass box, the part up to the value lit in the accent color, a white knob.
			if (w == 8 && self.getWidth() != 8) {
				int left = self.getX();
				int accent = self.active ? Theme.menuAccent() : 0xFF5B5F69;
				Smooth.rect(g, left + 1, y + 1, Math.max(0, x + 4 - left - 1), h - 2, 3, Theme.withAlpha(accent & 0xFFFFFF, 0.45F));
				Smooth.rect(g, x + 2, y + 2, 4, h - 4, 2, self.isHoveredOrFocused() ? 0xFFFFFFFF : 0xFFE6EAF0);
			} else {
				Ui.glass(g, x, y, w, h, 4, self.isHoveredOrFocused() ? 0xFF2E3845 : 0xFF242C37);
			}
			return;
		}
		if (w == 8 && self.getWidth() != 8) {
			// Handle
			int c = !self.active ? 0xFF6A6F7A : self.isHoveredOrFocused() ? 0xFFFFFFFF : Theme.accent();
			Theme.roundedRect(g, x + 1, y + 2, 6, h - 4, c);
		} else {
			Theme.button(g, x, y, w, h, self.active, false);
		}
	}
}
