package com.dnz.client.mixin;

import com.dnz.client.gui.SharpFonts;
import com.mojang.blaze3d.font.GlyphProvider;
import net.minecraft.client.gui.font.providers.TrueTypeGlyphProviderDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Our fonts get their glyphs rendered at the screen's real resolution and are remembered for later sizes (see SharpFonts). */
@Mixin(TrueTypeGlyphProviderDefinition.class)
public abstract class FontOversampleMixin {
	@Shadow
	@Final
	private Identifier location;

	@ModifyArg(method = "load", at = @At(value = "INVOKE",
		target = "Lcom/mojang/blaze3d/font/TrueTypeGlyphProvider;<init>(Ljava/nio/ByteBuffer;Lorg/lwjgl/util/freetype/FT_Face;FFFFLjava/lang/String;)V"), index = 3)
	private float dnz$oversample(float oversample) {
		return SharpFonts.oversample(this.location, oversample);
	}

	@Inject(method = "load", at = @At("RETURN"))
	private void dnz$register(ResourceManager resources, CallbackInfoReturnable<GlyphProvider> cir) {
		SharpFonts.register(this.location, cir.getReturnValue());
	}
}
