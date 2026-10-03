package com.dnz.client.mixin;

import com.dnz.client.gui.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FontDescription;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** While the DNZ menu draws, text in Minecraft's pixel font is measured with the smooth font instead. */
@Mixin(Font.class)
public abstract class FontSmoothMixin {
	@ModifyVariable(method = "getGlyphSource", at = @At("HEAD"), argsOnly = true)
	private FontDescription dnz$smooth(FontDescription font) {
		return Theme.smoothAll ? Theme.menuFont(font) : font;
	}
}
