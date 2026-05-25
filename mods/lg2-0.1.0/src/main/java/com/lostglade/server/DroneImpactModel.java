package com.lostglade.server;

import net.minecraft.world.phys.Vec3;

final class DroneImpactModel {
	static final double CONTROL_IMPACT_BREAK_DAMAGE = 1.0D;
	static final double SURFACE_WEAR_BREAK_LEVEL = 1.0D;
	static final double SURFACE_WEAR_MIN_TANGENTIAL_SPEED = 0.16D;
	static final double SURFACE_WEAR_BASE_GROUND_PRESSURE = 0.015D;
	static final double SURFACE_WEAR_REFERENCE_PRESSURE = 0.24D;
	static final double SURFACE_WEAR_MAX_DELTA_PER_TICK = 0.18D;
	private static final double MEANINGFUL_BLOCKED_MOVEMENT = 1.0E-5D;
	private static final double CONTROL_IMPACT_SAFE_NORMAL_SPEED = 0.22D;
	private static final double CONTROL_IMPACT_REFERENCE_NORMAL_SPEED = 0.40D;

	private DroneImpactModel() {
	}

	static float computeImpactDamage(
			Vec3 intendedMovement,
			Vec3 actualMovement,
			boolean horizontalCollision,
			boolean verticalCollision
	) {
		Forces forces = computeForces(
				intendedMovement,
				actualMovement,
				horizontalCollision,
				verticalCollision
		);
		if (forces.normalSpeed() <= CONTROL_IMPACT_SAFE_NORMAL_SPEED) {
			return 0.0F;
		}

		double angleSeverity = Math.pow(forces.angleFactor(), 0.65D);
		double normalSeverity = (forces.normalSpeed() - CONTROL_IMPACT_SAFE_NORMAL_SPEED)
				/ CONTROL_IMPACT_REFERENCE_NORMAL_SPEED;
		double glancingForgiveness = net.minecraft.util.Mth.lerp(angleSeverity, 0.48D, 1.75D);
		return (float) Math.max(0.0D, normalSeverity * glancingForgiveness);
	}

	static boolean hasMeaningfulVerticalCollision(
			Vec3 intendedMovement,
			Vec3 actualMovement,
			boolean verticalCollision,
			boolean verticalCollisionBelow
	) {
		if ((!verticalCollision && !verticalCollisionBelow) || intendedMovement == null || actualMovement == null) {
			return false;
		}
		double blockedY = Math.abs(intendedMovement.y - actualMovement.y);
		if (blockedY > MEANINGFUL_BLOCKED_MOVEMENT) {
			return true;
		}
		return false;
	}

	static boolean hasVerifiedGroundWearContact(
			Vec3 intendedMovement,
			Vec3 actualMovement,
			boolean verticalCollisionBelow,
			boolean hasSupportingBlock
	) {
		return verticalCollisionBelow
				&& hasSupportingBlock
				&& hasMeaningfulVerticalCollision(intendedMovement, actualMovement, true, true);
	}

	static SurfaceWear computeSurfaceWear(
			Vec3 intendedMovement,
			Vec3 actualMovement,
			boolean verifiedGroundContact
	) {
		if (!verifiedGroundContact || intendedMovement == null || actualMovement == null) {
			return SurfaceWear.NONE;
		}

		double tangentialSpeed = Math.sqrt(actualMovement.x * actualMovement.x + actualMovement.z * actualMovement.z);
		if (tangentialSpeed < SURFACE_WEAR_MIN_TANGENTIAL_SPEED) {
			return SurfaceWear.NONE;
		}

		Forces forces = computeForces(
				intendedMovement,
				actualMovement,
				false,
				true
		);
		double downwardPressure = Math.max(forces.normalSpeed(), SURFACE_WEAR_BASE_GROUND_PRESSURE);
		double speedFactor = net.minecraft.util.Mth.clamp(
				(tangentialSpeed - SURFACE_WEAR_MIN_TANGENTIAL_SPEED)
						/ Math.max(0.001D, DroneFlightPhysics.MAX_COMBINED_SPEED - SURFACE_WEAR_MIN_TANGENTIAL_SPEED),
				0.0D,
				1.0D
		);
		double pressureFactor = net.minecraft.util.Mth.clamp(
				downwardPressure / SURFACE_WEAR_REFERENCE_PRESSURE,
				0.0D,
				1.0D
		);
		double wearDelta = Math.min(
				SURFACE_WEAR_MAX_DELTA_PER_TICK,
				(0.018D + 0.145D * pressureFactor) * Math.pow(speedFactor, 1.35D)
		);
		return new SurfaceWear(wearDelta, speedFactor, pressureFactor);
	}

	static Forces computeForces(
			Vec3 intendedMovement,
			Vec3 actualMovement,
			boolean horizontalCollision,
			boolean verticalCollision
	) {
		if (intendedMovement == null || actualMovement == null || (!horizontalCollision && !verticalCollision)) {
			return Forces.NONE;
		}

		Vec3 blockedMovement = intendedMovement.subtract(actualMovement);
		double horizontalNormalSq = horizontalCollision
				? blockedMovement.x * blockedMovement.x + blockedMovement.z * blockedMovement.z
				: 0.0D;
		double verticalNormal = 0.0D;
		if (verticalCollision) {
			verticalNormal = Math.abs(intendedMovement.y - actualMovement.y);
		}
		double normalSpeed = Math.sqrt(horizontalNormalSq + verticalNormal * verticalNormal);
		double intendedSpeed = intendedMovement.length();
		double tangentialSpeed = Math.sqrt(Math.max(0.0D, intendedSpeed * intendedSpeed - normalSpeed * normalSpeed));
		double angleFactor = normalSpeed / Math.max(1.0E-6D, normalSpeed + tangentialSpeed);
		return new Forces(normalSpeed, tangentialSpeed, angleFactor);
	}

	record Forces(double normalSpeed, double tangentialSpeed, double angleFactor) {
		private static final Forces NONE = new Forces(0.0D, 0.0D, 0.0D);
	}

	record SurfaceWear(double delta, double speedFactor, double pressureFactor) {
		private static final SurfaceWear NONE = new SurfaceWear(0.0D, 0.0D, 0.0D);
	}
}
