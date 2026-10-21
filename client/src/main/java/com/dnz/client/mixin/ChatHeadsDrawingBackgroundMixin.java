package com.dnz.client.mixin;

import com.dnz.client.hud.ChatHeads;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Module "chatheads": draws the writer's head before a chat line. */
@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$DrawingBackgroundGraphicsAccess")
abstract class ChatHeadsDrawingBackgroundMixin {
	@Shadow
	@Final
	private GuiGraphicsExtractor graphics;

	@Inject(method = "handleMessage", at = @At("HEAD"))
	private void dnz$head(int y, float alpha, FormattedCharSequence line, CallbackInfoReturnable<Boolean> cir) {
		ChatHeads.draw(this.graphics, y, alpha, line);
	}
}
