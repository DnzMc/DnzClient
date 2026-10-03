package com.dnz.client.mixin;

import com.dnz.client.turbo.Turbo;
import java.util.Map;
import java.util.Queue;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DNZ Turbo: at most {@link Turbo#PARTICLE_LIMIT} particles at once; new ones are dropped above that. */
@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {
	@Shadow
	@Final
	private Map<ParticleRenderType, ParticleGroup<?>> particles;

	/** New particles wait here until the next tick; they count too (a burst of 5000 arrives in one tick). */
	@Shadow
	@Final
	private Queue<Particle> particlesToAdd;

	@Inject(method = "add", at = @At("HEAD"), cancellable = true)
	private void dnz$turboParticleLimit(Particle particle, CallbackInfo ci) {
		if (!Turbo.enabled()) {
			return;
		}
		int count = this.particlesToAdd.size();
		for (ParticleGroup<?> group : this.particles.values()) {
			count += group.size();
		}
		if (count >= Turbo.PARTICLE_LIMIT) {
			ci.cancel();
		}
	}
}
