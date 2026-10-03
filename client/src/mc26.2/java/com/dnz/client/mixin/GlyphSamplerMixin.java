package com.dnz.client.mixin;

import com.dnz.client.gui.SharpFonts;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Text of our fonts is sampled smoothly instead of pixel by pixel (see SharpFonts). */
@Mixin(GlyphRenderState.class)
public abstract class GlyphSamplerMixin {
	@Shadow
	@Final
	private TextRenderable renderable;

	@ModifyArg(method = "textureSetup", at = @At(value = "INVOKE",
		target = "Lcom/mojang/blaze3d/systems/SamplerCache;getClampToEdge(Lcom/mojang/blaze3d/textures/FilterMode;)Lcom/mojang/blaze3d/textures/GpuSampler;"))
	private FilterMode dnz$smooth(FilterMode mode) {
		return SharpFonts.smooth(this.renderable.textureView()) ? FilterMode.LINEAR : mode;
	}
}
