package com.dnz.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The font manager, to make our glyphs again after their size changed (see SharpFonts). */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
	@Accessor("fontManager")
	FontManager dnz$fontManager();
}
