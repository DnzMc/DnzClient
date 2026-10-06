package com.dnz.client.mixin;

import com.dnz.client.DnzConfig;
import com.mojang.blaze3d.platform.FramerateLimitTracker;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Less heat, steadier FPS: a laptop that runs hot slows itself down (in tests the same scene fell from about 500 to
 * 250 FPS once warm). In a world with a pause menu open (ESC, DNZ menu) the game draws at most 60 FPS; with
 * "Cool mode" on, playing is capped at twice the screen's refresh rate (frames the screen cannot show only add heat).
 * Minecraft's own limits (minimized, AFK, menus outside a world) stay as they are.
 */
@Mixin(FramerateLimitTracker.class)
public class CoolFramerateMixin {
	/** The menu FPS test measures menus without the cap. */
	private static final boolean TESTING = !System.getProperty("dnz.skia", "").isEmpty();

	@Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
	private void dnz$cool(CallbackInfoReturnable<Integer> cir) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || TESTING) {
			return;
		}
		int limit = cir.getReturnValueI();
		net.minecraft.client.gui.screens.Screen screen = mc.gui.screen();
		if (screen != null && screen.isPauseScreen()) {
			cir.setReturnValue(Math.min(limit, 60));
		} else if (DnzConfig.get().coolMode) {
			com.mojang.blaze3d.platform.Monitor monitor = mc.getWindow().findBestMonitor();
			int refresh = monitor != null ? Math.round(monitor.currentMode().getRefreshRate()) : 60;
			cir.setReturnValue(Math.min(limit, Math.max(120, refresh * 2)));
		}
	}
}
