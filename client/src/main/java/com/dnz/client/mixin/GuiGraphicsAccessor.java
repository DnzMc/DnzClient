package com.dnz.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets DNZ add its own smooth shapes to the interface the same way Minecraft adds rectangles. */
@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsAccessor {
	@Accessor("guiRenderState")
	GuiRenderState dnz$state();

	@Accessor("scissorStack")
	GuiGraphicsExtractor.ScissorStack dnz$scissor();
}
