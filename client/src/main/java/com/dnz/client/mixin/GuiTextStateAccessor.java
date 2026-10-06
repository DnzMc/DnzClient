package com.dnz.client.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** NanoVG menus: reads a text piece of the interface. */
@Mixin(GuiTextRenderState.class)
public interface GuiTextStateAccessor {
	@Accessor("font")
	Font dnz$font();

	@Accessor("text")
	FormattedCharSequence dnz$text();

	@Accessor("x")
	int dnz$x();

	@Accessor("y")
	int dnz$y();

	@Accessor("color")
	int dnz$color();

	@Accessor("dropShadow")
	boolean dnz$shadow();
}
