package com.dnz.client.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The game window is called "DNZ Client" (not "Minecraft 26.2 - Multiplayer (3rd-party Server)"). */
@Mixin(Minecraft.class)
public class WindowTitleMixin {
	@Inject(method = "createTitle", at = @At("RETURN"), cancellable = true)
	private void dnz$title(CallbackInfoReturnable<String> cir) {
		cir.setReturnValue("DNZ Client");
	}
}
