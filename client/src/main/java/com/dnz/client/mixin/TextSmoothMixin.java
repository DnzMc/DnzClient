package com.dnz.client.mixin;

import com.dnz.client.gui.Palette;
import com.dnz.client.gui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While the DNZ menu draws: every text in Minecraft's pixel font is drawn with the menu font (all text goes through
 * here), colors follow the menu theme, and in the Minecraft look the pixel font is put on whole screen pixels.
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class TextSmoothMixin {
	private static final String TEXT = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V";

	@Unique
	private boolean dnz$snapped;

	@ModifyVariable(method = TEXT, at = @At("HEAD"), argsOnly = true)
	private FormattedCharSequence dnz$smooth(FormattedCharSequence text) {
		return Theme.smoothAll ? Theme.smooth(text) : text;
	}

	/** No Minecraft drop shadow in the DNZ menu (it doubles the letters like pixel art). */
	@ModifyVariable(method = TEXT, at = @At("HEAD"), argsOnly = true)
	private boolean dnz$noShadow(boolean shadow) {
		return shadow && !Theme.smoothAll;
	}

	@ModifyVariable(method = TEXT, at = @At("HEAD"), argsOnly = true, ordinal = 2)
	private int dnz$textColor(int color) {
		return Palette.map(color);
	}

	/**
	 * Minecraft look: the pixel font is drawn at a whole number of screen pixels per font pixel, starting on a
	 * screen pixel, so every letter stays even and sharp (in between sizes would make some lines thicker).
	 */
	@Inject(method = TEXT, at = @At("HEAD"))
	private void dnz$snap(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow, CallbackInfo ci) {
		this.dnz$snapped = false;
		if (!Theme.smoothAll || !Theme.minecraftLook() || !Theme.pixelText(text)) {
			return;
		}
		Matrix3x2fStack pose = ((GuiGraphicsExtractor) (Object) this).pose();
		if (pose.m01() != 0 || pose.m10() != 0 || pose.m00() != pose.m11()) {
			return;
		}
		float gui = (float) Minecraft.getInstance().getWindow().getGuiScale();
		float k = Math.max(1, Math.round(pose.m00() * gui)) / gui;
		float sx = Math.round((pose.m00() * x + pose.m20()) * gui) / gui;
		float sy = Math.round((pose.m11() * y + pose.m21()) * gui) / gui;
		pose.pushMatrix();
		pose.set(k, 0, 0, k, sx - k * x, sy - k * y);
		this.dnz$snapped = true;
	}

	@Inject(method = TEXT, at = @At("RETURN"))
	private void dnz$unsnap(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow, CallbackInfo ci) {
		if (this.dnz$snapped) {
			this.dnz$snapped = false;
			((GuiGraphicsExtractor) (Object) this).pose().popMatrix();
		}
	}

	@ModifyVariable(method = {"fill(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;IIIII)V",
		"fill(Lcom/mojang/blaze3d/pipeline/RenderPipeline;IIIII)V"}, at = @At("HEAD"), argsOnly = true, ordinal = 4)
	private int dnz$fillColor(int color) {
		return Palette.map(color);
	}

	@ModifyVariable(method = "fillGradient(IIIIII)V", at = @At("HEAD"), argsOnly = true, ordinal = 4)
	private int dnz$gradientTop(int color) {
		return Palette.map(color);
	}

	@ModifyVariable(method = "fillGradient(IIIIII)V", at = @At("HEAD"), argsOnly = true, ordinal = 5)
	private int dnz$gradientBottom(int color) {
		return Palette.map(color);
	}
}
