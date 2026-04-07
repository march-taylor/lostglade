package com.lostglade.mixin;

import com.lostglade.server.RendererBotCameraSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockEventData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class ServerLevelRendererBotShadowEventsMixin {
	@Inject(method = "doBlockEvent", at = @At("TAIL"))
	private void lg2$mirrorShadowBlockEvents(BlockEventData data, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() || data == null) {
			return;
		}
		RendererBotCameraSystem.mirrorTransientBlockEvent(
				(ServerLevel) (Object) this,
				data.pos(),
				data.block(),
				data.paramA(),
				data.paramB()
		);
	}

	@Inject(method = "destroyBlockProgress", at = @At("TAIL"))
	private void lg2$mirrorShadowBlockDestruction(int breakerId, BlockPos pos, int progress, CallbackInfo ci) {
		RendererBotCameraSystem.mirrorTransientBlockDestruction((ServerLevel) (Object) this, breakerId, pos, progress);
	}

	@Inject(method = "globalLevelEvent", at = @At("TAIL"))
	private void lg2$mirrorShadowGlobalLevelEvent(int type, BlockPos pos, int data, CallbackInfo ci) {
		RendererBotCameraSystem.mirrorTransientLevelEvent((ServerLevel) (Object) this, type, pos, data, true);
	}

	@Inject(method = "levelEvent", at = @At("TAIL"))
	private void lg2$mirrorShadowLevelEvent(net.minecraft.world.entity.Entity entity, int type, BlockPos pos, int data, CallbackInfo ci) {
		RendererBotCameraSystem.mirrorTransientLevelEvent((ServerLevel) (Object) this, type, pos, data, false);
	}

	@Inject(
			method = "sendParticles(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDIDDDD)I",
			at = @At("TAIL")
	)
	private <T extends ParticleOptions> void lg2$mirrorShadowParticles(
			T particle,
			boolean overrideLimiter,
			boolean alwaysShow,
			double x,
			double y,
			double z,
			int count,
			double xDist,
			double yDist,
			double zDist,
			double maxSpeed,
			CallbackInfoReturnable<Integer> cir
	) {
		RendererBotCameraSystem.mirrorTransientParticles(
				(ServerLevel) (Object) this,
				particle,
				overrideLimiter,
				alwaysShow,
				x,
				y,
				z,
				count,
				xDist,
				yDist,
				zDist,
				maxSpeed
		);
	}
}
