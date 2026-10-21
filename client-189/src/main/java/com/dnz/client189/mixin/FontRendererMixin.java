package com.dnz.client189.mixin;

import com.dnz.client189.DnzBadges;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.IResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DNZ badge: after the font (re)loads, the badge character is DNZ's logo (see DnzBadges.installGlyph). */
@Mixin(FontRenderer.class)
public class FontRendererMixin {
	@Shadow
	protected byte[] glyphWidth;

	@Inject(method = "onResourceManagerReload", at = @At("TAIL"))
	private void dnz$badge(IResourceManager manager, CallbackInfo ci) {
		DnzBadges.installGlyph(manager, this.glyphWidth);
	}
}
