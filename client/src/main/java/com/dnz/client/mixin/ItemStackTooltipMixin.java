package com.dnz.client.mixin;

import com.dnz.client.hud.ShulkerPreview;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/** Filled shulker boxes get the grid preview as their tooltip image. */
@Mixin(ItemStack.class)
public abstract class ItemStackTooltipMixin {
	@Inject(method = "getTooltipImage", at = @At("HEAD"), cancellable = true)
	private void dnz$shulkerPreview(CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
		Optional<TooltipComponent> preview = ShulkerPreview.of((ItemStack) (Object) this);
		if (preview.isPresent()) {
			cir.setReturnValue(preview);
		}
	}
}
