package com.dnz.client.mixin;

import com.dnz.client.gui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the vanilla buttons inside the DNZ menus in the DNZ style (outside them they stay vanilla, see Theme.vanillaWidgets).
 * In the Simple style the label is also redrawn: smooth font, no drop shadow (the pixel font with
 * a shadow looks broken next to the smooth DNZ texts).
 */
@Mixin(AbstractButton.class)
public class AbstractButtonMixin {
	/** Set when this frame's vanilla label was skipped, so it is drawn smooth after the button. */
	@Unique
	private boolean dnz$smoothLabel;

	@Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true)
	private void dnz$modernButton(GuiGraphicsExtractor g, CallbackInfo ci) {
		if (Theme.vanillaWidgets()) {
			return;
		}
		ci.cancel();
		AbstractWidget self = (AbstractWidget) (Object) this;
		Theme.button(g, self.getX(), self.getY(), self.getWidth(), self.getHeight(), self.active, self.isHoveredOrFocused());
	}

	@Inject(method = "extractDefaultLabel", at = @At("HEAD"), cancellable = true)
	private void dnz$skipPixelLabel(ActiveTextCollector collector, CallbackInfo ci) {
		if (Theme.simpleStyle() && !Theme.vanillaWidgets()) {
			ci.cancel();
			this.dnz$smoothLabel = true;
		}
	}

	@Inject(method = "extractWidgetRenderState", at = @At("TAIL"))
	private void dnz$drawSmoothLabel(GuiGraphicsExtractor g, int mouseX, int mouseY, float a, CallbackInfo ci) {
		if (!this.dnz$smoothLabel) {
			return;
		}
		this.dnz$smoothLabel = false;
		AbstractWidget self = (AbstractWidget) (Object) this;
		Font font = Minecraft.getInstance().font;
		Component text = Theme.smooth(self.getMessage());
		int max = self.getWidth() - 8;
		if (font.width(text) > max) {
			// Too long for the button: cut it and add "..." (vanilla would scroll it).
			text = Theme.smooth(Component.literal(font.plainSubstrByWidth(self.getMessage().getString(), max - font.width("...")) + "..."));
		}
		int color = self.active ? 0xFFFFFFFF : 0xFF9AA0AC;
		Theme.centeredText(g, font, text, self.getX() + self.getWidth() / 2, self.getY() + (self.getHeight() - 8) / 2, color);
	}
}
