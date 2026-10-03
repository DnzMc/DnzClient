package com.dnz.client.mixin;

import com.dnz.client.MacRetina;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** macOS: the Retina setting is given to the window right before it is created (26.2; 26.3 creates the window elsewhere, so it is optional). */
@Mixin(Window.class)
public abstract class WindowRetinaMixin {
	@Inject(method = "createGlfwWindow", require = 0, at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J", remap = false))
	private static void dnz$retina(CallbackInfoReturnable<Long> cir) {
		MacRetina.applyHint();
	}
}
