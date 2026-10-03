package com.dnz.client.mixin;

import com.dnz.client.gui.ContainerTint;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Makes the dark-gray chest/inventory titles readable on the black container style. */
@Mixin(GuiGraphicsExtractor.class)
public class ContainerTextMixin {
	@ModifyVariable(method = "text", at = @At("HEAD"), argsOnly = true, ordinal = 2)
	private int dnz$labelColor(int color) {
		return ContainerTint.tintLabel(color);
	}
}
