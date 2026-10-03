package com.dnz.client.mixin;

import com.dnz.client.gui.ContainerTint;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Darkens chest / inventory textures for the black container style.
@Mixin(GuiGraphicsExtractor.class)
public class ContainerBlitMixin {
	@ModifyVariable(method = "innerBlit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIFFFFI)V", at = @At("HEAD"), argsOnly = true, ordinal = 4)
	private int dnz$tintTexture(int color, @Local(argsOnly = true) Identifier location) {
		return ContainerTint.tintTexture(location, color);
	}

	@ModifyVariable(method = "blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;IIIIIIIII)V", at = @At("HEAD"), argsOnly = true, ordinal = 8)
	private int dnz$tintSprite(int color, @Local(argsOnly = true) TextureAtlasSprite sprite) {
		return ContainerTint.tintTexture(sprite.contents().name(), color);
	}
}
