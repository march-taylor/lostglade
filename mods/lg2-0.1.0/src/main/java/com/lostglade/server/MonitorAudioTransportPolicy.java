package com.lostglade.server;

final class MonitorAudioTransportPolicy {
	static final long COMMAND_OVERRIDE_TIMEOUT_MS = 3500L;
	static final long POSITION_SETTLE_TOLERANCE_MS = 900L;

	private MonitorAudioTransportPolicy() {
	}

	static Resolution reconcile(
			boolean snapshotPaused,
			long snapshotPositionMs,
			Boolean pendingPauseState,
			boolean pendingPositionActive,
			long pendingPositionMs,
			long pendingIssuedAtMillis,
			long nowMillis
	) {
		long safeSnapshotPositionMs = Math.max(0L, snapshotPositionMs);
		long safePendingPositionMs = Math.max(0L, pendingPositionMs);
		long safePendingIssuedAtMillis = Math.max(0L, pendingIssuedAtMillis);
		boolean overrideFresh = safePendingIssuedAtMillis > 0L
				&& nowMillis >= safePendingIssuedAtMillis
				&& nowMillis - safePendingIssuedAtMillis <= COMMAND_OVERRIDE_TIMEOUT_MS;

		Boolean nextPendingPauseState = null;
		boolean effectivePaused = snapshotPaused;
		if (pendingPauseState != null && overrideFresh && snapshotPaused != pendingPauseState.booleanValue()) {
			effectivePaused = pendingPauseState.booleanValue();
			nextPendingPauseState = pendingPauseState;
		}

		boolean nextPendingPositionActive = false;
		long effectivePositionMs = safeSnapshotPositionMs;
		if (pendingPositionActive) {
			boolean settled = positionSettled(safeSnapshotPositionMs, safePendingPositionMs);
			if (overrideFresh && !settled) {
				effectivePositionMs = safePendingPositionMs;
				nextPendingPositionActive = true;
			}
		}

		long nextPendingIssuedAtMillis = nextPendingPauseState != null || nextPendingPositionActive
				? safePendingIssuedAtMillis
				: 0L;
		return new Resolution(
				effectivePaused,
				effectivePositionMs,
				nextPendingPauseState,
				nextPendingPositionActive,
				safePendingPositionMs,
				nextPendingIssuedAtMillis
		);
	}

	static boolean positionSettled(long observedPositionMs, long targetPositionMs) {
		return Math.abs(Math.max(0L, observedPositionMs) - Math.max(0L, targetPositionMs)) <= POSITION_SETTLE_TOLERANCE_MS;
	}

	record Resolution(
			boolean paused,
			long positionMs,
			Boolean pendingPauseState,
			boolean pendingPositionActive,
			long pendingPositionMs,
			long pendingIssuedAtMillis
	) {
	}
}
