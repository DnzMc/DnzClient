package com.dnz.client189.mixin;

import com.dnz.client189.engine.Culling;
import com.dnz.client189.engine.Engine;
import java.util.List;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DNZ Engine: at most Culling.PARTICLE_LIMIT particles at once, and particles behind the camera or off screen
 * are not drawn at all (vanilla draws every particle, also the ones you can't see).
 */
@Mixin(EffectRenderer.class)
public class EffectRendererMixin {
	@Shadow
	private List<EntityFX>[][] fxLayers;

	private Frustum dnz$frustum;

	@Inject(method = "addEffect", at = @At("HEAD"), cancellable = true)
	private void dnz$cap(EntityFX effect, CallbackInfo ci) {
		if (!Culling.enabled()) {
			return;
		}
		int count = 0;
		for (List<EntityFX>[] layer : this.fxLayers) {
			for (List<EntityFX> list : layer) {
				count += list.size();
			}
		}
		if (count >= Culling.PARTICLE_LIMIT) {
			ci.cancel();
		}
	}

	@Inject(method = "renderParticles", at = @At("HEAD"))
	private void dnz$frustum(Entity viewer, float partialTicks, CallbackInfo ci) {
		this.dnz$frustum = Engine.enabled() ? Engine.frustum(viewer, partialTicks) : null;
	}

	@Redirect(method = "renderParticles", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/particle/EntityFX;renderParticle(Lnet/minecraft/client/renderer/WorldRenderer;Lnet/minecraft/entity/Entity;FFFFFF)V"))
	private void dnz$visibleOnly(EntityFX fx, WorldRenderer wr, Entity viewer, float partialTicks, float rx, float rz, float ryz, float rxy, float rxz) {
		if (this.dnz$frustum == null || this.dnz$frustum.isBoundingBoxInFrustum(fx.getEntityBoundingBox().expand(0.5, 0.5, 0.5))) {
			fx.renderParticle(wr, viewer, partialTicks, rx, rz, ryz, rxy, rxz);
		}
	}
}
