package com.dnz.client.mixin;

import com.dnz.client.hud.ChatHeads;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Module "chatheads": room for the writer's head on the first line of a chat message. */
@Mixin(GuiMessage.class)
abstract class ChatHeadsLinesMixin {
	@ModifyArg(method = "splitLines", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/components/ComponentRenderUtils;wrapComponents(Lnet/minecraft/network/chat/FormattedText;ILnet/minecraft/client/gui/Font;)Ljava/util/List;"),
		index = 0)
	private FormattedText dnz$indent(FormattedText content, int width, Font font) {
		return ChatHeads.indent((GuiMessage) (Object) this, content, font);
	}

	@Inject(method = "splitLines", at = @At("RETURN"))
	private void dnz$lines(Font font, int width, CallbackInfoReturnable<List<FormattedCharSequence>> cir) {
		ChatHeads.lines((GuiMessage) (Object) this, cir.getReturnValue());
	}
}
