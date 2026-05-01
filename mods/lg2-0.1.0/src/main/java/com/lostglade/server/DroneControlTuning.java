package com.lostglade.server;

public final class DroneControlTuning {
	public static final double MIN_DRIVE_STEP = 0.055D;
	public static final double MAX_DRIVE_STEP = 0.500D;

	private DroneControlTuning() {
	}

	public static double driveStepForSlot(int controlSpeedSlot) {
		double normalized = net.minecraft.util.Mth.clamp(controlSpeedSlot, 0, 8) / 8.0D;
		return net.minecraft.util.Mth.lerp(normalized, MIN_DRIVE_STEP, MAX_DRIVE_STEP);
	}
}
