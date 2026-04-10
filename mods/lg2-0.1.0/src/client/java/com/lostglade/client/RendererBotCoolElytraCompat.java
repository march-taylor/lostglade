package com.lostglade.client;

import com.lostglade.Lg2;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class RendererBotCoolElytraCompat {
	private static final double RAD_TO_DEG = 57.29577951308232D;
	private static final double DEFAULT_WING_POWER = 1.25D;
	private static final double DEFAULT_ROLL_SMOOTHING = 0.85D;
	private static final RollOverride NO_OP = new RollOverride(false, 0.0D, false);
	private static final Map<UUID, RollSessionState> SESSION_STATES = new ConcurrentHashMap<>();
	private static volatile Access access = Access.pending();

	private RendererBotCoolElytraCompat() {
	}

	static RollOverride beginCameraRoll(UUID sessionId, Entity followTarget) {
		Access currentAccess = access();
		if (!currentAccess.available()) {
			return NO_OP;
		}
		double previousRoll;
		boolean previousFrontView;
		try {
			previousRoll = currentAccess.rollAngleField().getDouble(null);
			previousFrontView = currentAccess.isFrontViewField().getBoolean(null);
		} catch (ReflectiveOperationException exception) {
			Lg2.LOGGER.warn("Failed to read cool_elytra state for renderer bot camera", exception);
			access = Access.unavailable();
			return NO_OP;
		}

		double nextRoll = 0.0D;
		if (sessionId != null && followTarget != null) {
			nextRoll = computeSessionRoll(sessionId, followTarget, currentAccess);
		} else if (sessionId != null) {
			SESSION_STATES.remove(sessionId);
		}

		try {
			currentAccess.rollAngleField().setDouble(null, nextRoll);
			currentAccess.isFrontViewField().setBoolean(null, false);
			return new RollOverride(true, previousRoll, previousFrontView);
		} catch (ReflectiveOperationException exception) {
			Lg2.LOGGER.warn("Failed to apply cool_elytra state for renderer bot camera", exception);
			access = Access.unavailable();
			return NO_OP;
		}
	}

	static void endCameraRoll(RollOverride override) {
		if (override == null || !override.applied()) {
			return;
		}
		Access currentAccess = access();
		if (!currentAccess.available()) {
			return;
		}
		try {
			currentAccess.rollAngleField().setDouble(null, override.previousRollAngle());
			currentAccess.isFrontViewField().setBoolean(null, override.previousFrontView());
		} catch (ReflectiveOperationException exception) {
			Lg2.LOGGER.warn("Failed to restore cool_elytra state for renderer bot camera", exception);
			access = Access.unavailable();
		}
	}

	static void releaseSession(UUID sessionId) {
		if (sessionId != null) {
			SESSION_STATES.remove(sessionId);
		}
	}

	static void clearCaches() {
		SESSION_STATES.clear();
	}

	private static double computeSessionRoll(UUID sessionId, Entity followTarget, Access currentAccess) {
		if (!(followTarget instanceof LivingEntity living) || !living.isFallFlying()) {
			SESSION_STATES.remove(sessionId);
			return 0.0D;
		}
		RollSessionState state = SESSION_STATES.computeIfAbsent(sessionId, ignored -> new RollSessionState());
		long now = System.nanoTime();
		double deltaSeconds = state.lastUpdateNanos == 0L
				? 1.0D / 20.0D
				: Math.max(0.0D, (now - state.lastUpdateNanos) * 1.0E-9D);
		state.lastUpdateNanos = now;
		double targetRoll = computeClassicRollTarget(living, currentAccess.wingPower());
		double smoothing = Mth.clamp(currentAccess.rollSmoothing(), 0.0D, 1.0D);
		double nextRoll = targetRoll + Math.pow(smoothing, deltaSeconds * 40.0D) * (state.lastRollAngle - targetRoll);
		state.lastRollAngle = nextRoll;
		return nextRoll;
	}

	private static double computeClassicRollTarget(LivingEntity followTarget, double wingPower) {
		Vec3 look = followTarget.getLookAngle();
		Vec3 velocity = followTarget.getDeltaMovement();
		double lookHorizontal = look.x * look.x + look.z * look.z;
		double velocityHorizontal = velocity.x * velocity.x + velocity.z * velocity.z;
		if (lookHorizontal <= 1.0E-6D || velocityHorizontal <= 1.0E-6D) {
			return 0.0D;
		}
		double cosine = (velocity.x * look.x + velocity.z * look.z) / Math.sqrt(lookHorizontal * velocityHorizontal);
		cosine = Mth.clamp(cosine, -1.0D, 1.0D);
		double sign = Math.signum(velocity.x * look.z - velocity.z * look.x);
		return Math.atan(Math.sqrt(velocityHorizontal) * Math.acos(cosine) * wingPower) * sign * RAD_TO_DEG;
	}

	private static Access access() {
		Access current = access;
		if (!current.needsInitialization()) {
			return current;
		}
		synchronized (RendererBotCoolElytraCompat.class) {
			current = access;
			if (!current.needsInitialization()) {
				return current;
			}
			access = initializeAccess();
			return access;
		}
	}

	private static Access initializeAccess() {
		if (!FabricLoader.getInstance().isModLoaded("cool_elytra")) {
			return Access.unavailable();
		}
		try {
			Class<?> clientClass = Class.forName("edu.jorbonism.cool_elytra.CoolElytraClient");
			Class<?> configClass = Class.forName("edu.jorbonism.cool_elytra.config.CoolElytraConfig");
			Field rollAngleField = clientClass.getDeclaredField("rollAngle");
			Field isFrontViewField = clientClass.getDeclaredField("isFrontView");
			Field wingPowerField = configClass.getDeclaredField("wingPower");
			Field rollSmoothingField = configClass.getDeclaredField("rollSmoothing");
			rollAngleField.setAccessible(true);
			isFrontViewField.setAccessible(true);
			wingPowerField.setAccessible(true);
			rollSmoothingField.setAccessible(true);
			double wingPower = wingPowerField.getDouble(null);
			double rollSmoothing = rollSmoothingField.getDouble(null);
			return new Access(true, false, rollAngleField, isFrontViewField, wingPower, rollSmoothing);
		} catch (ReflectiveOperationException exception) {
			Lg2.LOGGER.warn("Failed to initialize cool_elytra compatibility for renderer bot camera", exception);
			return Access.unavailable();
		}
	}

	record RollOverride(boolean applied, double previousRollAngle, boolean previousFrontView) {
	}

	private record Access(
			boolean available,
			boolean needsInitialization,
			Field rollAngleField,
			Field isFrontViewField,
			double wingPower,
			double rollSmoothing
	) {
		private static Access pending() {
			return new Access(false, true, null, null, DEFAULT_WING_POWER, DEFAULT_ROLL_SMOOTHING);
		}

		private static Access unavailable() {
			return new Access(false, false, null, null, DEFAULT_WING_POWER, DEFAULT_ROLL_SMOOTHING);
		}
	}

	private static final class RollSessionState {
		private long lastUpdateNanos;
		private double lastRollAngle;
	}
}
