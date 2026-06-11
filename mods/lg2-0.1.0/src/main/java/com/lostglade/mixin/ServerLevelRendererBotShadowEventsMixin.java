package com.lostglade.mixin;

import com.lostglade.server.RendererBotCameraSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockEventData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class ServerLevelRendererBotShadowEventsMixin {
	@Inject(
			method = "runBlockEvents",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/players/PlayerList;broadcast(Lnet/minecraft/world/entity/player/Player;DDDDLnet/minecraft/resources/ResourceKey;Lnet/minecraft/network/protocol/Packet;)V"
			),
			locals = LocalCapture.CAPTURE_FAILHARD
	)
	private void lg2$mirrorShadowBlockEvents(CallbackInfo ci, BlockEventData data) {
		if (data == null) {
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

	@Inject(method = "playSeededSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V", at = @At("TAIL"))
	private void lg2$mirrorShadowPositionedSound(
			Entity entity,
			double x,
			double y,
			double z,
			Holder<SoundEvent> sound,
			SoundSource source,
			float volume,
			float pitch,
			long seed,
			CallbackInfo ci
	) {
		RendererBotCameraSystem.mirrorTransientSound((ServerLevel) (Object) this, sound, source, x, y, z, volume, pitch, seed);
	}

	@Inject(method = "playSeededSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V", at = @At("TAIL"))
	private void lg2$mirrorShadowEntitySound(
			Entity excludedEntity,
			Entity entity,
			Holder<SoundEvent> sound,
			SoundSource source,
			float volume,
			float pitch,
			long seed,
			CallbackInfo ci
	) {
		RendererBotCameraSystem.mirrorTransientEntitySound((ServerLevel) (Object) this, sound, source, entity, volume, pitch, seed);
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
