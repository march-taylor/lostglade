package com.lostglade.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraDroneProxyMixin {
	@Unique
	private static final double LG2_DRONE_CAMERA_HEIGHT = 0.35D * 0.5D;
	@Unique
	private static final double LG2_CAMERA_ANCHOR_MAX_DISTANCE_SQR = 2.0D * 2.0D;
	@Unique
	private static final double LG2_DRONE_CAMERA_ESCAPE_STEP = 0.04D;
	@Unique
	private static final int LG2_DRONE_CAMERA_ESCAPE_XZ_RADIUS_STEPS = 4;
	@Unique
	private static final double[] LG2_DRONE_CAMERA_ESCAPE_Y_OFFSETS = new double[]{
			0.0D,
			-0.05D,
			0.05D,
			-0.10D,
			0.10D,
			-0.15D,
			-0.20D,
			-0.25D,
			-0.30D,
			-0.35D,
			-0.40D
	};

	@Inject(method = "setup", at = @At("TAIL"))
	private void lg2$lockDroneProxyCameraToDrone(
			Level level,
			Entity entity,
			boolean detached,
			boolean thirdPersonReverse,
			float partialTick,
			CallbackInfo ci
	) {
		if (detached || !(entity instanceof LocalPlayer player) || !lg2$isDroneProxyActive(player)) {
			return;
		}

		Entity root = lg2$findDroneRootPassenger(player);
		if (root == null) {
			return;
		}

		Vec3 rootPos = root.getPosition(partialTick);
		Vec3 desiredOrigin = new Vec3(rootPos.x, rootPos.y + LG2_DRONE_CAMERA_HEIGHT, rootPos.z);
		Entity cameraAnchor = lg2$findDroneCameraAnchor(level, root, desiredOrigin);
		Vec3 anchorOrigin = cameraAnchor == null ? desiredOrigin : cameraAnchor.getPosition(partialTick);
		Vec3 safeOrigin = lg2$resolveSafeDroneCameraOrigin(level, rootPos.y, anchorOrigin);
		((CameraPositionInvoker) (Object) this).lg2$setPosition(safeOrigin);
	}

	@Unique
	private static Entity lg2$findDroneRootPassenger(LocalPlayer player) {
		if (player == null) {
			return null;
		}
		for (Entity passenger : player.getPassengers()) {
			if (passenger != null && passenger.getType() == EntityType.INTERACTION) {
				return passenger;
			}
		}
		return null;
	}

	@Unique
	private static boolean lg2$isDroneProxyActive(LocalPlayer player) {
		if (player == null || !player.isInvisible() || !player.isFallFlying() || !player.isNoGravity()) {
			return false;
		}
		return lg2$findDroneRootPassenger(player) != null;
	}

	@Unique
	private static Entity lg2$findDroneCameraAnchor(Level level, Entity root, Vec3 desiredOrigin) {
		if (level == null || root == null || desiredOrigin == null) {
			return null;
		}
		Entity best = null;
		double bestDistanceSqr = LG2_CAMERA_ANCHOR_MAX_DISTANCE_SQR;
		for (Entity candidate : level.getEntities(
				root,
				root.getBoundingBox().inflate(2.0D),
				entity -> entity != null
						&& entity != root
						&& entity.getType() == EntityType.INTERACTION
						&& !root.hasPassenger(entity)
		)) {
			if (candidate.getBbWidth() > 0.08F || candidate.getBbHeight() > 0.08F) {
				continue;
			}
			double distanceSqr = candidate.position().distanceToSqr(desiredOrigin);
			if (distanceSqr < bestDistanceSqr) {
				bestDistanceSqr = distanceSqr;
				best = candidate;
			}
		}
		return best;
	}

	@Unique
	private static Vec3 lg2$resolveSafeDroneCameraOrigin(Level level, double rootY, Vec3 desiredOrigin) {
		if (level == null || desiredOrigin == null) {
			return desiredOrigin == null ? Vec3.ZERO : desiredOrigin;
		}
		if (!lg2$isCameraOriginInsideSolid(level, desiredOrigin)) {
			return desiredOrigin;
		}
		double minY = rootY + 0.01D;
		Vec3 best = null;
		double bestDistanceSqr = Double.POSITIVE_INFINITY;
		for (double yOffset : LG2_DRONE_CAMERA_ESCAPE_Y_OFFSETS) {
			double candidateY = desiredOrigin.y + yOffset;
			if (candidateY < minY) {
				continue;
			}
			for (int xStep = -LG2_DRONE_CAMERA_ESCAPE_XZ_RADIUS_STEPS; xStep <= LG2_DRONE_CAMERA_ESCAPE_XZ_RADIUS_STEPS; xStep++) {
				for (int zStep = -LG2_DRONE_CAMERA_ESCAPE_XZ_RADIUS_STEPS; zStep <= LG2_DRONE_CAMERA_ESCAPE_XZ_RADIUS_STEPS; zStep++) {
					if (xStep == 0 && zStep == 0 && yOffset == 0.0D) {
						continue;
					}
					double xOffset = xStep * LG2_DRONE_CAMERA_ESCAPE_STEP;
					double zOffset = zStep * LG2_DRONE_CAMERA_ESCAPE_STEP;
					Vec3 candidate = new Vec3(
							desiredOrigin.x + xOffset,
							candidateY,
							desiredOrigin.z + zOffset
					);
					if (lg2$isCameraOriginInsideSolid(level, candidate)) {
						continue;
					}
					double distanceSqr = xOffset * xOffset + yOffset * yOffset + zOffset * zOffset;
					if (distanceSqr < bestDistanceSqr) {
						best = candidate;
						bestDistanceSqr = distanceSqr;
					}
				}
			}
		}
		if (best != null) {
			return best;
		}
		return new Vec3(desiredOrigin.x, minY, desiredOrigin.z);
	}

	@Unique
	private static boolean lg2$isCameraOriginInsideSolid(Level level, Vec3 origin) {
		if (level == null || origin == null) {
			return false;
		}
		AABB probe = new AABB(
				origin.x - 0.04D,
				origin.y - 0.04D,
				origin.z - 0.04D,
				origin.x + 0.04D,
				origin.y + 0.04D,
				origin.z + 0.04D
		);
		return !level.noCollision(probe);
	}
}
