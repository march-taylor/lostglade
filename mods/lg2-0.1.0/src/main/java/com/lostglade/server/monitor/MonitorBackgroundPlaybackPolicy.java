package com.lostglade.server.monitor;

public final class MonitorBackgroundPlaybackPolicy {
	private MonitorBackgroundPlaybackPolicy() {
	}

	public static boolean animatedMediaActive(boolean mediaPresent, boolean animated, int frameCount) {
		return mediaPresent && animated && frameCount > 1;
	}

	public static long sanitizedDelayMillis(int delayMillis) {
		return Math.max(1L, delayMillis);
	}

	public static long nextFrameDeadlineMillis(long currentDeadlineMillis, long nowMillis, int delayMillis) {
		if (currentDeadlineMillis > nowMillis) {
			return currentDeadlineMillis;
		}
		return nowMillis + sanitizedDelayMillis(delayMillis);
	}

	public static long earliestPositiveDeadlineMillis(long... deadlines) {
		long best = 0L;
		if (deadlines == null) {
			return 0L;
		}
		for (long deadline : deadlines) {
			if (deadline <= 0L) {
				continue;
			}
			best = best <= 0L ? deadline : Math.min(best, deadline);
		}
		return best;
	}
}
