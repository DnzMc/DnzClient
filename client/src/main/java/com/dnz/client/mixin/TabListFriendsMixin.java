package com.dnz.client.mixin;

import com.dnz.client.DnzBadges;
import com.dnz.client.Friends;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** DNZ friends: a green star before friends' names in the player list (Tab); DNZ badge for DNZ Client players. */
@Mixin(PlayerTabOverlay.class)
public class TabListFriendsMixin {
	@Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
	private void dnz$friend(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
		Component name = cir.getReturnValue();
		if (DnzBadges.has(info.getProfile().id())) {
			name = DnzBadges.badge().append(name);
		}
		if (Friends.isFriend(info.getProfile().name())) {
			name = Component.literal("★ ").withColor(0x55E36B).append(name);
		}
		cir.setReturnValue(name);
	}
}
