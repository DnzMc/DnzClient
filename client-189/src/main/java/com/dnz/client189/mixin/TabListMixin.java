package com.dnz.client189.mixin;

import com.dnz.client189.DnzBadges;
import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.client.network.NetworkPlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** DNZ badge in the player list (Tab). */
@Mixin(GuiPlayerTabOverlay.class)
public class TabListMixin {
	@Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
	private void dnz$badge(NetworkPlayerInfo info, CallbackInfoReturnable<String> cir) {
		if (DnzBadges.has(info.getGameProfile().getId())) {
			cir.setReturnValue(DnzBadges.MARK + cir.getReturnValue());
		}
	}
}
