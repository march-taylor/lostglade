package com.lostglade.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BlockGetter;
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

	@Inject(method = "setup", at = @At("TAIL"))
	private void lg2$lockDroneProxyCameraToDrone(
			BlockGetter level,
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
		Vec3 safeOrigin = lg2$resolveSafeDroneCameraOrigin(player, rootPos.y, desiredOrigin);
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
	private static Vec3 lg2$resolveSafeDroneCameraOrigin(LocalPlayer player, double rootY, Vec3 desiredOrigin) {
		if (player == null || desiredOrigin == null || player.level() == null) {
			return desiredOrigin == null ? Vec3.ZERO : desiredOrigin;
		}
		if (!lg2$isCameraOriginInsideSolid(player, desiredOrigin)) {
			return desiredOrigin;
		}

		double minY = rootY + 0.01D;
		for (int step = 1; step <= 8; step++) {
			double candidateY = desiredOrigin.y - step * 0.05D;
			if (candidateY < minY) {
				break;
			}
			Vec3 candidate = new Vec3(desiredOrigin.x, candidateY, desiredOrigin.z);
			if (!lg2$isCameraOriginInsideSolid(player, candidate)) {
				return candidate;
			}
		}
		return new Vec3(desiredOrigin.x, minY, desiredOrigin.z);
	}

	@Unique
	private static boolean lg2$isCameraOriginInsideSolid(LocalPlayer player, Vec3 origin) {
		if (player == null || origin == null || player.level() == null) {
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
		return !player.level().noCollision(probe);
	}
}
