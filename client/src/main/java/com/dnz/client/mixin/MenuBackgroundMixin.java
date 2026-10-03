package com.dnz.client.mixin;

import com.dnz.client.DnzConfig;
import com.dnz.client.gui.SmoothBlit;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Title, Singleplayer and Multiplayer screens: DNZ pictures instead of the panorama (a sunset on the title screen,
 * another picture behind the world and server lists). The pictures are already blurred, so Minecraft's own menu blur
 * is skipped there, and they are drawn with smooth sampling so they never look blocky on big screens.
 */
@Mixin(Screen.class)
public abstract class MenuBackgroundMixin {
	private static final Identifier TITLE_PICTURE = Identifier.fromNamespaceAndPath("dnzclient", "textures/gui/title_background.png");
	private static final Identifier LIST_PICTURE = Identifier.fromNamespaceAndPath("dnzclient", "textures/gui/menu_background.png");
	private static final float PICTURE_W = 1920;
	private static final float PICTURE_H = 1080;

	@Shadow
	public int width;
	@Shadow
	public int height;

	private boolean dnz$picture() {
		Object self = this;
		return (self instanceof TitleScreen || self instanceof SelectWorldScreen || self instanceof JoinMultiplayerScreen) && !DnzConfig.get().javaStyle;
	}

	@Inject(method = "extractPanorama", at = @At("HEAD"), cancellable = true)
	private void dnz$panorama(GuiGraphicsExtractor g, float alpha, CallbackInfo ci) {
		if (!this.dnz$picture()) {
			return;
		}
		// Fill the whole screen without stretching: scale up until both sides are covered, crop the rest evenly.
		float scale = Math.max(this.width / PICTURE_W, this.height / PICTURE_H);
		int w = (int) Math.ceil(PICTURE_W * scale);
		int h = (int) Math.ceil(PICTURE_H * scale);
		int x = (this.width - w) / 2;
		int y = (this.height - h) / 2;
		SmoothBlit.blit(g, (Object) this instanceof TitleScreen ? TITLE_PICTURE : LIST_PICTURE, x, y, x + w, y + h);
		ci.cancel();
	}

	@Inject(method = "extractBlurredBackground", at = @At("HEAD"), cancellable = true)
	private void dnz$noBlur(GuiGraphicsExtractor g, CallbackInfo ci) {
		if (this.dnz$picture()) {
			ci.cancel();
		}
	}
}
