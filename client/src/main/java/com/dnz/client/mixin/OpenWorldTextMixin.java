package com.dnz.client.mixin;

import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Open World: the bundled hosting mod's chat messages ("world hosted on ...", errors) use DNZ's own texts
 * (dnzclient.openworld.* in our language files) instead of its name.
 */
@Pseudo
@Mixin(targets = "link.e4mc.Mirror", remap = false)
public class OpenWorldTextMixin {
	private static final String PREFIX = "text.e4mc_minecraft.";

	@Inject(method = "translatable", at = @At("HEAD"), cancellable = true, require = 0)
	private static void dnz$text(String key, Object[] args, CallbackInfoReturnable<Component> cir) {
		if (key.startsWith(PREFIX)) {
			cir.setReturnValue(Component.translatable("dnzclient.openworld." + key.substring(PREFIX.length()), args));
		}
	}
}
