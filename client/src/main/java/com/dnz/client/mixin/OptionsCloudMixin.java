package com.dnz.client.mixin;

import com.dnz.client.CloudSync;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DNZ Cloud: saved options (key bindings among them) are sent to the player's cloud copy a little later. */
@Mixin(Options.class)
public class OptionsCloudMixin {
	@Inject(method = "save", at = @At("TAIL"))
	private void dnz$cloud(CallbackInfo ci) {
		CloudSync.changed();
	}
}
