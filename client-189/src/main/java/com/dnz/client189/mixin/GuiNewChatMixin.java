package com.dnz.client189.mixin;

import com.dnz.client189.ChatHeads;
import java.util.List;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.GuiUtilRenderComponents;
import net.minecraft.util.IChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Chat Heads module: room for the writer's head on a message's first line, and the head drawn there. */
@Mixin(GuiNewChat.class)
public class GuiNewChatMixin {
	@Redirect(method = "setChatLine", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiUtilRenderComponents;splitText(Lnet/minecraft/util/IChatComponent;ILnet/minecraft/client/gui/FontRenderer;ZZ)Ljava/util/List;"))
	private List<IChatComponent> dnz$split(IChatComponent message, int width, FontRenderer font, boolean a, boolean b) {
		List<IChatComponent> lines = GuiUtilRenderComponents.splitText(ChatHeads.indent(message), width, font, a, b);
		ChatHeads.lines(lines);
		return lines;
	}

	@Redirect(method = "drawChat", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/FontRenderer;drawStringWithShadow(Ljava/lang/String;FFI)I"))
	private int dnz$line(FontRenderer font, String text, float x, float y, int color) {
		ChatHeads.draw(text, x, y, color);
		return font.drawStringWithShadow(text, x, y, color);
	}
}
