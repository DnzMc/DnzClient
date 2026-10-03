package com.dnz.client.mixin;

import com.dnz.client.script.ScriptManager;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells DNZ Script mods when a key is pressed (key("K", ...)). */
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
	@Inject(method = "keyPress", at = @At("HEAD"))
	private void dnz$scriptKeys(long window, int action, KeyEvent event, CallbackInfo ci) {
		if (action == 1) { // pressed (not released, not held down)
			ScriptManager.onKeyPress(event.input());
		}
	}
}
