package com.dnz.client.mixin;

import com.dnz.client.DnzBadges;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** DNZ badge: the DNZ logo before the name above the head of players who play with DNZ Client. */
@Mixin(EntityRenderer.class)
public class EntityNameBadgeMixin {
	@Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
	private void dnz$badge(Entity entity, CallbackInfoReturnable<Component> cir) {
		Component name = cir.getReturnValue();
		if (name != null && entity instanceof Player && DnzBadges.has(entity.getUUID())) {
			cir.setReturnValue(DnzBadges.badge().append(name));
		}
	}
}
