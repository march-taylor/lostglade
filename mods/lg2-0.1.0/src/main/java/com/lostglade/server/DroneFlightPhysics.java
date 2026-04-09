package com.lostglade.server;

import net.minecraft.world.phys.Vec3;

public final class DroneFlightPhysics {
	public static final double MAX_FORWARD_SPEED = 0.96D;
	public static final double MAX_REVERSE_SPEED = 0.78D;
	public static final double MAX_STRAFE_SPEED = 0.74D;
	public static final double MAX_COMBINED_SPEED = 1.02D;
	public static final double GRAVITY_UP_BRAKE = 0.008D;
	public static final double GRAVITY_DOWN_ACCEL = 0.006D;
	public static final double PLANAR_DRIVE_STEP = 0.080D;
	public static final double MAX_FORWARD_DRIVE = 1.0D;
	public static final double MAX_STRAFE_DRIVE = 1.0D;

	private DroneFlightPhysics() {
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

	public static Vec3 step(float pitch, float yaw, double forwardDrive, double strafeDrive) {
		Vec3 velocity = computeDriveVelocity(pitch, yaw, forwardDrive, strafeDrive);
		return applyGravityBias(velocity);
	}

	public static Vec3 computeDriveVelocity(float pitch, float yaw, double forwardDrive, double strafeDrive) {
		Vec3 forward = Vec3.directionFromRotation(pitch, yaw);
		Vec3 horizontalForward = Vec3.directionFromRotation(0.0F, yaw);
		Vec3 right = new Vec3(-horizontalForward.z, 0.0D, horizontalForward.x);
		if (right.lengthSqr() <= 1.0E-6D) {
			right = new Vec3(1.0D, 0.0D, 0.0D);
		} else {
			right = right.normalize();
		}

		Vec3 velocity = Vec3.ZERO;
		if (Math.abs(forwardDrive) > 1.0E-6D) {
			double forwardScale = forwardDrive >= 0.0D ? MAX_FORWARD_SPEED : MAX_REVERSE_SPEED;
			velocity = velocity.add(forward.scale(forwardDrive * forwardScale));
		}
		if (Math.abs(strafeDrive) > 1.0E-6D) {
			velocity = velocity.add(right.scale(strafeDrive * MAX_STRAFE_SPEED));
		}
		if (velocity.lengthSqr() > MAX_COMBINED_SPEED * MAX_COMBINED_SPEED) {
			velocity = velocity.normalize().scale(MAX_COMBINED_SPEED);
		}
		return velocity;
	}

	public static Vec3 applyGravityBias(Vec3 velocity) {
		double vertical = velocity.y;
		if (vertical > 0.0D) {
			vertical = Math.max(0.0D, vertical - GRAVITY_UP_BRAKE);
		} else if (vertical < 0.0D) {
			vertical -= GRAVITY_DOWN_ACCEL;
		}
		Vec3 nextVelocity = new Vec3(velocity.x, vertical, velocity.z);
		if (nextVelocity.lengthSqr() > MAX_COMBINED_SPEED * MAX_COMBINED_SPEED) {
			nextVelocity = nextVelocity.normalize().scale(MAX_COMBINED_SPEED);
		}
		return nextVelocity;
	}
}
