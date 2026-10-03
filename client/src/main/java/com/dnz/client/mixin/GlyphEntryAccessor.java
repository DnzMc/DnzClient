package com.dnz.client.mixin;

import com.mojang.blaze3d.font.UnbakedGlyph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A TrueType font's cached glyph, cleared when the font renders its glyphs again at another size (see SharpFonts). */
@Mixin(targets = "com.mojang.blaze3d.font.TrueTypeGlyphProvider$GlyphEntry")
public interface GlyphEntryAccessor {
	@Accessor("glyph")
	void dnz$setGlyph(UnbakedGlyph glyph);
}
