package com.dnz.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

/** Draws a picture with smooth (linear) sampling, so big pictures don't turn blocky when stretched. */
public final class SmoothBlit {
	private SmoothBlit() {
	}

	/** The whole texture into the box (x0, y0) - (x1, y1). */
	public static void blit(GuiGraphicsExtractor g, Identifier texture, int x0, int y0, int x1, int y1) {
		AbstractTexture t = Minecraft.getInstance().getTextureManager().getTexture(texture);
		g.blit(t.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR), x0, y0, x1, y1, 0, 1, 0, 1);
	}
}
