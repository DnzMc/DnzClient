package com.dnz.client189.mixin;

import com.dnz.client189.engine.AnimatedTextures;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DNZ Engine: animated block textures that are not on screen are not sent to the graphics card. */
@Mixin(TextureMap.class)
public class TextureMapMixin {
	@Redirect(method = "updateAnimations", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;updateAnimation()V"))
	private void dnz$visibleOnly(TextureAtlasSprite sprite) {
		if (AnimatedTextures.animate(sprite)) {
			sprite.updateAnimation();
		}
	}

	@Inject(method = "updateAnimations", at = @At("TAIL"))
	private void dnz$done(CallbackInfo ci) {
		AnimatedTextures.updated();
	}
}
