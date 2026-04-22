package com.lostglade.mixin;

import com.lostglade.server.DroneSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityDroneOperatorKnockbackMixin {
	@Unique
	private Vec3 lg2$droneOperatorPreKnockbackVelocity;

	@Inject(method = "knockback", at = @At("HEAD"))
	private void lg2$captureDroneOperatorKnockbackVelocity(double strength, double x, double z, CallbackInfo ci) {
		if (!((Object) this instanceof ServerPlayer player) || !DroneSystem.isControllingDrone(player)) {
			this.lg2$droneOperatorPreKnockbackVelocity = null;
			return;
		}
		this.lg2$droneOperatorPreKnockbackVelocity = player.getDeltaMovement();
	}

	@Inject(method = "knockback", at = @At("TAIL"))
	private void lg2$recordDroneOperatorKnockbackVelocity(double strength, double x, double z, CallbackInfo ci) {
		if (!((Object) this instanceof ServerPlayer player)
				|| !DroneSystem.isControllingDrone(player)
				|| this.lg2$droneOperatorPreKnockbackVelocity == null) {
			return;
		}
		Vec3 velocity = player.getDeltaMovement();
		if (velocity.distanceToSqr(this.lg2$droneOperatorPreKnockbackVelocity) > 1.0E-7D) {
			DroneSystem.recordControlledOperatorKnockback(player, velocity);
		}
		this.lg2$droneOperatorPreKnockbackVelocity = null;
	}
}
