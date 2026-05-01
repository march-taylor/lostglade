package com.lostglade.server;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class DroneGeometry {
	public static final float WIDTH = 0.78F;
	public static final float HEIGHT = 0.28F;

	private DroneGeometry() {
	}

	public static AABB boxAt(Vec3 position) {
		Vec3 safePosition = position == null ? Vec3.ZERO : position;
		double halfWidth = WIDTH * 0.5D;
		return new AABB(
				safePosition.x - halfWidth,
				safePosition.y,
				safePosition.z - halfWidth,
				safePosition.x + halfWidth,
				safePosition.y + HEIGHT,
				safePosition.z + halfWidth
		);
	}

	public static Vec3 cameraOrigin(Vec3 rootPosition) {
		if (rootPosition == null) {
			return Vec3.ZERO;
		}
		return new Vec3(rootPosition.x, rootPosition.y + HEIGHT * 0.5D, rootPosition.z);
	}
}
