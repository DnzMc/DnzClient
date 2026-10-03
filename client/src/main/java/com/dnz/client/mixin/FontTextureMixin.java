package com.dnz.client.mixin;

import com.dnz.client.gui.SharpFonts;
import java.util.function.Supplier;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Remembers the glyph atlases of our fonts, so they are drawn with smooth sampling (see SharpFonts). */
@Mixin(FontTexture.class)
public abstract class FontTextureMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void dnz$register(Supplier<String> label, GlyphRenderTypes renderTypes, boolean colored, CallbackInfo ci) {
		SharpFonts.registerAtlas(label.get(), ((FontTexture) (Object) this).getTextureView());
	}
}
