package com.dnz.client.mixin;

import com.dnz.client.hud.ShulkerPreview;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/** With the shulker preview on, the vanilla text list of the contents is left out (the grid shows them). */
@Mixin(ItemContainerContents.class)
public abstract class ItemContainerContentsMixin {
	@Inject(method = "addToTooltip", at = @At("HEAD"), cancellable = true)
	private void dnz$hideList(Item.TooltipContext context, Consumer<Component> lines, TooltipFlag flag, DataComponentGetter getter, CallbackInfo ci) {
		if (getter instanceof ItemStack stack && ShulkerPreview.hidesTextList(stack)) {
			ci.cancel();
		}
	}
}
