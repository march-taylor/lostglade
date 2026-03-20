package com.lostglade.mixin;

import com.lostglade.server.ServerAbsoluteInvisibilitySystem;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class ServerLevelAbsoluteInvisibilityMixin {
	@Inject(method = "sendParticles(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/core/particles/ParticleOptions;ZZDDDIDDDD)Z", at = @At("HEAD"), cancellable = true)
	private void lg2$suppressAbsoluteInvisibilityMovementParticles(
			ServerPlayer viewer,
			ParticleOptions particle,
			boolean longDistance,
			boolean overrideLimiter,
			double x,
			double y,
			double z,
			int count,
			double xDist,
			double yDist,
			double zDist,
			double maxSpeed,
			CallbackInfoReturnable<Boolean> cir
	) {
		if (viewer != null && ServerAbsoluteInvisibilitySystem.shouldSuppressParticlePacketFor(viewer, particle, x, y, z, count)) {
			cir.setReturnValue(false);
		}
	}
}
