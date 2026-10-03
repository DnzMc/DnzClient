package com.dnz.client.mixin;

import com.dnz.client.gui.SharpFonts;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import java.nio.ByteBuffer;
import net.minecraft.client.gui.font.CodepointMap;
import net.minecraft.client.gui.font.providers.FreeTypeUtil;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FT_Vector;
import org.lwjgl.util.freetype.FreeType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets a TrueType font render its glyphs again at another size (see SharpFonts): the same FreeType setup Minecraft
 * does when the font loads, with the new detail, and the glyphs made so far are forgotten.
 */
@Mixin(TrueTypeGlyphProvider.class)
public abstract class TrueTypeProviderMixin implements SharpFonts.Sharp {
	@Shadow
	private FT_Face face;

	@Shadow
	@Final
	@Mutable
	private float oversample;

	@Shadow
	@Final
	private CodepointMap<Object> glyphs;

	@Unique
	private float dnz$size;
	@Unique
	private float dnz$shiftX;
	@Unique
	private float dnz$shiftY;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void dnz$remember(ByteBuffer memory, FT_Face face, float size, float oversample, float shiftX, float shiftY, String skip,
		CallbackInfo ci) {
		this.dnz$size = size;
		this.dnz$shiftX = shiftX;
		this.dnz$shiftY = shiftY;
	}

	@Override
	public void dnz$setOversample(float value) {
		FT_Face current = this.face;
		if (current == null || value == this.oversample) {
			return;
		}
		// Minecraft loads glyphs while holding the face, so nothing is half loaded while the size changes.
		synchronized (current) {
			if (this.face == null) {
				return; // closed meanwhile (resource reload)
			}
			int pixels = Math.max(1, Math.round(this.dnz$size * value));
			FreeType.FT_Set_Pixel_Sizes(current, pixels, pixels);
			try (MemoryStack stack = MemoryStack.stackPush()) {
				FT_Vector shift = FreeTypeUtil.setVector(FT_Vector.malloc(stack), this.dnz$shiftX * value, -this.dnz$shiftY * value);
				FreeType.FT_Set_Transform(current, null, shift);
			}
			this.oversample = value;
			this.glyphs.forEach((codepoint, entry) -> ((GlyphEntryAccessor) entry).dnz$setGlyph(null));
		}
	}
}
