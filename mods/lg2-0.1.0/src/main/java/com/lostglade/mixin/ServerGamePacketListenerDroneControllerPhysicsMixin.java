package com.lostglade.mixin;

import com.lostglade.server.DroneSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerDroneControllerPhysicsMixin {
	@Shadow
	public ServerPlayer player;

	@Unique
	private Vec3 lg2$controlledOperatorPreTickPos;

	@Inject(method = "tickPlayer", at = @At("HEAD"))
	private void lg2$captureControlledOperatorPhysicsBaseline(CallbackInfoReturnable<Boolean> cir) {
		if (!DroneSystem.isControllingDrone(this.player)) {
			this.lg2$controlledOperatorPreTickPos = null;
			return;
		}
		this.lg2$controlledOperatorPreTickPos = this.player.position();
		Vec3 pendingKnockback = DroneSystem.consumeControlledOperatorKnockback(this.player);
		if (pendingKnockback.lengthSqr() > 1.0E-5D) {
			this.player.setDeltaMovement(pendingKnockback);
			this.player.hurtMarked = true;
		}
	}

	@Inject(method = "tickPlayer", at = @At("TAIL"))
	private void lg2$applyVanillaMovementBookkeepingForControlledOperator(CallbackInfoReturnable<Boolean> cir) {
		if (!DroneSystem.isControllingDrone(this.player) || this.lg2$controlledOperatorPreTickPos == null) {
			return;
		}

		Vec3 movement = this.player.position().subtract(this.lg2$controlledOperatorPreTickPos);
		this.player.setOnGroundWithMovement(this.player.onGround(), this.player.horizontalCollision, movement);
		this.player.doCheckFallDamage(movement.x, movement.y, movement.z, this.player.onGround());

		if (movement.lengthSqr() > 1.0E-5D) {
			this.player.resetLastActionTime();
		}
		this.player.setKnownMovement(movement);
		((ServerGamePacketListenerImplAccessor) this).lg2$setReceivedMovementThisTick(true);

		if (movement.y > 0.0D) {
			this.player.resetFallDistance();
		}
		if (!this.player.onGround()
				&& !this.player.hasLandedInLiquid()
				&& !this.player.onClimbable()
				&& !this.player.isSpectator()
				&& !this.player.isFallFlying()
				&& !this.player.isAutoSpinAttack()) {
			this.player.tryResetCurrentImpulseContext();
		}

		this.player.checkMovementStatistics(movement.x, movement.y, movement.z);
		this.lg2$controlledOperatorPreTickPos = null;
	}

	@Redirect(
			method = "tickPlayer",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/level/ServerPlayer;absSnapTo(DDDFF)V"
			)
	)
	private void lg2$preserveRealBodyPhysicsWhileControllingDrone(
			ServerPlayer player,
			double x,
			double y,
			double z,
			float yRot,
			float xRot
	) {
		if (DroneSystem.isControllingDrone(player)) {
			return;
		}
		player.absSnapTo(x, y, z, yRot, xRot);
	}
}
