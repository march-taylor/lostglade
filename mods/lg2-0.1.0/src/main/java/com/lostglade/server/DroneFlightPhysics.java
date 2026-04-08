package com.lostglade.server;

import net.minecraft.world.phys.Vec3;

public final class DroneFlightPhysics {
	public static final double MAX_SPEED = 1.08D;
	public static final double FORWARD_THRUST = 0.112D;
	public static final double REVERSE_THRUST = 0.082D;
	public static final double STRAFE_THRUST = 0.076D;
	public static final double VERTICAL_THRUST = 0.074D;
	public static final double GRAVITY = 0.046D;
	public static final double AIR_DRAG = 0.996D;
	public static final double GLIDE_LIFT = 0.020D;
	public static final double DIVE_ACCELERATION = 0.018D;
	public static final double PLANAR_DRIVE_STEP = 0.080D;
	public static final double MAX_FORWARD_DRIVE = 0.420D;
	public static final double MAX_STRAFE_DRIVE = 0.420D;

	private DroneFlightPhysics() {
	}

	public record ControlInput(
			boolean forward,
			boolean backward,
			boolean left,
			boolean right,
			boolean up,
			boolean down
	) {
	}

	public static double adjustDrive(double currentDrive, boolean positivePressed, boolean negativePressed, double driveStep, double maxMagnitude) {
		double nextDrive = currentDrive;
		if (positivePressed && !negativePressed) {
			nextDrive += driveStep;
		} else if (negativePressed && !positivePressed) {
			nextDrive -= driveStep;
		}
		return net.minecraft.util.Mth.clamp(nextDrive, -maxMagnitude, maxMagnitude);
	}

	public static Vec3 computeAcceleration(float pitch, float yaw, double forwardDrive, double strafeDrive, boolean jumpPressed, boolean descendPressed) {
		Vec3 forward = Vec3.directionFromRotation(pitch, yaw);
		Vec3 horizontalForward = Vec3.directionFromRotation(0.0F, yaw);
		Vec3 right = new Vec3(-horizontalForward.z, 0.0D, horizontalForward.x);
		if (right.lengthSqr() <= 1.0E-6D) {
			right = new Vec3(1.0D, 0.0D, 0.0D);
		} else {
			right = right.normalize();
		}

		Vec3 up = right.cross(forward);
		if (up.lengthSqr() <= 1.0E-6D) {
			up = new Vec3(0.0D, 1.0D, 0.0D);
		} else {
			up = up.normalize();
		}

		Vec3 acceleration = new Vec3(0.0D, -GRAVITY, 0.0D);
		if (Math.abs(forwardDrive) > 1.0E-6D) {
			double forwardScale = forwardDrive >= 0.0D ? FORWARD_THRUST : REVERSE_THRUST;
			acceleration = acceleration.add(forward.scale(forwardDrive * forwardScale));
		}
		if (Math.abs(strafeDrive) > 1.0E-6D) {
			acceleration = acceleration.add(right.scale(strafeDrive * STRAFE_THRUST));
		}
		if (jumpPressed) {
			acceleration = acceleration.add(up.scale(VERTICAL_THRUST));
		}
		if (descendPressed) {
			acceleration = acceleration.add(up.scale(-VERTICAL_THRUST));
		}
		return acceleration;
	}

	public static Vec3 step(Vec3 currentVelocity, float pitch, float yaw, double forwardDrive, double strafeDrive, ControlInput input, Vec3 externalAcceleration) {
		Vec3 velocity = currentVelocity == null ? Vec3.ZERO : currentVelocity;
		Vec3 acceleration = computeAcceleration(
				pitch,
				yaw,
				forwardDrive,
				strafeDrive,
				input != null && input.up(),
				input != null && input.down()
		);
		if (externalAcceleration != null) {
			acceleration = acceleration.add(externalAcceleration);
		}

		Vec3 look = Vec3.directionFromRotation(pitch, yaw).normalize();
		double speed = velocity.length();
		double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
		if (horizontalSpeed > 1.0E-6D) {
			acceleration = acceleration.add(0.0D, horizontalSpeed * GLIDE_LIFT * Math.max(0.0D, 1.0D - Math.max(0.0D, look.y)), 0.0D);
		}
		double diveFactor = Math.max(0.0D, -look.y);
		if (diveFactor > 1.0E-6D && speed > 1.0E-6D) {
			acceleration = acceleration.add(look.scale(diveFactor * DIVE_ACCELERATION * Math.max(0.35D, speed)));
		}

		return integrateVelocity(velocity, acceleration);
	}

	public static Vec3 integrateVelocity(Vec3 currentVelocity, Vec3 acceleration) {
		Vec3 nextVelocity = currentVelocity.add(acceleration).scale(AIR_DRAG);
		if (nextVelocity.length() > MAX_SPEED) {
			nextVelocity = nextVelocity.normalize().scale(MAX_SPEED);
		}
		return nextVelocity;
	}
}
