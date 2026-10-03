package com.dnz.client.mixin;

import com.dnz.client.gui.Theme;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Button labels and the title screen's yellow text are drawn through Minecraft's text collector, not GuiGraphics.text;
 * while the smooth look is on (DNZ menu, title screen) they get the smooth font and no drop shadow there too.
 */
@Mixin(targets = "net.minecraft.client.gui.GuiGraphicsExtractor$RenderingTextCollector")
public abstract class TextCollectorSmoothMixin {
	private static final String ACCEPT = "accept(Lnet/minecraft/client/gui/TextAlignment;IILnet/minecraft/client/gui/ActiveTextCollector$Parameters;Lnet/minecraft/util/FormattedCharSequence;)V";
	private static final String NEW_TEXT = "Lnet/minecraft/client/renderer/state/gui/GuiTextRenderState;<init>(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;Lorg/joml/Matrix3x2fc;IIIIZZLnet/minecraft/client/gui/navigation/ScreenRectangle;)V";

	@ModifyArg(method = ACCEPT, at = @At(value = "INVOKE", target = NEW_TEXT), index = 1)
	private FormattedCharSequence dnz$smoothText(FormattedCharSequence text) {
		return Theme.smoothAll ? Theme.smooth(text) : text;
	}

	@ModifyArg(method = ACCEPT, at = @At(value = "INVOKE", target = NEW_TEXT), index = 7)
	private boolean dnz$noShadow(boolean shadow) {
		return shadow && !Theme.smoothAll;
	}
}
