package com.dnz.client.mixin;

import com.dnz.client.hud.DnzHud;
import com.dnz.client.hud.Zoom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** CPS counting, zoom scrolling and lower mouse sensitivity while zoomed. */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	@Inject(method = "onButton", at = @At("HEAD"))
	private void dnz$countClick(long handle, MouseButtonInfo info, int action, CallbackInfo ci) {
		if (action == 1 && Minecraft.getInstance().gui.screen() == null) {
			DnzHud.onClick(info.button());
		}
	}

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void dnz$zoomScroll(long handle, double xoffset, double yoffset, CallbackInfo ci) {
		if (yoffset != 0 && Zoom.active()) {
			Zoom.scroll(yoffset);
			ci.cancel();
		}
	}

	@ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 0)
	private double dnz$zoomSensX(double x) {
		return x * Zoom.sensitivity();
	}

	@ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 1)
	private double dnz$zoomSensY(double y) {
		return y * Zoom.sensitivity();
	}
}
