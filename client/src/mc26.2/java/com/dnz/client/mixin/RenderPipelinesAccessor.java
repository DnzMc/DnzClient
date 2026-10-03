package com.dnz.client.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Minecraft's interface pipeline settings, so DNZ's smooth-shape pipeline matches them exactly. */
@Mixin(RenderPipelines.class)
public interface RenderPipelinesAccessor {
	@Accessor("GUI_SNIPPET")
	static RenderPipeline.Snippet dnz$guiSnippet() {
		throw new AssertionError();
	}
}
