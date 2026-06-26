package com.lostglade.server;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

final class MonitorScrollAnimationSystem {
	private static final double SMOOTHING_PER_SECOND = 14.0D;
	private static final double SNAP_EPSILON = 0.01D;
	private static final long STALE_STATE_NANOS = TimeUnit.SECONDS.toNanos(20L);
	private static final Map<ScrollAnimationKey, ScrollAnimationState> STATES = new ConcurrentHashMap<>();

	private MonitorScrollAnimationSystem() {
	}

	static ScrollVisualState sample(ScreenRuntimeKey runtimeKey, ScrollChannel channel, int target, int maxTarget) {
		int clampedTarget = MonitorScreenSystem.clampInt(target, 0, Math.max(0, maxTarget));
		if (runtimeKey == null || channel == null) {
			return new ScrollVisualState(clampedTarget, 0.0D, clampedTarget);
		}

		long now = System.nanoTime();
		ScrollAnimationKey key = new ScrollAnimationKey(runtimeKey, channel);
		ScrollAnimationState state = STATES.computeIfAbsent(key, ignored -> new ScrollAnimationState(clampedTarget, now));
		synchronized (state) {
			advanceLocked(state, now);
			state.lastTouchedAtNanos = now;
			if (Math.abs(state.target - clampedTarget) > 1.0E-4D) {
				state.target = clampedTarget;
			}
			if (maxTarget <= 0) {
				state.value = 0.0D;
				state.target = 0.0D;
			}
			double display = MonitorScreenSystem.clampDouble(state.value, 0.0D, Math.max(0, maxTarget));
			int anchor = MonitorScreenSystem.clampInt((int) Math.floor(display + 1.0E-6D), 0, Math.max(0, maxTarget));
			double fraction = MonitorScreenSystem.clampDouble(display - anchor, 0.0D, 0.999999D);
			return new ScrollVisualState(anchor, fraction, display);
		}
	}

	static void tick(MinecraftServer server) {
		if (server == null || STATES.isEmpty()) {
			return;
		}

		long now = System.nanoTime();
		var staleKeys = new ArrayList<ScrollAnimationKey>();
		for (Map.Entry<ScrollAnimationKey, ScrollAnimationState> entry : STATES.entrySet()) {
			ScrollAnimationKey key = entry.getKey();
			ScrollAnimationState state = entry.getValue();
			if (key == null || state == null) {
				if (key != null) {
					staleKeys.add(key);
				}
				continue;
			}

			boolean active;
			boolean stale;
			synchronized (state) {
				advanceLocked(state, now);
				active = Math.abs(state.target - state.value) > SNAP_EPSILON;
				stale = !active && now - state.lastTouchedAtNanos > STALE_STATE_NANOS;
			}

			if (stale) {
				staleKeys.add(key);
				continue;
			}
			if (active) {
				MonitorScreenSystem.requestRuntimeRender(server, key.runtimeKey());
			}
		}
		for (ScrollAnimationKey key : staleKeys) {
			STATES.remove(key);
		}
	}

	static void clear() {
		STATES.clear();
	}

	private static void advanceLocked(ScrollAnimationState state, long now) {
		if (state == null) {
			return;
		}
		long elapsedNanos = Math.max(0L, now - state.lastUpdateAtNanos);
		if (elapsedNanos == 0L) {
			return;
		}
		state.lastUpdateAtNanos = now;
		if (Math.abs(state.target - state.value) <= SNAP_EPSILON) {
			state.value = state.target;
			return;
		}

		double deltaSeconds = elapsedNanos / 1_000_000_000.0D;
		double alpha = 1.0D - Math.exp(-SMOOTHING_PER_SECOND * deltaSeconds);
		state.value += (state.target - state.value) * alpha;
		if (Math.abs(state.target - state.value) <= SNAP_EPSILON) {
			state.value = state.target;
		}
	}

	enum ScrollChannel {
		HOME_LAUNCHER,
		MEDIA_LIBRARY_BROWSER,
		MEDIA_QUEUE_WINDOW,
		CAMERA_PICKER_CAMERAS,
		CAMERA_PICKER_MICROPHONES,
		MAX_AVATAR_PICKER,
		MAX_RINGTONE_PICKER,
		MAX_FILE_SHARE_PICKER,
		MAX_CALL_MINI_PARTICIPANTS,
		MAX_CALL_CAMERA_PICKER,
		MAX_CALL_MICROPHONE_PICKER,
		MAX_CALL_CONTACT_PICKER
	}

	record ScrollVisualState(int anchorIndex, double fraction, double displayValue) {
		boolean animated() {
			return this.fraction > 1.0E-4D;
		}
	}

	private record ScrollAnimationKey(ScreenRuntimeKey runtimeKey, ScrollChannel channel) {
	}

	private static final class ScrollAnimationState {
		private double value;
		private double target;
		private long lastUpdateAtNanos;
		private long lastTouchedAtNanos;

		private ScrollAnimationState(double initialValue, long now) {
			this.value = initialValue;
			this.target = initialValue;
			this.lastUpdateAtNanos = now;
			this.lastTouchedAtNanos = now;
		}
	}
}
