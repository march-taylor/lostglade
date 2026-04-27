package com.lostglade.mixin.client;

import com.lostglade.network.DronePayloads;
import com.lostglade.server.DroneFlightPhysics;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class LocalPlayerDroneProxyMixin {
	@Unique
	private static final double LG2_MIN_CONTROL_DRIVE_STEP = 0.055D;
	@Unique
	private static final double LG2_MAX_CONTROL_DRIVE_STEP = 0.500D;

	@Unique
	private double lg2$forwardDrive;
	@Unique
	private double lg2$strafeDrive;
	@Unique
	private boolean lg2$proxyWasActive;
	@Unique
	private long lg2$lastKineticCollisionReportTick = Long.MIN_VALUE;

	@Inject(method = "travel", at = @At("HEAD"), cancellable = true)
	private void lg2$applyDroneProxyTravel(Vec3 travelVector, CallbackInfo ci) {
		if (!((Object) this instanceof LocalPlayer player) || !lg2$isDroneProxyActive(player)) {
			lg2$resetProxyDriveState();
			return;
		}

		ClientInput input = player.input;
		Input keyPresses = input == null ? null : ((ClientInputAccessor) input).lg2$getKeyPresses();
		if (keyPresses == null) {
			keyPresses = Input.EMPTY;
		}

		double driveStep = lg2$getControlDriveStep((Player) (Object) this);
		this.lg2$forwardDrive = DroneFlightPhysics.adjustDrive(
				this.lg2$forwardDrive,
				keyPresses.forward(),
				keyPresses.backward(),
				driveStep,
				DroneFlightPhysics.MAX_FORWARD_DRIVE
		);
		this.lg2$strafeDrive = DroneFlightPhysics.adjustDrive(
				this.lg2$strafeDrive,
				keyPresses.right(),
				keyPresses.left(),
				driveStep,
				DroneFlightPhysics.MAX_STRAFE_DRIVE
		);

		player.noPhysics = false;
		player.setNoGravity(true);
		player.fallDistance = 0.0F;
		Vec3 nextVelocity = DroneFlightPhysics.step(
				player.getXRot(),
				player.getYRot(),
				this.lg2$forwardDrive,
				this.lg2$strafeDrive
		);
		Vec3 startPos = player.position();
		double horizontalSpeedBefore = nextVelocity.horizontalDistance();
		player.setDeltaMovement(nextVelocity);
		player.move(MoverType.SELF, nextVelocity);
		Vec3 actualMovement = player.position().subtract(startPos);
		lg2$reportKineticCollision(player, horizontalSpeedBefore, actualMovement.horizontalDistance());
		this.lg2$proxyWasActive = true;
		ci.cancel();
	}

	@Inject(method = "aiStep", at = @At("TAIL"))
	private void lg2$stabilizeDroneProxyState(CallbackInfo ci) {
		LocalPlayer player = (LocalPlayer) (Object) this;
		if (!lg2$isDroneProxyActive(player)) {
			lg2$resetProxyDriveState();
			return;
		}

		player.noPhysics = false;
		player.setNoGravity(true);
		player.fallDistance = 0.0F;
		this.lg2$proxyWasActive = true;
	}

	@Unique
	private void lg2$resetProxyDriveState() {
		if (!this.lg2$proxyWasActive) {
			return;
		}
		this.lg2$proxyWasActive = false;
		this.lg2$forwardDrive = 0.0D;
		this.lg2$strafeDrive = 0.0D;
		this.lg2$lastKineticCollisionReportTick = Long.MIN_VALUE;
	}

	@Unique
	private void lg2$reportKineticCollision(LocalPlayer player, double horizontalSpeedBefore, double horizontalSpeedAfter) {
		if (player == null || !player.horizontalCollision) {
			return;
		}
		if (lg2$computeFallFlyingKineticDamage(horizontalSpeedBefore, horizontalSpeedAfter) <= 0.0F) {
			return;
		}
		long gameTime = player.level().getGameTime();
		if (this.lg2$lastKineticCollisionReportTick == gameTime) {
			return;
		}
		this.lg2$lastKineticCollisionReportTick = gameTime;
		if (ClientPlayNetworking.canSend(DronePayloads.DroneKineticCollisionC2SPayload.TYPE)) {
			ClientPlayNetworking.send(new DronePayloads.DroneKineticCollisionC2SPayload(horizontalSpeedBefore, horizontalSpeedAfter));
		}
	}

	@Unique
	private static float lg2$computeFallFlyingKineticDamage(double horizontalSpeedBefore, double horizontalSpeedAfter) {
		if (!Double.isFinite(horizontalSpeedBefore) || !Double.isFinite(horizontalSpeedAfter)) {
			return 0.0F;
		}
		double speedLoss = Math.max(0.0D, horizontalSpeedBefore - horizontalSpeedAfter);
		return (float) Math.max(0.0D, speedLoss * 10.0D - 3.0D);
	}

	@Unique
	private static double lg2$getControlDriveStep(Player player) {
		if (player == null) {
			return LG2_MIN_CONTROL_DRIVE_STEP;
		}
		int selectedSlot = Mth.clamp(player.getInventory().getSelectedSlot(), 0, 8);
		double normalized = selectedSlot / 8.0D;
		return Mth.lerp(normalized, LG2_MIN_CONTROL_DRIVE_STEP, LG2_MAX_CONTROL_DRIVE_STEP);
	}

	@Unique
	private static boolean lg2$isDroneProxyActive(LocalPlayer player) {
		if (player == null || !player.isInvisible() || !player.isFallFlying() || !player.isNoGravity()) {
			return false;
		}
		for (Entity passenger : player.getPassengers()) {
			if (passenger != null && passenger.getType() == EntityType.INTERACTION) {
				return true;
			}
		}
		return false;
	}
}
