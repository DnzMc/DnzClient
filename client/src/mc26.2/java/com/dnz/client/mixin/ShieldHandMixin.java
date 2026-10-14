package com.dnz.client.mixin;

import com.dnz.client.hud.ShieldView;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shield module: the shield in first person lower and smaller, so it covers less of the screen (see ShieldView). */
@Mixin(ItemInHandRenderer.class)
public class ShieldHandMixin {
	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;applyItemArmTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V", shift = At.Shift.AFTER))
	private void dnz$shield(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swing, ItemStack stack, float equip, PoseStack pose, SubmitNodeCollector nodes, int light, CallbackInfo ci) {
		ShieldView.apply(stack, pose);
	}
}
