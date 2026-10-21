package com.dnz.client189.mixin;

import java.util.List;
import net.minecraft.client.renderer.ChunkRenderContainer;
import net.minecraft.client.renderer.chunk.RenderChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read access to the camera position and the visible pieces of Minecraft's chunk list (for DNZ's region drawing). */
@Mixin(ChunkRenderContainer.class)
public interface ChunkRenderContainerAccessor {
	@Accessor("viewEntityX")
	double dnz$viewX();

	@Accessor("viewEntityY")
	double dnz$viewY();

	@Accessor("viewEntityZ")
	double dnz$viewZ();

	@Accessor("renderChunks")
	List<RenderChunk> dnz$chunks();

	@Accessor("initialized")
	boolean dnz$initialized();
}
