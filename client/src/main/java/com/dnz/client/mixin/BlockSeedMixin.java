package com.dnz.client.mixin;

import com.dnz.client.DnzConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Streamer mode: every block uses the same model variant, so randomly rotated textures
 * (grass, stone, sand, ...) can no longer reveal coordinates in screenshots or streams.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public class BlockSeedMixin {
	@Inject(method = "getSeed", at = @At("HEAD"), cancellable = true)
	private void dnz$fixedSeed(BlockPos pos, CallbackInfoReturnable<Long> cir) {
		if (DnzConfig.get().streamerMode) {
			cir.setReturnValue(0L);
		}
	}
}
