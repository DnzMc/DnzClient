package com.dnz.client.mixin;

import com.dnz.client.hud.DnzHud;
import com.dnz.client.hud.HudModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minecraft's own effect icons (top right) are hidden while the DNZ "Effects" HUD module shows them. */
@Mixin(Hud.class)
public class HudEffectsMixin {
	@Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
	private void dnz$onlyOurEffects(GuiGraphicsExtractor g, DeltaTracker delta, CallbackInfo ci) {
		HudModule effects = DnzHud.module("effects");
		if (effects != null && effects.enabled()) {
			ci.cancel();
		}
	}
}
